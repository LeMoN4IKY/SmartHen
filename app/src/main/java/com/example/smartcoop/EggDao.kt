package com.example.smartcoop.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface EggDao {

    @Insert
    suspend fun insertEgg(egg: EggEntity)

    @Query("SELECT * FROM eggs WHERE date >= :fromDate ORDER BY date DESC")
    suspend fun getEggsFromDate(fromDate: Long): List<EggEntity>

    @Query("SELECT * FROM eggs WHERE date >= :fromDate ORDER BY date ASC")
    fun getEggsFlowFromDate(fromDate: Long): Flow<List<EggEntity>>

    @Query("SELECT SUM(count) FROM eggs WHERE date >= :fromDate")
    suspend fun getTotalEggsFromDate(fromDate: Long): Int

    @Query("DELETE FROM eggs WHERE date < :beforeDate")
    suspend fun deleteOldEggs(beforeDate: Long)
}