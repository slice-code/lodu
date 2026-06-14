package com.example.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.GenerationHistory
import kotlinx.coroutines.flow.Flow

@Dao
interface GenerationHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(item: GenerationHistory): Long

    @Query("SELECT * FROM generation_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<GenerationHistory>>

    @Query("DELETE FROM generation_history WHERE id = :id")
    suspend fun deleteHistoryById(id: Long)

    @Query("DELETE FROM generation_history")
    suspend fun deleteAllHistory()
}
