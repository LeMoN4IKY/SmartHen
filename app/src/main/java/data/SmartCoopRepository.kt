package com.example.smartcoop.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class SmartCoopRepository(private val context: Context) {

    private val database = AppDatabase.getInstance(context)
    private val eggDao = database.eggDao()
    private val sensorDao = database.sensorDao()

    // ========== ЯЙЦА ==========

    suspend fun addEggs(count: Int) {
        withContext(Dispatchers.IO) {
            val today = getTodayTimestamp()
            eggDao.insertEgg(EggEntity(date = today, count = count))
        }
    }

    suspend fun getTotalEggsThisWeek(): Int {
        return withContext(Dispatchers.IO) {
            val weekAgo = getTodayTimestamp() - (7L * 24 * 60 * 60 * 1000)
            eggDao.getTotalEggsFromDate(weekAgo)
        }
    }

    fun getWeeklyEggsFlow(): Flow<List<EggEntity>> {
        val weekAgo = getTodayTimestamp() - (7L * 24 * 60 * 60 * 1000)
        return eggDao.getEggsFlowFromDate(weekAgo)
    }

    suspend fun getLast7DaysEggs(): List<Pair<String, Int>> {
        return getEggsForLastDays(7)
    }

    // ========== НОВЫЕ МЕТОДЫ ДЛЯ АНАЛИТИКИ ==========

    suspend fun getEggsForLastDays(days: Int): List<Pair<String, Int>> {
        return withContext(Dispatchers.IO) {
            val fromDate = getTodayTimestamp() - (days.toLong() * 24 * 60 * 60 * 1000)
            val eggs = eggDao.getEggsFromDate(fromDate)

            val result = mutableListOf<Pair<String, Int>>()
            val calendar = Calendar.getInstance()

            for (i in (days - 1) downTo 0) {
                calendar.time = Date()
                calendar.add(Calendar.DAY_OF_YEAR, -i)
                val date = getDateString(calendar.timeInMillis)
                val count = eggs.filter { getDateString(it.date) == date }.sumOf { it.count }
                result.add(Pair(date, count))
            }
            result
        }
    }

    suspend fun getTemperaturesForLastDays(days: Int): List<Pair<String, Float>> {
        return withContext(Dispatchers.IO) {
            val fromDate = getTodayTimestamp() - (days.toLong() * 24 * 60 * 60 * 1000)
            val data = sensorDao.getTemperaturesFromDate(fromDate)
            val grouped = data.groupBy { getDateString(it.timestamp) }

            val result = mutableListOf<Pair<String, Float>>()
            val calendar = Calendar.getInstance()

            for (i in (days - 1) downTo 0) {
                calendar.time = Date()
                calendar.add(Calendar.DAY_OF_YEAR, -i)
                val date = getDateString(calendar.timeInMillis)
                val temps = grouped[date]?.map { it.temperature } ?: emptyList()
                val avgTemp = if (temps.isNotEmpty()) temps.average().toFloat() else 0f
                result.add(Pair(date, avgTemp))
            }
            result
        }
    }

    suspend fun getEggsForLastYearByMonth(): List<Pair<String, Int>> {
        return withContext(Dispatchers.IO) {
            val fromDate = getTodayTimestamp() - (365L * 24 * 60 * 60 * 1000)
            val eggs = eggDao.getEggsFromDate(fromDate)

            val result = mutableListOf<Pair<String, Int>>()
            val calendar = Calendar.getInstance()
            val monthFormat = SimpleDateFormat("MMM", Locale.getDefault())

            for (i in 11 downTo 0) {
                calendar.time = Date()
                calendar.add(Calendar.MONTH, -i)
                val monthStart = getMonthStartTimestamp(calendar.timeInMillis)
                val monthEnd = getMonthEndTimestamp(calendar.timeInMillis)
                val count = eggs.filter { it.date in monthStart..monthEnd }.sumOf { it.count }
                val monthName = monthFormat.format(Date(monthStart))
                result.add(Pair(monthName, count))
            }
            result
        }
    }

    // ========== ДАТЧИКИ ==========

    suspend fun saveSensorData(water: Int, feed: Int, temp: Float, air: Int) {
        withContext(Dispatchers.IO) {
            val sensor = SensorEntity(
                timestamp = System.currentTimeMillis(),
                waterLevel = water,
                feedLevel = feed,
                temperature = temp,
                airQuality = air
            )
            sensorDao.insertSensorData(sensor)
        }
    }

    suspend fun getLatestSensor(): SensorEntity? {
        return withContext(Dispatchers.IO) {
            sensorDao.getLatestSensor()
        }
    }

    suspend fun getLast7DaysTemperature(): List<Pair<String, Float>> {
        return getTemperaturesForLastDays(7)
    }

    // ========== ВСПОМОГАТЕЛЬНЫЕ ==========

    private fun getTodayTimestamp(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    private fun getWeekAgoTimestamp(): Long {
        return getTodayTimestamp() - (7L * 24 * 60 * 60 * 1000)
    }

    private fun getDateString(timestamp: Long): String {
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return format.format(Date(timestamp))
    }

    private fun getMonthStartTimestamp(timestamp: Long): Long {
        val calendar = Calendar.getInstance()
        calendar.time = Date(timestamp)
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    private fun getMonthEndTimestamp(timestamp: Long): Long {
        val calendar = Calendar.getInstance()
        calendar.time = Date(timestamp)
        calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        return calendar.timeInMillis
    }

    // Получить яйца за период с byDate по toDate (по дням)
    suspend fun getEggsForDateRange(fromDate: Long, toDate: Long): List<Pair<String, Int>> {
        return withContext(Dispatchers.IO) {
            val eggs = eggDao.getEggsFromDate(fromDate)
            val result = mutableListOf<Pair<String, Int>>()
            val calendar = Calendar.getInstance()
            var currentDate = fromDate
            while (currentDate <= toDate) {
                val dateStr = getDateString(currentDate)
                val count = eggs.filter { getDateString(it.date) == dateStr }.sumOf { it.count }
                result.add(Pair(dateStr, count))
                currentDate += 24 * 60 * 60 * 1000
            }
            result
        }
    }

    // Получить температуру за период с byDate по toDate (по дням)
    suspend fun getTemperaturesForDateRange(fromDate: Long, toDate: Long): List<Pair<String, Float>> {
        return withContext(Dispatchers.IO) {
            val data = sensorDao.getTemperaturesFromDate(fromDate)
            val grouped = data.groupBy { getDateString(it.timestamp) }

            val result = mutableListOf<Pair<String, Float>>()
            var currentDate = fromDate
            while (currentDate <= toDate) {
                val dateStr = getDateString(currentDate)
                val temps = grouped[dateStr]?.map { it.temperature } ?: emptyList()
                val avgTemp = if (temps.isNotEmpty()) temps.average().toFloat() else 0f
                result.add(Pair(dateStr, avgTemp))
                currentDate += 24 * 60 * 60 * 1000
            }
            result
        }
    }
}