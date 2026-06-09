package com.example.smartcoop

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.example.smartcoop.data.SmartCoopRepository
import kotlinx.coroutines.runBlocking
import kotlin.random.Random

class NotificationWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        return try {
            val repository = SmartCoopRepository(applicationContext)
            val notifier = NotificationHelper(applicationContext)

            val water = Random.nextInt(30, 100)
            val feed = Random.nextInt(20, 100)
            val todayEggs = runBlocking { getTodayEggs(repository) }

            notifier.sendNotification(
                "🌅 Умный курятник",
                "Вода: $water% | Корм: $feed% | Яиц сегодня: ${todayEggs.toInt()} шт"
            )

            if (feed <= 10) {
                notifier.sendNotification("⚠️ Корм заканчивается!", "Осталось $feed% корма!")
            }
            if (water <= 10) {
                notifier.sendNotification("⚠️ Вода заканчивается!", "Осталось $water% воды!")
            }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private suspend fun getTodayEggs(repository: SmartCoopRepository): Int {
        val today = getTodayDate()
        val eggs = repository.getLast7DaysEggs()
        return eggs.find { it.first == today }?.second ?: 0
    }

    private fun getTodayDate(): String {
        val format = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        return format.format(java.util.Date())
    }
}