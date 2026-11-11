package com.smarthen.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class TelemetryTest {
    private val device = DeviceId("house-1", "heater-1")
    private val snapshot = SensorSnapshot(device, SensorKind.HEATING, 1.0, "", 1_000, true, ReadingSource.LIVE)

    @Test fun ageBoundaryAndMissingTimestamps() {
        assertEquals(Freshness.FRESH, snapshot.freshness(1_060, 60))
        assertEquals(Freshness.STALE, snapshot.freshness(1_061, 60))
        assertEquals(Freshness.UNKNOWN, snapshot.freshness(999, 60))
        for (timestamp in listOf(null, 0L, -1L)) {
            assertEquals(Freshness.UNKNOWN, snapshot.copy(observedAtEpochSeconds = timestamp).freshness(1_000, 60))
        }
    }

    @Test fun invalidAndOfflineValuesAreNeverFresh() {
        for (value in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            assertEquals(Freshness.INVALID, snapshot.copy(value = value).freshness(1_000, 60))
        }
        assertEquals(Freshness.OFFLINE, snapshot.copy(reportedOnline = false).freshness(1_000, 60))
        assertFalse(MeasurementBounds(0.0, 100.0).contains(Double.NaN))
        assertFailsWith<IllegalArgumentException> { MeasurementBounds(100.0, 0.0) }
    }

    private fun eligible(
        reading: SensorSnapshot? = snapshot,
        connection: ConnectionState = ConnectionState.CONNECTED,
        now: Long = 1_000,
        authorized: Boolean = true,
        supported: Boolean = true,
        pending: Boolean = false,
    ) = commandEligibility(device, reading, connection, now, 60, authorized, supported, pending)

    @Test fun onlyFreshLiveAuthorizedDeviceCanReceiveCommands() {
        assertEquals(CommandEligibility.Eligible, eligible())
        assertIs<CommandEligibility.Blocked>(eligible(now = 1_061))
        assertIs<CommandEligibility.Blocked>(eligible(now = 999))
        assertIs<CommandEligibility.Blocked>(eligible(reading = null))
        assertIs<CommandEligibility.Blocked>(eligible(authorized = false))
        assertIs<CommandEligibility.Blocked>(eligible(supported = false))
        assertIs<CommandEligibility.Blocked>(eligible(pending = true))
        assertIs<CommandEligibility.Blocked>(eligible(reading = snapshot.copy(reportedOnline = false)))
        for (state in ConnectionState.entries.filter { it != ConnectionState.CONNECTED }) {
            assertIs<CommandEligibility.Blocked>(eligible(connection = state))
        }
        for (source in listOf(ReadingSource.CACHE, ReadingSource.DEMO)) {
            assertIs<CommandEligibility.Blocked>(eligible(reading = snapshot.copy(source = source)))
        }
    }

    @Test fun sameDeviceNameInDifferentHouseCannotAuthorizeCommand() {
        assertEquals(
            CommandEligibility.Blocked(CommandBlockReason.TARGET_MISMATCH),
            eligible(reading = snapshot.copy(device = DeviceId("house-2", "heater-1"))),
        )
        assertEquals(
            CommandEligibility.Blocked(CommandBlockReason.TARGET_MISMATCH),
            eligible(reading = snapshot.copy(device = DeviceId("house-1", "heater-2"))),
        )
    }
}
