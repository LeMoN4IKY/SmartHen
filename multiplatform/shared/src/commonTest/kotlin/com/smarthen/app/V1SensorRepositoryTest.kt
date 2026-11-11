package com.smarthen.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class V1SensorRepositoryTest {
    private class MemoryStore : LocalStore {
        val values = mutableMapOf<String, String>()
        override fun get(key: String) = values[key]
        override fun put(key: String, value: String) { values[key] = value }
    }
    private val body = """[
        {"device_id":"a","sensor_id":"temp","type":"TEMPERATURE","unit":"degC","value":22,"observed_at":1000,"received_at":2000},
        {"device_id":"b","sensor_id":"temp","type":"TEMPERATURE","unit":"degC","value":23,"observed_at":1001,"received_at":2000}
    ]"""
    private fun TestScope.client(handler: MockRequestHandler) = HttpClient(MockEngine(MockEngineConfig().apply {
        dispatcher = StandardTestDispatcher(testScheduler)
        addHandler(handler)
    }))

    @Test fun usesBearerAndDeviceScopedIdsAndObservationTime() = runTest {
        val store = MemoryStore()
        val client = client { request ->
            assertEquals("/api/v1/houses/house-1/readings", request.url.encodedPath)
            assertEquals("Bearer secret", request.headers[HttpHeaders.Authorization])
            respond(body)
        }
        try {
            val repository = V1SensorRepository(store, client, "account-a") { "secret" }
            val rows = repository.fetch("https://example.com", "house-1")
            assertNotEquals(rows[0].id, rows[1].id)
            assertEquals(1000L, rows[0].last_seen)
            assertEquals(rows, repository.cached("https://example.com", "house-1"))
            assertNull(repository.cached("https://other.example", "house-1"))
            assertNull(repository.cached("https://example.com", "house-2"))
            assertNull(V1SensorRepository(store, client, "account-b") { "secret" }.cached("https://example.com", "house-1"))
        } finally { client.close() }
    }

    @Test fun absentTokenNeverSendsRequest() = runTest {
        val client = client { error("Must not send unauthenticated request") }
        try {
            val error = assertFailsWith<SensorHttpException> {
                V1SensorRepository(MemoryStore(), client, "a") { null }.fetch("https://example.com", "1")
            }
            assertEquals(401, error.statusCode)
        } finally { client.close() }
    }

    @Test fun changedSessionCannotWriteCache() = runTest {
        val store = MemoryStore()
        var token: String? = "first"
        val client = client { token = "replacement"; respond(body) }
        try {
            assertFailsWith<CancellationException> {
                V1SensorRepository(store, client, "a") { token }.fetch("https://example.com", "1")
            }
            assertEquals(emptyMap(), store.values)
        } finally { client.close() }
    }

    @Test fun serverErrorCannotReplaceExistingScopedCache() = runTest {
        val store = MemoryStore()
        var fail = false
        val client = client { if (fail) respond("Unavailable", HttpStatusCode.ServiceUnavailable) else respond(body) }
        try {
            val repository = V1SensorRepository(store, client, "a") { "secret" }
            val initial = repository.fetch("https://example.com", "1")
            fail = true
            assertFailsWith<SensorHttpException> { repository.fetch("https://example.com", "1") }
            assertEquals(initial, repository.cached("https://example.com", "1"))
        } finally { client.close() }
    }

    @Test fun ownTimeoutRemainsRecoverable() = runTest {
        val client = client { awaitCancellation() }
        try {
            assertFailsWith<SensorTimeoutException> {
                V1SensorRepository(MemoryStore(), client, "a") { "secret" }.fetch("https://example.com", "1")
            }
        } finally { client.close() }
    }
    @Test fun redirectedResponseIsNotFollowedWithBearer() = runTest {
        var requests = 0
        val client = client {
            requests++
            respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "https://other.example/readings"))
        }
        val repository = V1SensorRepository(MemoryStore(), client, "a") { "secret" }
        try {
            assertEquals(302, assertFailsWith<SensorHttpException> {
                repository.fetch("https://example.com", "1")
            }.statusCode)
            assertEquals(1, requests)
        } finally { repository.close(); client.close() }
    }

    @Test fun plaintextBearerTransportRequiresExplicitLocalOptIn() = runTest {
        val client = client { error("HTTP must be rejected before any request") }
        val repository = V1SensorRepository(MemoryStore(), client, "a") { "secret" }
        try {
            assertFailsWith<IllegalArgumentException> { repository.fetch("http://example.com", "1") }
            assertFailsWith<IllegalArgumentException> { repository.fetch("http://127.0.0.1", "1") }
        } finally { repository.close(); client.close() }
    }

}
