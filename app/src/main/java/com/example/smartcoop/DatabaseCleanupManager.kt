package com.example.smartcoop.data

import android.content.Context
import androidx.work.*
import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeUnit

class DatabaseCleanupManager(private val context: Context) {

    fun startPeriodicCleanup() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .build()

        val workRequest = PeriodicWorkRequestBuilder<CleanupWorker>(
            1, TimeUnit.DAYS,
            1, TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "database_cleanup",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    fun cleanupNow() {
        runBlocking {
            val now = System.currentTimeMillis()
            val oneYearAgo = now - (365L * 24 * 60 * 60 * 1000)
            val database = AppDatabase.getInstance(context)
            database.eggDao().deleteOldEggs(oneYearAgo)
        }
    }
}

class CleanupWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    override fun doWork(): Result {
        return try {
            runBlocking {
                val now = System.currentTimeMillis()
                val oneYearAgo = now - (365L * 24 * 60 * 60 * 1000)
                val thirtyDaysAgo = now - (30L * 24 * 60 * 60 * 1000)

                val database = AppDatabase.getInstance(applicationContext)

                database.sensorDao().deleteOldSensors(thirtyDaysAgo)
                database.eggDao().deleteOldEggs(oneYearAgo)
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}