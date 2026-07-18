package com.tired.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tired.app.data.local.entity.FatigueAnalysisEntity

@Dao
interface FatigueAnalysisDao {
    @Query("SELECT * FROM fatigue_analysis_cache WHERE date = :date")
    suspend fun getByDate(date: String): FatigueAnalysisEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: FatigueAnalysisEntity)

    @Query("SELECT * FROM fatigue_analysis_cache ORDER BY date DESC LIMIT 7")
    suspend fun getRecentWeek(): List<FatigueAnalysisEntity>

    @Query("DELETE FROM fatigue_analysis_cache WHERE date < :beforeDate")
    suspend fun deleteOlderThan(beforeDate: String)
}
