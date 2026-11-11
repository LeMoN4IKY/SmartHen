package com.smarthen.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.ktor.client.HttpClient
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

@Serializable data class HouseDto(val id: String, val name: String)
@Serializable data class ClaimedDevice(val id: String, val name: String, val capabilities: List<String> = emptyList(), val last_seen: Long? = null)
@Serializable private data class HouseBody(val name: String)
@Serializable private data class ClaimBody(val claim_token: String)
interface HousesRepository {
    suspend fun list(server: String, token: String): List<HouseDto>
    suspend fun create(server: String, token: String, name: String): HouseDto
    suspend fun claim(server: String, token: String, houseId: String, claimToken: String): ClaimedDevice
    fun close() {}
}
class ApiHousesRepository(client: HttpClient, allowLocalHttp: Boolean = false) : HousesRepository {
    private val transport = V1ApiClient(client, allowLocalHttp)
    override fun close() = transport.close()
    override suspend fun list(server: String, token: String): List<HouseDto> = sensorJson.decodeFromString(transport.request(server, "houses", token))
    override suspend fun create(server: String, token: String, name: String): HouseDto = sensorJson.decodeFromString(transport.request(server, "houses", token, sensorJson.encodeToString(HouseBody(name)), true))
    override suspend fun claim(server: String, token: String, houseId: String, claimToken: String): ClaimedDevice {
        // IDs originate on our server, never embed arbitrary QR text in a URL path.
        require(houseId.matches(Regex("[A-Za-z0-9_-]+")))
        return sensorJson.decodeFromString(transport.request(server, "houses/$houseId/devices/claim", token, sensorJson.encodeToString(ClaimBody(claimToken)), true))
    }
}
enum class HousesError { INVALID_INPUT, SESSION_EXPIRED, INVALID_CLAIM, UNAVAILABLE }
data class HousesUiState(val server: String = "", val houses: List<HouseDto> = emptyList(), val busy: Boolean = false, val error: HousesError? = null, val claimedDevice: ClaimedDevice? = null)
class HousesViewModel(private val repository: HousesRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(HousesUiState())
    val state = mutableState.asStateFlow()
    private var token: String? = null
    private var rejectedToken: String? = null
    fun expiredSessionToken(): String? = rejectedToken.takeIf { state.value.error == HousesError.SESSION_EXPIRED }
    private var generation = 0
    private var job: Job? = null
    fun configure(server: String, bearer: String?) {
        if (state.value.server == server && token == bearer) return
        job?.cancel(); generation++; token = bearer; rejectedToken = null
        mutableState.value = HousesUiState(server = server)
        if (bearer != null) refresh()
    }
    fun refresh() = operation { server, saved ->
        val houses = repository.list(server, saved)
        currentCoroutineContext().ensureActive()
        mutableState.value = state.value.copy(houses = houses)
    }
    fun create(name: String) {
        if (name.trim().isEmpty() || name.trim().length > 100) { mutableState.value = state.value.copy(error = HousesError.INVALID_INPUT); return }
        operation { server, saved ->
            val added = repository.create(server, saved, name.trim())
            currentCoroutineContext().ensureActive()
            mutableState.value = state.value.copy(houses = (state.value.houses + added).distinctBy { it.id }.sortedBy { it.name })
        }
    }
    fun claim(houseId: String, claimToken: String) {
        if (claimToken.trim().length !in 20..200 || state.value.houses.none { it.id == houseId }) { mutableState.value = state.value.copy(error = HousesError.INVALID_INPUT); return }
        operation { server, saved ->
            val device = repository.claim(server, saved, houseId, claimToken.trim())
            currentCoroutineContext().ensureActive()
            mutableState.value = state.value.copy(claimedDevice = device)
        }
    }
    private fun operation(block: suspend (String, String) -> Unit) {
        if (state.value.busy || rejectedToken != null) return
        val saved = token ?: return
        val server = state.value.server
        val version = ++generation
        mutableState.value = state.value.copy(busy = true, error = null, claimedDevice = null)
        job = viewModelScope.launch {
            try { block(server, saved) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (version != generation) return@launch
                val reason = when ((error as? AccountHttpException)?.status) {
                    401 -> HousesError.SESSION_EXPIRED
                    400 -> HousesError.INVALID_CLAIM
                    422 -> HousesError.INVALID_INPUT
                    else -> HousesError.UNAVAILABLE
                }
                if (reason == HousesError.SESSION_EXPIRED) {
                    rejectedToken = saved
                    mutableState.value = state.value.copy(houses = emptyList(), claimedDevice = null)
                }
                mutableState.value = state.value.copy(error = reason)
            } finally { if (version == generation) mutableState.value = state.value.copy(busy = false) }
        }
    }
    override fun onCleared() { generation++; job?.cancel(); token = null; repository.close() }
}
