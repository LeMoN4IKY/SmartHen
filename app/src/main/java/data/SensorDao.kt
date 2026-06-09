package com.example.smartcoop.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SensorDao {

    @Insert
    suspend fun insertSensorData(sensor: SensorEntity)

    @Query("SELECT * FROM sensors ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestSensor(): SensorEntity?

    @Query("SELECT * FROM sensors WHERE timestamp >= :fromDate ORDER BY timestamp DESC")
    fun getSensorsFromDate(fromDate: Long): Flow<List<SensorEntity>>

    @Query("DELETE FROM sensors WHERE timestamp < :beforeDate")
    suspend fun deleteOldSensors(beforeDate: Long)

    @Query("SELECT timestamp, temperature FROM sensors WHERE timestamp >= :fromDate ORDER BY timestamp ASC")
    suspend fun getTemperaturesFromDate(fromDate: Long): List<TemperatureData>
}

data class TemperatureData(
    val timestamp: Long,
    val temperature: Float
)