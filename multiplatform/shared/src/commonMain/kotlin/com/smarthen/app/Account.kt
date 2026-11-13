package com.smarthen.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

/** Implement with Keychain/Keystore-backed storage; never use ordinary preferences for tokens. */
interface SessionStore {
    fun read(server: String): String?
    fun write(server: String, token: String)
    fun clear(server: String)
}
@Serializable data class AccountUser(val id: String, val email: String, val display_name: String)
@Serializable data class AccountSession(val access_token: String, val token_type: String, val expires_at: Long, val user: AccountUser)
@Serializable data class AccountSubscription(val status: String, val tariff: String? = null, val expires_at: Long? = null)
@Serializable private data class SignupBody(val email: String, val password: String, val display_name: String)
@Serializable private data class LoginBody(val email: String, val password: String)
class AccountHttpException(val status: Int) : Exception("Account request failed ($status)")
interface AuthRepository {
    suspend fun authenticate(server: String, email: String, password: String, name: String?): AccountSession
    suspend fun me(server: String, token: String): AccountUser
    suspend fun subscription(server: String, token: String): AccountSubscription
    suspend fun logout(server: String, token: String)
    fun close() {}
}
/** Public servers require TLS. Private HTTP must be an explicit development opt-in. */
internal fun authServer(server: String, allowLocalHttp: Boolean): Url {
    val url = validatedServer(server)
    if (url.protocol == URLProtocol.HTTPS) return url
    val host = url.host.removePrefix("[").removeSuffix("]").lowercase()
    val parts = host.split('.').mapNotNull { it.toIntOrNull() }
    val privateIpv4 = host.split('.').size == 4 && parts.size == 4 && parts.all { it in 0..255 } &&
        (parts[0] == 127 || parts[0] == 10 || (parts[0] == 192 && parts[1] == 168) ||
            (parts[0] == 172 && parts[1] in 16..31))
    require(allowLocalHttp && (privateIpv4 || host == "localhost" || host == "::1")) { "Authentication requires HTTPS" }
    return url
}
internal class V1ApiClient(client: HttpClient, private val allowLocalHttp: Boolean = false) {
    // Do not forward a password or bearer token to a redirected origin, even over HTTPS.
    private val authClient = client.config { followRedirects = false }
    fun close() { authClient.close() }
    private fun endpoint(server: String, path: String) = URLBuilder(authServer(server, allowLocalHttp)).apply {
        appendPathSegments("api", "v1")
        appendPathSegments(path.split('/'))
    }.buildString()
    suspend fun request(server: String, path: String, token: String? = null, body: String? = null, post: Boolean = false, query: Map<String, String> = emptyMap()): String =
        withTimeoutOrNull(15_000) {
            val response = authClient.request(endpoint(server, path)) {
                url { query.forEach { (key, value) -> parameters.append(key, value) } }
                method = if (post) HttpMethod.Post else HttpMethod.Get
                if (token != null) bearerAuth(token)
                if (body != null) { contentType(ContentType.Application.Json); setBody(body) }
            }
            if (response.status.value !in 200..299) throw AccountHttpException(response.status.value)
            response.bodyAsText()
        } ?: throw SensorTimeoutException()
}
class ApiAuthRepository(client: HttpClient, allowLocalHttp: Boolean = false) : AuthRepository {
    private val transport = V1ApiClient(client, allowLocalHttp)
    override fun close() = transport.close()
    override suspend fun authenticate(server: String, email: String, password: String, name: String?): AccountSession {
        val body = if (name == null) sensorJson.encodeToString(LoginBody(email, password)) else sensorJson.encodeToString(SignupBody(email, password, name))
        return sensorJson.decodeFromString(transport.request(server, if (name == null) "auth/login" else "auth/signup", body = body, post = true))
    }
    override suspend fun me(server: String, token: String): AccountUser = sensorJson.decodeFromString(transport.request(server, "me", token))
    override suspend fun subscription(server: String, token: String): AccountSubscription = sensorJson.decodeFromString(transport.request(server, "subscription", token))
    override suspend fun logout(server: String, token: String) { transport.request(server, "auth/logout", token, post = true) }
}
enum class AccountError { INVALID_INPUT, INVALID_CREDENTIALS, EMAIL_USED, UNAVAILABLE, SESSION_EXPIRED, STORAGE, LOGOUT_PENDING }
data class AccountUiState(
    val server: String = "", val user: AccountUser? = null,
    val subscription: AccountSubscription? = null, val busy: Boolean = false,
    val error: AccountError? = null, val sessionRevision: Int = 0,
)
class AccountViewModel(private val repository: AuthRepository, private val sessions: SessionStore) : ViewModel() {
    private val mutableState = MutableStateFlow(AccountUiState())
    val state = mutableState.asStateFlow()
    private var job: Job? = null
    private var token: String? = null
    private var generation = 0
    fun bearerToken(): String? = token

    /** A late 401 from another server or an older login must never invalidate a newer session. */
    fun invalidateSession(expectedServer: String, expectedToken: String): Boolean {
        if (state.value.server != expectedServer || token != expectedToken) return false
        generation++
        job?.cancel()
        token = null
        mutableState.value = AccountUiState(
            server = expectedServer,
            error = AccountError.SESSION_EXPIRED,
            sessionRevision = state.value.sessionRevision + 1,
        )
        // Memory state is invalidated first: Keychain/Keystore failure must not preserve access.
        try { sessions.clear(expectedServer) } catch (_: Exception) { /* A future restore revalidates with the server. */ }
        return true
    }

    fun configure(server: String) {
        if (server == state.value.server) return
        job?.cancel(); generation++; token = null
        mutableState.value = AccountUiState(server = server, sessionRevision = state.value.sessionRevision + 1)
        if (server.isBlank()) return
        operation {
            val saved = sessions.read(server) ?: return@operation
            val user = repository.me(server, saved)
            currentCoroutineContext().ensureActive()
            token = saved
            mutableState.value = state.value.copy(user = user, sessionRevision = state.value.sessionRevision + 1)
            loadSubscription(server, saved)
        }
    }
    fun submit(name: String, email: String, password: String, register: Boolean) {
        if (state.value.busy) return
        if (state.value.server.isBlank() || !email.contains('@') || password.length < 12 || (register && name.isBlank())) {
            mutableState.value = state.value.copy(error = AccountError.INVALID_INPUT); return
        }
        operation {
            val server = state.value.server
            val session = repository.authenticate(server, email.trim(), password, name.trim().takeIf { register })
            currentCoroutineContext().ensureActive()
            // Persist before publishing authenticated state; a failed secure write must be visible.
            try { sessions.write(server, session.access_token) }
            catch (_: Exception) { mutableState.value = state.value.copy(error = AccountError.STORAGE); return@operation }
            token = session.access_token
            mutableState.value = state.value.copy(user = session.user, sessionRevision = state.value.sessionRevision + 1)
            loadSubscription(server, session.access_token)
        }
    }
    fun refresh() {
        val saved = token ?: return
        operation { loadSubscription(state.value.server, saved) }
    }
    fun logout() {
        if (state.value.busy) return
        val saved = token ?: return
        operation {
            val server = state.value.server
            // A failed revocation is not called a successful logout: users can retry explicitly.
            repository.logout(server, saved)
            currentCoroutineContext().ensureActive()
            sessions.clear(server); token = null
            mutableState.value = AccountUiState(server = server, sessionRevision = state.value.sessionRevision + 1)
        }
    }
    private suspend fun loadSubscription(server: String, saved: String) {
        val subscription = repository.subscription(server, saved)
        currentCoroutineContext().ensureActive()
        mutableState.value = state.value.copy(subscription = subscription)
    }
    private fun operation(block: suspend () -> Unit) {
        job?.cancel()
        val current = ++generation
        mutableState.value = state.value.copy(busy = true, error = null)
        job = viewModelScope.launch {
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (current != generation) return@launch
                val reason = when ((error as? AccountHttpException)?.status) {
                    401 -> if (state.value.user == null) AccountError.INVALID_CREDENTIALS else AccountError.SESSION_EXPIRED
                    409 -> AccountError.EMAIL_USED
                    422 -> AccountError.INVALID_INPUT
                    else -> AccountError.UNAVAILABLE
                }
                if ((error as? AccountHttpException)?.status == 401) {
                    // A rejected token must not remain available to other feature repositories.
                    token = null
                    try { sessions.clear(state.value.server) } catch (_: Exception) { /* Retry on next login. */ }
                    mutableState.value = state.value.copy(user = null, subscription = null, sessionRevision = state.value.sessionRevision + 1)
                }
                mutableState.value = state.value.copy(error = reason)
            } finally { if (current == generation) mutableState.value = state.value.copy(busy = false) }
        }
    }
    override fun onCleared() {
        generation++
        job?.cancel()
        token = null
        repository.close()
    }
}
