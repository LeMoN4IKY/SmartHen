package com.example.smartcoop

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.runBlocking
import com.example.smartcoop.data.SensorManager
import com.example.smartcoop.data.SensorType

/**
 * Worker для планирования уведомлений.
 * Отправляет уведомления о состоянии курятника в фоновом режиме.
 * Данные получает с сервера через SensorManager.
 */
class NotificationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val context = applicationContext
            val notifier = NotificationHelper(context)
            val sensorManager = SensorManager(context)

            // Загружаем актуальные данные с сервера
            val coopId = DataManager.getCurrentCoopIdOrDefault()
            sensorManager.loadSensors(coopId)

            // Получаем значения датчиков
            val sensors = sensorManager.sensors.value
            val water = sensors.find { it.type == SensorType.WATER }?.currentValue?.toInt() ?: 0
            val feed = sensors.find { it.type == SensorType.FEED }?.currentValue?.toInt() ?: 0
            val eggs = sensors.find { it.type == SensorType.EGG_COUNT }?.currentValue?.toInt() ?: 0

            // Отправляем уведомление
            notifier.sendNotification(
                "🐔 SmartHen — состояние курятника",
                "💧 Вода: $water% | 🌽 Корм: $feed% | 🥚 Яиц: $eggs шт"
            )

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }
}