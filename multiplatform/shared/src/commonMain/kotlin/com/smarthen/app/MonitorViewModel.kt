package com.smarthen.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Values only: Compose observes this state without owning requests, clocks or cache access. */
data class MonitorUiState(
    val server: String = "",
    val house: String = "",
    val readings: List<SensorDto> = emptyList(),
    val cached: Boolean = true,
    val loading: Boolean = false,
    val failed: Boolean = false,
    val sessionExpired: Boolean = false,
    val nowSeconds: Long = 0,
)

class MonitorViewModel(
    store: LocalStore,
    private val client: HttpClient = platformClient(),
    private val clock: () -> Long = ::nowSeconds,
    accountId: String? = null,
    allowLocalHttp: Boolean = false,
    tokenProvider: () -> String? = { null },
) : ViewModel() {
    private val sessionToken = tokenProvider()
    private val repository = SensorRepository(store, client)
    private val authenticated = accountId?.let { V1SensorRepository(store, client, it, allowLocalHttp = allowLocalHttp, tokenProvider = { sessionToken }) }
    private val mutableState = MutableStateFlow(MonitorUiState(nowSeconds = clock()))
    val state: StateFlow<MonitorUiState> = mutableState.asStateFlow()
    private var polling: Job? = null
    private var ticking: Job? = null
    private var active = false
    private var generation = 0L

    /** Call from the UI thread. Cache entries are scoped by server and house in the repository. */
    fun configure(server: String, house: String, active: Boolean) {
        val changed = server != state.value.server || house != state.value.house
        if (!changed && this.active == active) return
        pause()
        if (changed) {
            mutableState.value = MonitorUiState(
                server = server,
                house = house,
                readings = (if (authenticated != null) authenticated.cached(server, house) else repository.cached(server, house)).orEmpty(),
                nowSeconds = clock(),
            )
        }
        this.active = active
        if (!active) return
        ticking = viewModelScope.launch {
            while (isActive) {
                mutableState.value = state.value.copy(nowSeconds = clock())
                delay(1_000)
            }
        }
        startPolling()
    }

    fun expiredSessionToken(): String? = sessionToken.takeIf { state.value.sessionExpired }

    fun refresh() {
        if (active && !state.value.loading) startPolling()
    }

    fun pause() {
        active = false
        generation++
        polling?.cancel()
        ticking?.cancel()
        polling = null
        ticking = null
        mutableState.value = state.value.copy(loading = false)
    }

    private fun startPolling() {
        polling?.cancel()
        val requestGeneration = ++generation
        val server = state.value.server
        val house = state.value.house
        if (server.isBlank() || house.isBlank() || state.value.sessionExpired) return
        polling = viewModelScope.launch {
            while (isActive) {
                mutableState.value = state.value.copy(loading = true)
                try {
                    val readings = authenticated?.fetch(server, house) ?: repository.fetch(server, house)
                    // Cancellation is cooperative. Guard completion as well so a late old request
                    // cannot publish readings or clear the spinner of a newly selected house.
                    if (requestGeneration != generation) return@launch
                    mutableState.value = state.value.copy(readings = readings, cached = false, failed = false)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    if (requestGeneration != generation) return@launch
                    if (authenticated != null && ((error as? AccountHttpException)?.status == 401 || (error as? SensorHttpException)?.statusCode == 401)) {
                        mutableState.value = state.value.copy(sessionExpired = true, readings = emptyList(), failed = true)
                        ticking?.cancel()
                        return@launch // Credentials will not recover through polling; request a new login.
                    }
                    // Retain only this configuration's last good readings. A failed response is
                    // never substituted with another house's cache or interpreted as an empty list.
                    mutableState.value = state.value.copy(failed = true, cached = true)
                } finally {
                    if (requestGeneration == generation) {
                        mutableState.value = state.value.copy(loading = false, nowSeconds = clock())
                    }
                }
                delay(if (state.value.failed) 15_000 else 5_000)
            }
        }
    }

    override fun onCleared() {
        pause()
        authenticated?.close()
        client.close()
    }
}
