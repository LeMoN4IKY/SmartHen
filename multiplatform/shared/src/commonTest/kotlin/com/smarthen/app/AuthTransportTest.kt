package com.smarthen.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.test.*
import kotlin.test.*

class AuthTransportTest {
    @Test fun cleartextRequiresExplicitPrivateDevelopmentEndpoint() {
        assertFailsWith<IllegalArgumentException> { authServer("http://example.com", true) }
        assertFailsWith<IllegalArgumentException> { authServer("http://192.168.1.5", false) }
        assertFailsWith<IllegalArgumentException> { authServer("http://172.32.0.1", true) }
        assertFailsWith<IllegalArgumentException> { authServer("http://127.bad.0.0.1", true) }
        assertEquals("192.168.1.5", authServer("http://192.168.1.5", true).host)
        assertEquals("example.com", authServer("https://example.com", false).host)
    }
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun credentialPostNeverFollowsRedirect() = runTest {
        var calls = 0
        val engine = MockEngine(MockEngineConfig().apply {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler {
                calls++
                respond("", HttpStatusCode.TemporaryRedirect, headersOf(HttpHeaders.Location, "https://different.example/auth"))
            }
        })
        val client = HttpClient(engine)
        val repository = ApiAuthRepository(client)
        try {
            val error = assertFailsWith<AccountHttpException> {
                repository.authenticate("https://example.com", "a@example.com", "password-long", null)
            }
            assertEquals(307, error.status)
            assertEquals(1, calls)
        } finally { repository.close(); client.close() }
    }
}
