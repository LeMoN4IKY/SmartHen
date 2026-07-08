package com.example.smartcoop.data

import android.content.Context
import com.example.smartcoop.RetrofitHelper
import com.example.smartcoop.SensorStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SensorManager(private val context: Context) {

    private val _sensors = MutableStateFlow<List<Sensor>>(emptyList())
    val sensors: StateFlow<List<Sensor>> = _sensors

    private var currentCoopId: String = "1"
    private val api = RetrofitHelper.api

    suspend fun loadSensors(coopId: String) {
        currentCoopId = coopId
        try {
            val response = api.getSensors(coopId)
            _sensors.value = response
        } catch (e: Exception) {
            e.printStackTrace()
            _sensors.value = emptyList()
        }
    }

    suspend fun updateSensorValue(sensorId: String, newValue: Float) {
        try {
            api.updateSensor(currentCoopId, sensorId, newValue)
            val list = _sensors.value.toMutableList()
            val index = list.indexOfFirst { it.id == sensorId }
            if (index >= 0) {
                list[index] = list[index].copy(currentValue = newValue)
                _sensors.value = list
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun updateSensorStatus(sensorId: String, isOnline: Boolean) {
        val list = _sensors.value.toMutableList()
        val index = list.indexOfFirst { it.id == sensorId }
        if (index >= 0) {
            list[index] = list[index].copy(isOnline = isOnline)
            _sensors.value = list
        }
    }

    suspend fun checkAllSensors(): List<SensorStatus> {
        return try {
            val response = api.checkSensors(currentCoopId)
            response.forEach { status ->
                updateSensorStatus(status.sensorId, status.isOnline)
            }
            response
        } catch (e: Exception) {
            emptyList()
        }
    }
}