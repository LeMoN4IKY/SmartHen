package com.smarthen.app

import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

@Serializable
private data class V1Reading(
    val device_id: String,
    val sensor_id: String,
    val type: String,
    val unit: String,
    val value: Double,
    val observed_at: Long,
    val received_at: Long,
)

/**
 * Authenticated telemetry adapter. Each instance belongs to exactly one authenticated account.
 * Cache identities include server/account/house; bearer secrets are never part of a cache key.
 * The UI uses observed_at for freshness, not received_at (historical backfill is not live data).
 */
class V1SensorRepository(
    private val store: LocalStore,
    client: HttpClient,
    private val accountId: String,
    private val allowLocalHttp: Boolean = false,
    private val tokenProvider: () -> String?,
) {
    init { require(accountId.isNotBlank()) }
    private val authenticatedClient = client.config { followRedirects = false }
    fun close() { authenticatedClient.close() }

    private fun key(server: String, house: String): String =
        "v1-account:${accountId.length}:$accountId:${sensorCacheKey(server, house)}"

    suspend fun fetch(server: String, house: String): List<SensorDto> {
        // Reuse the legacy helper's strict house validation, but never its public endpoint.
        val cacheKey = key(server, house)
        val token = tokenProvider()?.takeIf { it.isNotBlank() } ?: throw SensorHttpException(401)
        val endpoint = URLBuilder(authServer(server, allowLocalHttp)).apply {
            appendPathSegments("api", "v1", "houses", house, "readings", encodeSlash = true)
        }.buildString()
        val readings = withTimeoutOrNull(15_000L) {
            val response = authenticatedClient.get(endpoint) { bearerAuth(token) }
            if (response.status.value !in 200..299) throw SensorHttpException(response.status.value)
            val rows = sensorJson.decodeFromString<List<V1Reading>>(response.bodyAsText())
            rows.map { row ->
                require(row.device_id.isNotBlank() && row.sensor_id.isNotBlank()) { "Invalid device or sensor ID" }
                require(row.value.isFinite() && row.observed_at > 0 && row.received_at > 0) { "Invalid telemetry" }
                SensorDto(
                    // Same sensor IDs can exist on several controllers in one house.
                    id = "${row.device_id.length}:${row.device_id}:${row.sensor_id.length}:${row.sensor_id}",
                    type = row.type,
                    name = row.sensor_id,
                    unit = row.unit,
                    current_value = row.value,
                    last_seen = row.observed_at,
                    // V1 has no per-sensor online flag. This permits timestamp-based freshness
                    // evaluation; it is NOT a claim that the controller is currently connected.
                    is_online = true,
                )
            }.also { sensors ->
                require(sensors.map { it.id }.toSet().size == sensors.size) { "Duplicate sensor readings" }
            }
        } ?: throw SensorTimeoutException()
        currentCoroutineContext().ensureActive()
        // Do not cache or publish a request from a session that was replaced while it was running.
        if (tokenProvider() != token) throw CancellationException("Session changed during telemetry request")
        try { store.put(cacheKey, sensorJson.encodeToString(readings)) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { /* Valid network readings remain usable when local persistence fails. */ }
        return readings
    }

    fun cached(server: String, house: String): List<SensorDto>? = try {
        store.get(key(server, house))?.let(::decodeSensors)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }
}
