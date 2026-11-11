package com.smarthen.app

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.appendPathSegments
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

interface LocalStore {
    fun get(key: String): String?
    fun put(key: String, value: String)
}

expect fun platformClient(): HttpClient
expect fun nowSeconds(): Long

/** Transport DTO: retain unknown types for forward compatibility, and preserve server timestamps. */
@Serializable
data class SensorDto(
    val id: String,
    val type: String,
    val name: String,
    val unit: String,
    val current_value: Double,
    val min_value: Double = 0.0,
    val max_value: Double = 100.0,
    val warning_threshold: Double = 20.0,
    val is_online: Boolean = false,
    val last_seen: Long = 0,
)

internal val sensorJson = Json { ignoreUnknownKeys = true }

/** HTTP is supported only for an explicitly configured development server; production uses HTTPS. */
internal fun validatedServer(server: String): Url {
    val value = server.trim()
    require(value.startsWith("https://") || value.startsWith("http://")) { "Use a complete HTTP or HTTPS URL" }
    require('?' !in value && '#' !in value) { "Server URL cannot contain a query or fragment" }
    val url = Url(value)
    require(url.host.isNotBlank()) { "Server host is required" }
    require(url.user == null && url.password == null) { "Credentials cannot be embedded in the server URL" }
    return url
}

private fun validatedHouse(houseId: String): String {
    require(houseId.isNotBlank() && houseId == houseId.trim()) { "House ID is required and cannot have surrounding whitespace" }
    require(houseId != "." && houseId != "..") { "Invalid house ID" }
    require(houseId.none { it.code < 32 || it.code == 127 }) { "Invalid house ID" }
    return houseId
}

internal fun sensorUrl(server: String, houseId: String): String =
    URLBuilder(validatedServer(server)).apply {
        appendPathSegments("sensors", validatedHouse(houseId), encodeSlash = true)
    }.buildString()

/** Length prefixes avoid delimiter collisions, and the version allows future cache schema changes. */
internal fun sensorCacheKey(server: String, houseId: String): String {
    val base = validatedServer(server).toString().trimEnd('/')
    val house = validatedHouse(houseId)
    return "sensor-cache-v1:${base.length}:$base:${house.length}:$house"
}

internal fun decodeSensors(text: String): List<SensorDto> =
    sensorJson.decodeFromString<List<SensorDto>>(text).also { sensors ->
        require(sensors.all { it.id.isNotBlank() && it.current_value.isFinite() }) { "Invalid sensor data" }
        require(sensors.map { it.id }.toSet().size == sensors.size) { "Duplicate sensor IDs" }
    }

class SensorHttpException(val statusCode: Int) : Exception("Sensor request failed (HTTP $statusCode)")
class SensorTimeoutException : Exception("Sensor request timed out")

/** The caller owns [client]. Cache failures must not turn a successful refresh into a network error. */
class SensorRepository(private val store: LocalStore, private val client: HttpClient) {
    suspend fun fetch(server: String, houseId: String): List<SensorDto> {
        val endpoint = sensorUrl(server, houseId)
        val key = sensorCacheKey(server, houseId)
        val sensors = withTimeoutOrNull(15_000L) {
            val response = client.get(endpoint)
            if (response.status.value !in 200..299) throw SensorHttpException(response.status.value)
            decodeSensors(response.bodyAsText())
        } ?: throw SensorTimeoutException()
        try {
            store.put(key, sensorJson.encodeToString(sensors))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // The UI can still show the valid response. No stale cache replaces fresh readings.
        }
        return sensors
    }

    fun cached(server: String, houseId: String): List<SensorDto>? {
        return try {
            // Empty configuration is normal before the first server has been configured.
            val key = sensorCacheKey(server, houseId)
            store.get(key)?.let(::decodeSensors)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }
}
