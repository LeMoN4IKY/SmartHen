package com.example.smartcoop.data

enum class SensorType {
    WATER,
    FEED,
    TEMPERATURE,
    HEATING,
    AIR_QUALITY,
    EGG_COUNT
}

data class Sensor(
    val id: String,
    val type: SensorType,
    val name: String,
    val unit: String,
    val currentValue: Float,
    val minValue: Float = 0f,
    val maxValue: Float = 100f,
    val warningThreshold: Float = 20f,
    val isOnline: Boolean = true  // ✅ добавлено для статуса
)