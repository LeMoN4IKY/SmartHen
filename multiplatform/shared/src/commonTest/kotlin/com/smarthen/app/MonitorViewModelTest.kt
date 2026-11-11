package com.smarthen.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MonitorViewModelTest {
    private class MemoryStore : LocalStore {
        private val values = mutableMapOf<String, String>()
        override fun get(key: String) = values[key]
        override fun put(key: String, value: String) { values[key] = value }
    }

    private fun body(value: Int) = """[{"id":"s1","type":"TEMPERATURE","name":"Temperature","unit":"C","current_value":$value,"last_seen":1000,"is_online":true}]"""

    private fun TestScope.client(handler: MockRequestHandler) = HttpClient(MockEngine(MockEngineConfig().apply {
        dispatcher = StandardTestDispatcher(testScheduler)
        addHandler(handler)
    }))

    @Test fun lateOldHouseResponseCannotOverwriteNewHouse() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val releaseOld = CompletableDeferred<Unit>()
        val oldStarted = CompletableDeferred<Unit>()
        val client = client { request ->
            if (request.url.encodedPath.endsWith("/old")) {
                oldStarted.complete(Unit)
                // Simulate a transport finishing work after the caller has cancelled it.
                withContext(NonCancellable) { releaseOld.await() }
                respond(body(11))
            } else respond(body(22))
        }
        val model = MonitorViewModel(MemoryStore(), client, clock = { 1000 })
        try {
            model.configure("https://example.com", "old", true)
            runCurrent()
            assertTrue(oldStarted.isCompleted)
            model.configure("https://example.com", "new", true)
            runCurrent()
            assertEquals(22.0, model.state.value.readings.single().current_value)
            releaseOld.complete(Unit)
            runCurrent()
            assertEquals("new", model.state.value.house)
            assertEquals(22.0, model.state.value.readings.single().current_value)
            assertFalse(model.state.value.failed)
        } finally {
            releaseOld.complete(Unit)
            model.pause()
            client.close()
            runCurrent()
            Dispatchers.resetMain()
        }
    }

    @Test fun pauseCancelsInFlightRequestAndStopsClockAndPolling() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var requests = 0
        var cancelled = false
        var now = 1000L
        val client = client {
            requests++
            try { awaitCancellation() } finally { cancelled = true }
        }
        val model = MonitorViewModel(MemoryStore(), client, clock = { now })
        try {
            model.configure("https://example.com", "1", true)
            runCurrent()
            assertTrue(model.state.value.loading)
            model.pause()
            runCurrent()
            assertTrue(cancelled)
            now = 2000
            advanceTimeBy(60_000)
            runCurrent()
            assertEquals(1, requests)
            assertEquals(1000L, model.state.value.nowSeconds)
            assertFalse(model.state.value.loading)
            assertFalse(model.state.value.failed)
        } finally { model.pause(); client.close(); runCurrent(); Dispatchers.resetMain() }
    }

    @Test fun requestTimeoutIsRecoverableAndPollingRetries() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var requests = 0
        val client = client {
            requests++
            if (requests == 1) awaitCancellation()
            respond(body(24))
        }
        val model = MonitorViewModel(MemoryStore(), client, clock = { 1000 })
        try {
            model.configure("https://example.com", "1", true)
            runCurrent()
            advanceTimeBy(15_000)
            runCurrent()
            assertTrue(model.state.value.failed)
            assertFalse(model.state.value.loading)
            advanceTimeBy(15_000)
            runCurrent()
            assertEquals(2, requests)
            assertEquals(24.0, model.state.value.readings.single().current_value)
            assertFalse(model.state.value.failed)
            assertFalse(model.state.value.cached)
        } finally { model.pause(); client.close(); runCurrent(); Dispatchers.resetMain() }
    }
}
