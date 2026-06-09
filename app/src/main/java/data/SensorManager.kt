package com.example.smartcoop.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SensorManager(private val context: Context) {

    private val _sensors = MutableStateFlow<List<Sensor>>(emptyList())
    val sensors: StateFlow<List<Sensor>> = _sensors

    suspend fun loadSensors(coopId: String) {
        val mockSensors = listOf(
            Sensor(
                id = "sensor_water",
                type = SensorType.WATER,
                name = "Вода",
                unit = "%",
                currentValue = 65f
            ),
            Sensor(
                id = "sensor_feed",
                type = SensorType.FEED,
                name = "Корм",
                unit = "%",
                currentValue = 42f
            ),
            Sensor(
                id = "sensor_temp",
                type = SensorType.TEMPERATURE,
                name = "Температура",
                unit = "°C",
                currentValue = 22.5f,
                minValue = -10f,
                maxValue = 50f,
                warningThreshold = 19f
            ),
            Sensor(
                id = "sensor_heating",
                type = SensorType.HEATING,
                name = "Отопление",
                unit = "",
                currentValue = 0f,
                minValue = 0f,
                maxValue = 1f
            ),
            Sensor(
                id = "sensor_air",
                type = SensorType.AIR_QUALITY,
                name = "Загрязнение воздуха",
                unit = "%",
                currentValue = 35f
            ),
            Sensor(
                id = "sensor_eggs",
                type = SensorType.EGG_COUNT,
                name = "Накоплено яиц",
                unit = "шт",
                currentValue = 28f,
                maxValue = 20f,
                warningThreshold = 10f
            )
        )
        _sensors.value = mockSensors
    }

    suspend fun updateSensorValue(sensorId: String, newValue: Float) {
        val currentList = _sensors.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == sensorId }
        if (index >= 0) {
            currentList[index] = currentList[index].copy(currentValue = newValue)
            _sensors.value = currentList

            if (currentList[index].type == SensorType.TEMPERATURE) {
                val heatingIndex = currentList.indexOfFirst { it.type == SensorType.HEATING }
                if (heatingIndex >= 0) {
                    val isHeatingOn = newValue < 19f
                    val newHeatingValue = if (isHeatingOn) 1f else 0f
                    currentList[heatingIndex] = currentList[heatingIndex].copy(currentValue = newHeatingValue)
                    _sensors.value = currentList
                }
            }
        }
    }
}