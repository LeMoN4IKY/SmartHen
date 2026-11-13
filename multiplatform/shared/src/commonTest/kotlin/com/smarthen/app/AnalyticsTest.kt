package com.smarthen.app

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class AnalyticsTest {
    private fun row(value: Double, time: Long = 1000) = MetricReading("d", "s", "TEMPERATURE", "C", value, time, time)
    private open inner class Repository : AnalyticsRepository {
        override suspend fun sensors(server: String, token: String, house: String) = listOf(row(10.0))
        override suspend fun history(server: String, token: String, house: String, metric: MetricKey, since: Long, until: Long, cursor: String?) = HistoryPage(listOf(row(10.0)))
    }
    @Test fun sampleMeanIsNotEggProductionAndHandlesConstantOrEmptySeries() {
        assertNull(sampleStatistics(emptyList()))
        assertEquals(SampleStatistics(5.0, 5.0, 5.0, 2), sampleStatistics(listOf(row(5.0), row(5.0))))
        assertEquals(5.0, sampleStatistics(listOf(row(10.0), row(0.0)))?.sampleMean)
        assertEquals(0.0, sampleStatistics(listOf(row(Double.MAX_VALUE), row(-Double.MAX_VALUE)))?.sampleMean)
    }
    @Test fun historyFromPreviousHouseCannotReplaceCurrentSelection() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val gate = CompletableDeferred<Unit>()
            val vm = AnalyticsViewModel(object : Repository() {
                override suspend fun history(server: String, token: String, house: String, metric: MetricKey, since: Long, until: Long, cursor: String?): HistoryPage {
                    if (house == "old") withContext(NonCancellable) { gate.await() }
                    return HistoryPage(listOf(row(if (house == "old") 1.0 else 2.0)))
                }
            }, { 2000 })
            vm.configure("https://example.com", "token", "old"); runCurrent()
            vm.configure("https://example.com", "token", "new"); runCurrent()
            gate.complete(Unit); runCurrent()
            assertEquals(2.0, vm.state.value.readings.single().value)
        } finally { Dispatchers.resetMain() }
    }
    @Test fun paginationRetainsFixedWindowAndCombinesInTimeOrder() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val windows = mutableListOf<Pair<Long, Long>>()
            val vm = AnalyticsViewModel(object : Repository() {
                override suspend fun history(server: String, token: String, house: String, metric: MetricKey, since: Long, until: Long, cursor: String?): HistoryPage {
                    windows += since to until
                    return if (cursor == null) HistoryPage(listOf(row(20.0, 1800)), "next") else HistoryPage(listOf(row(10.0, 1000)))
                }
            }, { 2000 })
            vm.configure("https://example.com", "token", "house"); runCurrent()
            assertEquals("next", vm.state.value.nextCursor)
            vm.loadMore(); runCurrent()
            assertEquals(listOf(1000L, 1800L), vm.state.value.readings.map { it.observed_at })
            assertNull(vm.state.value.nextCursor)
            assertEquals(windows.first(), windows.last())
        } finally { Dispatchers.resetMain() }
    }
    @Test fun unauthorizedHistoryExposesScopedSessionFailure() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val vm = AnalyticsViewModel(object : Repository() {
                override suspend fun sensors(server: String, token: String, house: String): List<MetricReading> = throw AccountHttpException(401)
            })
            vm.configure("https://example.com", "expired", "house"); runCurrent()
            assertEquals(AnalyticsError.SESSION_EXPIRED, vm.state.value.error)
            assertEquals("expired", vm.expiredSessionToken())
            vm.configure("https://example.com", null, "house")
            assertNull(vm.expiredSessionToken())
        } finally { Dispatchers.resetMain() }
    }
    @Test fun historyRejectsMixedQuantitiesEvenWithTheSameUnit() {
        val water = row(50.0).copy(type = "WATER", unit = "%")
        val humidity = water.copy(type = "HUMIDITY", observed_at = 999)
        assertFailsWith<IllegalArgumentException> { requireConsistentHistory(listOf(water, humidity)) }
        assertFailsWith<IllegalArgumentException> { requireConsistentHistory(listOf(water, water.copy(unit = "L"))) }
        assertFailsWith<IllegalArgumentException> { requireConsistentHistory(listOf(water), "HUMIDITY") }
        assertFailsWith<IllegalArgumentException> { requireConsistentHistory(listOf(water), "WATER", "L") }
        requireConsistentHistory(listOf(water), "WATER", "%")
    }

    @Test fun historicalTypeCannotBeLabelledAsCurrentSensorType() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val vm = AnalyticsViewModel(object : Repository() {
                override suspend fun sensors(server: String, token: String, house: String) = listOf(row(50.0).copy(type = "HUMIDITY", unit = "%"))
                override suspend fun history(server: String, token: String, house: String, metric: MetricKey, since: Long, until: Long, cursor: String?) =
                    HistoryPage(listOf(row(50.0).copy(type = "WATER", unit = "%")))
            }, { 2000 })
            vm.configure("https://example.com", "token", "house"); runCurrent()
            assertEquals(AnalyticsError.UNAVAILABLE, vm.state.value.error)
            assertTrue(vm.state.value.readings.isEmpty())
        } finally { Dispatchers.resetMain() }
    }

    @Test fun mismatchedPagePreservesPreviousSamplesAndCursor() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val original = row(50.0, 1800).copy(type = "WATER", unit = "%")
            val vm = AnalyticsViewModel(object : Repository() {
                override suspend fun sensors(server: String, token: String, house: String) = listOf(original)
                override suspend fun history(server: String, token: String, house: String, metric: MetricKey, since: Long, until: Long, cursor: String?) =
                    if (cursor == null) HistoryPage(listOf(original), "next")
                    // Same timestamp exercises validation before duplicate removal.
                    else HistoryPage(listOf(original.copy(type = "HUMIDITY")))
            }, { 2000 })
            vm.configure("https://example.com", "token", "house"); runCurrent()
            vm.loadMore(); runCurrent()
            assertEquals(AnalyticsError.UNAVAILABLE, vm.state.value.error)
            assertEquals(listOf(original), vm.state.value.readings)
            assertEquals("next", vm.state.value.nextCursor)
        } finally { Dispatchers.resetMain() }
    }

}
