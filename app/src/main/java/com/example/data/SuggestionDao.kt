package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SuggestionDao {
    @Query("SELECT * FROM suggestions ORDER BY timestamp DESC")
    fun getAllSuggestions(): Flow<List<SuggestionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSuggestion(suggestion: SuggestionEntity)

    @Query("DELETE FROM suggestions")
    suspend fun clearAllSuggestions()
}
