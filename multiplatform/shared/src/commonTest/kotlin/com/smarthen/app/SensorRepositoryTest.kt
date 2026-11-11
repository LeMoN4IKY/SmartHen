package com.smarthen.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SensorRepositoryTest {
    private val body = """[{"id":"s1","type":"TEMPERATURE","name":"Temperature","unit":"C","current_value":22.5,"last_seen":1000,"is_online":true}]"""
    private class MemoryStore(val failWrite: Boolean = false) : LocalStore {
        val values = mutableMapOf<String, String>()
        override fun get(key: String) = values[key]
        override fun put(key: String, value: String) {
            if (failWrite) error("Disk unavailable")
            values[key] = value
        }
    }

    // Engine and caller must share virtual time; a real IO dispatcher can lose the race
    // against runTest's auto-advanced request deadline even for an immediate mock response.
    private fun TestScope.client(handler: MockRequestHandler) = HttpClient(MockEngine(MockEngineConfig().apply {
        dispatcher = StandardTestDispatcher(testScheduler)
        addHandler(handler)
    }))

    @Test fun successfulGetCachesValidatedDataInTheCorrectScope() = runTest {
        val store = MemoryStore()
        val client = client { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/api/sensors/house-1", request.url.encodedPath)
            respond(body)
        }
        try {
            val repository = SensorRepository(store, client)
            val fresh = repository.fetch("https://example.com/api", "house-1")
            assertEquals(22.5, fresh.single().current_value)
            assertEquals(fresh, repository.cached("https://example.com/api", "house-1"))
            assertNull(repository.cached("https://example.com/api", "house-2"))
            assertNull(repository.cached("https://other.example/api", "house-1"))
        } finally { client.close() }
    }

    @Test fun cacheWriteFailureDoesNotDiscardSuccessfulReading() = runTest {
        val client = client { respond(body) }
        try {
            assertEquals(22.5, SensorRepository(MemoryStore(failWrite = true), client)
                .fetch("https://example.com", "1").single().current_value)
        } finally { client.close() }
    }

    @Test fun missingConfigurationAndCorruptCacheAreSafe() {
        val store = MemoryStore()
        val client = HttpClient(MockEngine { error("Cache reads must not make network requests") })
        try {
            val repository = SensorRepository(store, client)
            assertNull(repository.cached("", ""))
            store.values[sensorCacheKey("https://example.com", "1")] = "corrupt"
            assertNull(repository.cached("https://example.com", "1"))
        } finally { client.close() }
    }

    @Test fun errorStatusDoesNotReplaceCache() = runTest {
        val store = MemoryStore()
        val key = sensorCacheKey("https://example.com", "1")
        store.values[key] = body
        val client = client { respond("Unavailable", HttpStatusCode.ServiceUnavailable) }
        try {
            val failure = assertFailsWith<SensorHttpException> {
                SensorRepository(store, client).fetch("https://example.com", "1")
            }
            assertEquals(503, failure.statusCode)
            assertEquals(body, store.values[key])
        } finally { client.close() }
    }

    @Test fun malformedSuccessBodyDoesNotPoisonCache() = runTest {
        val store = MemoryStore()
        val client = client { respond("not JSON") }
        try {
            assertFailsWith<Exception> { SensorRepository(store, client).fetch("https://example.com", "1") }
            assertEquals(emptyMap(), store.values)
        } finally { client.close() }
    }

    @Test fun ownDeadlineProducesRecoverableTimeout() = runTest {
        val client = client { delay(60_000); respond(body) }
        try {
            assertFailsWith<SensorTimeoutException> {
                SensorRepository(MemoryStore(), client).fetch("https://example.com", "1")
            }
        } finally { client.close() }
    }

    @Test fun callerCancellationPropagatesAndDoesNotWriteCache() = runTest {
        val store = MemoryStore()
        val client = client { awaitCancellation() }
        try {
            val request = async { SensorRepository(store, client).fetch("https://example.com", "1") }
            runCurrent()
            request.cancel()
            assertFailsWith<CancellationException> { request.await() }
            assertEquals(emptyMap(), store.values)
        } finally { client.close() }
    }
}
