package com.example.smartcoop.data

import com.google.gson.annotations.SerializedName

enum class SensorType {
    WATER,
    FEED,
    TEMPERATURE,
    HEATING,
    AIR_QUALITY,
    EGG_COUNT
}

data class Sensor(
    @SerializedName("id")
    val id: String,

    @SerializedName("type")
    val type: SensorType,

    @SerializedName("name")
    val name: String,

    @SerializedName("unit")
    val unit: String,

    @SerializedName("current_value")
    val currentValue: Float,

    @SerializedName("min_value")
    val minValue: Float = 0f,

    @SerializedName("max_value")
    val maxValue: Float = 100f,

    @SerializedName("warning_threshold")
    val warningThreshold: Float = 20f,

    @SerializedName("is_online")
    val isOnline: Boolean = true
)