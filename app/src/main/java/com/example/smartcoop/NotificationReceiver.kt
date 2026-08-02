package com.example.smartcoop

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import com.example.smartcoop.data.SensorManager
import com.example.smartcoop.data.SensorType

/**
 * Получатель уведомлений.
 * Отправляет утренние и вечерние уведомления о состоянии курятника.
 * Данные получает с сервера через SensorManager.
 */
class NotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val type = intent.getStringExtra("type") ?: return

        val notifier = NotificationHelper(context)
        val sensorManager = SensorManager(context)

        // Загружаем актуальные данные с сервера
        runBlocking {
            val coopId = DataManager.getCurrentCoopIdOrDefault()
            sensorManager.loadSensors(coopId)
        }

        // Получаем значения датчиков
        val sensors = runBlocking { sensorManager.sensors.value }
        val water = sensors.find { it.type == SensorType.WATER }?.currentValue?.toInt() ?: 0
        val feed = sensors.find { it.type == SensorType.FEED }?.currentValue?.toInt() ?: 0
        val eggs = sensors.find { it.type == SensorType.EGG_COUNT }?.currentValue?.toInt() ?: 0

        when (type) {
            "morning" -> {
                notifier.sendNotification(
                    "🌅 Доброе утро!",
                    "Вода: $water% | Корм: $feed% | Яиц накоплено: $eggs шт"
                )
            }
            "evening" -> {
                notifier.sendNotification(
                    "🌙 Итоги дня",
                    "Яиц накоплено: $eggs шт\nВода: $water% | Корм: $feed%"
                )
            }
        }

        // Проверка критических состояний
        checkProblems(context, water, feed, eggs)
    }

    /**
     * Проверка критических состояний и отправка срочных уведомлений
     */
    private fun checkProblems(context: Context, water: Int, feed: Int, eggs: Int) {
        val notifier = NotificationHelper(context)

        when {
            feed < 15 -> notifier.sendNotification(
                "⚠️ СРОЧНО! Корм заканчивается",
                "Осталось $feed% корма! Пополните запасы."
            )
            water < 15 -> notifier.sendNotification(
                "⚠️ СРОЧНО! Вода заканчивается",
                "Осталось $water% воды. Добавьте воду!"
            )
            eggs > 40 -> notifier.sendNotification(
                "🥚 Пора собрать яйца!",
                "Накопилось $eggs яиц. Место почти заполнено!"
            )
        }
    }
}