package com.example.smartcoop.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "eggs")
data class EggEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val date: Long,  // timestamp в миллисекундах
    val count: Int   // количество яиц
)