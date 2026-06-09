package com.example.smartcoop.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sensors")
data class SensorEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,     // когда было измерение
    val waterLevel: Int,     // вода 0-100%
    val feedLevel: Int,      // корм 0-100%
    val temperature: Float,  // температура
    val airQuality: Int      // загрязнение воздуха 0-100%
)