package com.example.smartcoop

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.smartcoop.data.SmartCoopRepository
import kotlinx.coroutines.runBlocking
import kotlin.random.Random

class NotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val type = intent.getStringExtra("type") ?: return

        val repository = SmartCoopRepository(context)
        val notifier = NotificationHelper(context)

        val water = getCurrentWaterLevel()
        val feed = getCurrentFeedLevel()
        val eggs = runBlocking { getCurrentEggCount(repository) }

        when (type) {
            "morning" -> {
                notifier.sendNotification(
                    "🌅 Доброе утро!",
                    "Вода: $water% | Корм: $feed% | Яиц сегодня: ${eggs.toInt()} шт"
                )
            }
            "evening" -> {
                val todayEggs = runBlocking { getTodayEggs(repository) }
                notifier.sendNotification(
                    "🌙 Итоги дня",
                    "Сегодня собрано: ${todayEggs.toInt()} яиц\nВода: $water% | Корм: $feed%"
                )
            }
        }

        checkProblems(context, water, feed, eggs)
    }

    private fun getCurrentWaterLevel(): Int = Random.nextInt(30, 100)
    private fun getCurrentFeedLevel(): Int = Random.nextInt(20, 100)

    private suspend fun getCurrentEggCount(repository: SmartCoopRepository): Int {
        val today = getTodayDate()
        val eggs = repository.getLast7DaysEggs()
        return eggs.find { it.first == today }?.second ?: 0
    }

    private suspend fun getTodayEggs(repository: SmartCoopRepository): Int {
        return getCurrentEggCount(repository)
    }

    private fun getTodayDate(): String {
        val format = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        return format.format(java.util.Date())
    }

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