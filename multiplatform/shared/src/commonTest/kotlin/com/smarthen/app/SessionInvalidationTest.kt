package com.smarthen.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class SessionInvalidationTest {
    private class Store : SessionStore, LocalStore {
        val values = mutableMapOf<String, String>()
        var failClear = false
        override fun read(server: String) = values[server]
        override fun write(server: String, token: String) { values[server] = token }
        override fun clear(server: String) { if (failClear) error("Keychain unavailable"); values.remove(server) }
        override fun get(key: String) = values[key]
        override fun put(key: String, value: String) { values[key] = value }
    }
    private class Auth : AuthRepository {
        override suspend fun me(server: String, token: String) = AccountUser("user", "user@example.com", "Name")
        override suspend fun subscription(server: String, token: String) = AccountSubscription("inactive")
        override suspend fun authenticate(server: String, email: String, password: String, name: String?) = AccountSession("new-token", "bearer", 100, me(server, ""))
        override suspend fun logout(server: String, token: String) {}
    }
    @Test fun staleCallbacksCannotRevokeNewSessionAndStorageFailureCannotKeepAccess() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val store = Store().apply { write("https://server.example", "old-token") }
            val vm = AccountViewModel(Auth(), store)
            vm.configure("https://server.example"); runCurrent()
            vm.submit("Name", "user@example.com", "long-password", false); runCurrent()
            assertFalse(vm.invalidateSession("https://other.example", "new-token"))
            assertFalse(vm.invalidateSession("https://server.example", "old-token"))
            assertEquals("new-token", vm.bearerToken())
            store.failClear = true
            val revision = vm.state.value.sessionRevision
            assertTrue(vm.invalidateSession("https://server.example", "new-token"))
            assertNull(vm.bearerToken()); assertNull(vm.state.value.user)
            assertEquals(revision + 1, vm.state.value.sessionRevision)
            assertEquals(AccountError.SESSION_EXPIRED, vm.state.value.error)
        } finally { Dispatchers.resetMain() }
    }
    @Test fun telemetry401StopsRetryingAndReportsTheRejectedSession() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var calls = 0
        val client = HttpClient(MockEngine(MockEngineConfig().apply {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler { calls++; respond("", HttpStatusCode.Unauthorized) }
        }))
        try {
            val vm = MonitorViewModel(Store(), client, { 1000 }, "user", { "rejected" })
            vm.configure("https://server.example", "house", true); runCurrent()
            assertTrue(vm.state.value.sessionExpired)
            assertEquals("rejected", vm.expiredSessionToken())
            advanceTimeBy(60_000); runCurrent(); vm.refresh(); runCurrent()
            assertEquals(1, calls)
            vm.pause()
        } finally { client.close(); Dispatchers.resetMain() }
    }
    @Test fun houses401ClearsVisibleDataAndStopsSubsequentRequests() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            var calls = 0
            val vm = HousesViewModel(object : HousesRepository {
                override suspend fun list(server: String, token: String): List<HouseDto> { calls++; throw AccountHttpException(401) }
                override suspend fun create(server: String, token: String, name: String): HouseDto = error("Unexpected write")
                override suspend fun claim(server: String, token: String, houseId: String, claimToken: String): ClaimedDevice = error("Unexpected write")
            })
            vm.configure("https://server.example", "expired"); runCurrent()
            assertEquals("expired", vm.expiredSessionToken())
            assertTrue(vm.state.value.houses.isEmpty())
            vm.refresh(); runCurrent(); assertEquals(1, calls)
            vm.configure("https://server.example", null)
            assertNull(vm.expiredSessionToken())
        } finally { Dispatchers.resetMain() }
    }
}
