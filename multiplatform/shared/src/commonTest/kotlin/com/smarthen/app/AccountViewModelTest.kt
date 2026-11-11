package com.smarthen.app

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class AccountViewModelTest {
    private class MemorySessions : SessionStore {
        val tokens = mutableMapOf<String, String>()
        override fun read(server: String) = tokens[server]
        override fun write(server: String, token: String) { tokens[server] = token }
        override fun clear(server: String) { tokens.remove(server) }
    }
    private open class FakeAuth : AuthRepository {
        override suspend fun authenticate(server: String, email: String, password: String, name: String?) = AccountSession("token", "bearer", 1000, AccountUser("1", email, name ?: "Name"))
        override suspend fun me(server: String, token: String) = AccountUser("1", "a@example.com", "Name")
        override suspend fun subscription(server: String, token: String) = AccountSubscription("inactive")
        override suspend fun logout(server: String, token: String) {}
    }
    @Test fun invalidSavedTokenIsRemovedAndNeverPublished() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val store = MemorySessions().apply { write("https://a.example", "expired") }
            val vm = AccountViewModel(object : FakeAuth() {
                override suspend fun me(server: String, token: String): AccountUser = throw AccountHttpException(401)
            }, store)
            vm.configure("https://a.example")
            runCurrent()
            assertNull(vm.bearerToken()); assertNull(vm.state.value.user)
            assertNull(store.read("https://a.example"))
        } finally { Dispatchers.resetMain() }
    }
    @Test fun lateUncooperativeAuthenticationCannotLeakIntoAnotherServer() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val response = CompletableDeferred<Unit>()
            val store = MemorySessions()
            val vm = AccountViewModel(object : FakeAuth() {
                override suspend fun authenticate(server: String, email: String, password: String, name: String?): AccountSession {
                    withContext(NonCancellable) { response.await() }
                    return super.authenticate(server, email, password, name)
                }
            }, store)
            vm.configure("https://a.example"); runCurrent()
            vm.submit("Name", "a@example.com", "long-password", false); runCurrent()
            vm.configure("https://b.example"); runCurrent()
            response.complete(Unit); runCurrent()
            assertEquals("https://b.example", vm.state.value.server)
            assertNull(vm.bearerToken()); assertNull(vm.state.value.user)
            assertTrue(store.tokens.isEmpty())
        } finally { Dispatchers.resetMain() }
    }
    @Test fun logoutRevokesBeforeClearingLocalSession() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            var revoked = false
            val store = MemorySessions().apply { write("https://a.example", "valid") }
            val vm = AccountViewModel(object : FakeAuth() {
                override suspend fun logout(server: String, token: String) { revoked = true }
            }, store)
            vm.configure("https://a.example"); runCurrent()
            assertNotNull(vm.state.value.user)
            vm.logout(); runCurrent()
            assertTrue(revoked); assertNull(vm.bearerToken()); assertNull(store.read("https://a.example"))
        } finally { Dispatchers.resetMain() }
    }
}
