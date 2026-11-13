package com.smarthen.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.ktor.client.HttpClient
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.Serializable

@Serializable data class MetricReading(val device_id: String, val sensor_id: String, val type: String, val unit: String, val value: Double, val observed_at: Long, val received_at: Long)
@Serializable data class HistoryPage(val points: List<MetricReading>, val next_cursor: String? = null)
data class MetricKey(val device: String, val sensor: String)
fun MetricReading.key() = MetricKey(device_id, sensor_id)
enum class HistoryPeriod(val seconds: Long) { DAY(86_400), WEEK(604_800), MONTH(2_592_000) }
data class SampleStatistics(val minimum: Double, val maximum: Double, val sampleMean: Double, val count: Int)
fun sampleStatistics(rows: List<MetricReading>): SampleStatistics? {
    if (rows.isEmpty()) return null
    require(rows.all { it.value.isFinite() })
    // Incremental weighted mean avoids overflow from summing many finite large readings.
    val mean = rows.foldIndexed(0.0) { index, previous, row -> previous * (index.toDouble() / (index + 1)) + row.value / (index + 1) }
    return SampleStatistics(rows.minOf { it.value }, rows.maxOf { it.value }, mean, rows.size)
}
/** A firmware remapping must not merge different quantities into one chart/statistic.
 * Validate before deduplication, so a conflicting repeated timestamp cannot hide a change.
 */
internal fun requireConsistentHistory(rows: List<MetricReading>, expectedType: String? = null, expectedUnit: String? = null) {
    require(rows.map { it.unit }.distinct().size <= 1) { "History contains different units" }
    require(rows.map { it.type }.distinct().size <= 1) { "History contains different sensor types" }
    require(rows.all { it.type.isNotBlank() && (expectedType == null || it.type == expectedType) && (expectedUnit == null || it.unit == expectedUnit) }) {
        "History type or unit differs from the selected sensor"
    }
}
interface AnalyticsRepository {
    suspend fun sensors(server: String, token: String, house: String): List<MetricReading>
    suspend fun history(server: String, token: String, house: String, metric: MetricKey, since: Long, until: Long, cursor: String? = null): HistoryPage
    fun close() {}
}
class ApiAnalyticsRepository(client: HttpClient, allowLocalHttp: Boolean = false) : AnalyticsRepository {
    private val transport = V1ApiClient(client, allowLocalHttp)
    override fun close() = transport.close()
    private fun path(house: String, route: String): String {
        require(house.matches(Regex("[A-Za-z0-9_-]+")))
        return "houses/$house/$route"
    }
    private fun decode(body: String): List<MetricReading> = sensorJson.decodeFromString<List<MetricReading>>(body).also {
        require(it.all { row -> row.value.isFinite() && row.observed_at > 0 && row.device_id.isNotBlank() && row.sensor_id.isNotBlank() })
    }
    override suspend fun sensors(server: String, token: String, house: String) = decode(transport.request(server, path(house, "readings"), token)).distinctBy { it.key() }
    override suspend fun history(server: String, token: String, house: String, metric: MetricKey, since: Long, until: Long, cursor: String?): HistoryPage {
        val params = mutableMapOf("device_id" to metric.device, "sensor_id" to metric.sensor, "since" to since.toString(), "until" to until.toString(), "limit" to "200")
        if (cursor != null) params["cursor"] = cursor
        val page = sensorJson.decodeFromString<HistoryPage>(transport.request(server, path(house, "history-page"), token, query = params))
        require(page.points.all { it.key() == metric && it.observed_at in since..until && it.value.isFinite() })
        requireConsistentHistory(page.points)
        return page.copy(points = page.points.sortedBy { it.observed_at })
    }
}
enum class AnalyticsError { UNAVAILABLE, SESSION_EXPIRED }
data class AnalyticsUiState(
    val server: String = "", val house: String = "", val sensors: List<MetricReading> = emptyList(),
    val selected: MetricKey? = null, val period: HistoryPeriod = HistoryPeriod.DAY,
    val readings: List<MetricReading> = emptyList(), val busy: Boolean = false,
    val error: AnalyticsError? = null, val nextCursor: String? = null, val since: Long = 0, val until: Long = 0,
)
class AnalyticsViewModel(private val repository: AnalyticsRepository, private val clock: () -> Long = ::nowSeconds) : ViewModel() {
    private val mutableState = MutableStateFlow(AnalyticsUiState())
    val state = mutableState.asStateFlow()
    private var token: String? = null
    private var rejectedToken: String? = null
    private var job: Job? = null
    private var generation = 0
    fun expiredSessionToken() = rejectedToken.takeIf { state.value.error == AnalyticsError.SESSION_EXPIRED }
    fun configure(server: String, token: String?, house: String) {
        if (state.value.server == server && state.value.house == house && this.token == token) return
        job?.cancel(); generation++; this.token = token; rejectedToken = null
        mutableState.value = AnalyticsUiState(server = server, house = house)
        if (server.isNotBlank() && house.isNotBlank() && token != null) refresh()
    }
    fun select(metric: MetricKey) {
        if (state.value.sensors.none { it.key() == metric }) return
        mutableState.value = state.value.copy(selected = metric)
        load(false)
    }
    fun period(period: HistoryPeriod) { mutableState.value = state.value.copy(period = period); load(false) }
    fun refresh() = load(true)
    private fun load(reloadSensors: Boolean) {
        val saved = token ?: return
        if (state.value.house.isBlank() || rejectedToken != null) return
        job?.cancel()
        val version = ++generation
        val scope = state.value
        val until = clock()
        val since = (until - scope.period.seconds).coerceAtLeast(0)
        mutableState.value = scope.copy(busy = true, error = null, readings = emptyList(), nextCursor = null, since = since, until = until)
        job = viewModelScope.launch {
            try {
                val sensors = if (reloadSensors) repository.sensors(scope.server, saved, scope.house) else scope.sensors
                currentCoroutineContext().ensureActive()
                val selected = scope.selected?.takeIf { key -> sensors.any { it.key() == key } } ?: sensors.firstOrNull()?.key()
                mutableState.value = state.value.copy(sensors = sensors, selected = selected)
                val page = if (selected == null) HistoryPage(emptyList()) else repository.history(scope.server, saved, scope.house, selected, since, until)
                currentCoroutineContext().ensureActive()
                // Latest metadata labels the selected metric; do not label old remapped history as its new type.
                val metadata = sensors.firstOrNull { it.key() == selected }
                requireConsistentHistory(page.points, metadata?.type, metadata?.unit)
                mutableState.value = state.value.copy(readings = page.points, nextCursor = page.next_cursor)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (version != generation) return@launch
                val expired = (error as? AccountHttpException)?.status == 401
                if (expired) rejectedToken = saved
                mutableState.value = state.value.copy(error = if (expired) AnalyticsError.SESSION_EXPIRED else AnalyticsError.UNAVAILABLE, sensors = if (expired) emptyList() else state.value.sensors, readings = emptyList())
            } finally { if (version == generation) mutableState.value = state.value.copy(busy = false) }
        }
    }
    fun loadMore() {
        val scope = state.value
        val saved = token ?: return
        val selected = scope.selected ?: return
        val cursor = scope.nextCursor ?: return
        if (scope.busy || rejectedToken != null) return
        val version = ++generation
        mutableState.value = scope.copy(busy = true, error = null)
        job = viewModelScope.launch {
            try {
                val page = repository.history(scope.server, saved, scope.house, selected, scope.since, scope.until, cursor)
                currentCoroutineContext().ensureActive()
                val allRows = scope.readings + page.points
                val metadata = scope.sensors.firstOrNull { it.key() == selected }
                requireConsistentHistory(allRows, metadata?.type, metadata?.unit)
                val combined = allRows.distinctBy { it.observed_at }.sortedBy { it.observed_at }
                require(page.next_cursor == null || page.next_cursor != cursor)
                mutableState.value = state.value.copy(readings = combined, nextCursor = page.next_cursor)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (version != generation) return@launch
                val expired = (error as? AccountHttpException)?.status == 401
                if (expired) rejectedToken = saved
                mutableState.value = state.value.copy(error = if (expired) AnalyticsError.SESSION_EXPIRED else AnalyticsError.UNAVAILABLE, readings = if (expired) emptyList() else scope.readings)
            } finally { if (version == generation) mutableState.value = state.value.copy(busy = false) }
        }
    }
    override fun onCleared() { generation++; job?.cancel(); token = null; repository.close() }
}
