package com.smarthen.domain

/** IDs are scoped to a poultry house: legacy sensor IDs are not globally unique. */
data class DeviceId(val houseId: String, val deviceId: String) {
    init {
        require(houseId.isNotBlank())
        require(deviceId.isNotBlank())
    }
}

enum class SensorKind { TEMPERATURE, HUMIDITY, WATER, FEED, AIR_QUALITY, EGG_COUNT, HEATING, UNKNOWN }
enum class ReadingSource { LIVE, CACHE, DEMO }
enum class Freshness { FRESH, STALE, OFFLINE, UNKNOWN, INVALID }

/** Unit and physical bounds come from a device contract, never guessed from a sensor label. */
data class SensorSnapshot(
    val device: DeviceId,
    val kind: SensorKind,
    val value: Double,
    val unit: String,
    val observedAtEpochSeconds: Long?,
    val reportedOnline: Boolean,
    val source: ReadingSource,
) {
    fun freshness(nowEpochSeconds: Long, maximumAgeSeconds: Long): Freshness {
        require(maximumAgeSeconds > 0)
        require(nowEpochSeconds >= 0)
        if (!value.isFinite()) return Freshness.INVALID
        if (!reportedOnline) return Freshness.OFFLINE
        val observed = observedAtEpochSeconds ?: return Freshness.UNKNOWN
        // Zero is the legacy server's missing timestamp; future timestamps cannot prove freshness.
        if (observed <= 0 || observed > nowEpochSeconds) return Freshness.UNKNOWN
        return if (nowEpochSeconds - observed <= maximumAgeSeconds) Freshness.FRESH else Freshness.STALE
    }
}

data class MeasurementBounds(val minimum: Double, val maximum: Double) {
    init {
        require(minimum.isFinite() && maximum.isFinite() && minimum <= maximum)
    }

    fun contains(value: Double): Boolean = value.isFinite() && value in minimum..maximum
}
