package com.example.smartcoop.data

import android.content.Context
import com.example.smartcoop.DataManager
import com.example.smartcoop.RetrofitHelper
import com.example.smartcoop.SensorHistoryPoint
import com.example.smartcoop.SensorStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Менеджер датчиков — работа с данными датчиков через сервер
 * Поддерживает кэширование для офлайн-режима
 */
class SensorManager(private val context: Context) {

    // Поток с текущим списком датчиков
    private val _sensors = MutableStateFlow<List<Sensor>>(emptyList())
    val sensors: StateFlow<List<Sensor>> = _sensors

    // Флаг, показывающий, что данные из кэша (для UI)
    private val _isFromCache = MutableStateFlow(false)
    val isFromCache: StateFlow<Boolean> = _isFromCache

    // ID текущего курятника — берём из DataManager
    private var currentCoopId: String
        get() = DataManager.getCurrentCoopIdOrDefault()
        set(value) { DataManager.currentCoopId = value }

    private val api = RetrofitHelper.api

    /**
     * Загрузить датчики для текущего курятника
     * Используется для автообновления
     */
    suspend fun loadSensors() {
        loadSensors(currentCoopId)
    }

    /**
     * Загрузить датчики для указанного курятника
     * При ошибке показывает кэшированные данные
     */
    suspend fun loadSensors(coopId: String) {
        currentCoopId = coopId
        try {
            // Запрос к серверу
            val response = api.getSensors(coopId)
            _sensors.value = response
            _isFromCache.value = false  // Данные с сервера

            // Сохраняем в кэш
            DataManager.cachedSensors = response
            DataManager.lastUpdateTime = System.currentTimeMillis()

        } catch (e: Exception) {
            // Если есть кэш — показываем его
            if (DataManager.cachedSensors.isNotEmpty()) {
                _sensors.value = DataManager.cachedSensors
                _isFromCache.value = true  // Данные из кэша
            } else {
                // Нет кэша — пробрасываем ошибку
                _isFromCache.value = false
                throw e
            }
        }
    }

    /**
     * Проверить, показываются ли данные из кэша
     */
    fun isDataFromCache(): Boolean {
        return _isFromCache.value
    }

    /**
     * Обновить значение датчика
     */
    suspend fun updateSensorValue(sensorId: String, newValue: Float) {
        try {
            api.updateSensor(currentCoopId, sensorId, newValue)
            val list = _sensors.value.toMutableList()
            val index = list.indexOfFirst { it.id == sensorId }
            if (index >= 0) {
                list[index] = list[index].copy(currentValue = newValue)
                _sensors.value = list
                // Обновляем кэш
                DataManager.cachedSensors = list
            }
        } catch (e: Exception) {
            throw e
        }
    }

    /**
     * Обновить статус онлайн/офлайн датчика
     */
    suspend fun updateSensorStatus(sensorId: String, isOnline: Boolean) {
        val list = _sensors.value.toMutableList()
        val index = list.indexOfFirst { it.id == sensorId }
        if (index >= 0) {
            list[index] = list[index].copy(isOnline = isOnline)
            _sensors.value = list
        }
    }

    /**
     * Проверить все датчики (диагностика)
     */
    suspend fun checkAllSensors(): List<SensorStatus> {
        return try {
            val response = api.checkSensors(currentCoopId)
            response.forEach { status ->
                updateSensorStatus(status.sensorId, status.isOnline)
            }
            response
        } catch (e: Exception) {
            throw e
        }
    }

    /**
     * Собрать яйца — реальное значение с датчика, обнуление после сбора
     */
    suspend fun collectEggs(): Int {
        try {
            // Находим датчик яиц
            val eggSensor = _sensors.value.find { it.type == SensorType.EGG_COUNT }
                ?: return 0

            // Текущее количество яиц
            val collectedCount = eggSensor.currentValue.toInt()
            if (collectedCount <= 0) {
                return 0
            }

            // Логируем сбор на сервере
            api.collectEggs(currentCoopId)

            // Обнуляем датчик на сервере
            api.updateSensor(currentCoopId, eggSensor.id, 0f)

            // Обновляем локальное состояние
            val list = _sensors.value.toMutableList()
            val index = list.indexOfFirst { it.id == eggSensor.id }
            if (index >= 0) {
                list[index] = list[index].copy(currentValue = 0f)
                _sensors.value = list
                // Обновляем кэш
                DataManager.cachedSensors = list
            }

            return collectedCount
        } catch (e: Exception) {
            throw e
        }
    }

    /**
     * Получить историю датчика (для графиков)
     */
    suspend fun getSensorHistory(sensorId: String, days: Int = 7): List<SensorHistoryPoint> {
        return try {
            api.getSensorHistory(currentCoopId, sensorId, days)
        } catch (e: Exception) {
            throw e
        }
    }
}