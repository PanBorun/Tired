package com.tired.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "fatigue_analysis_cache")
data class FatigueAnalysisEntity(
    @PrimaryKey
    val date: String, // yyyy-MM-dd
    val fatigueScore: Int,
    val fatigueType: String,
    val summary: String,
    val suggestions: String, // JSON serialized list
    val detailedAnalysis: String,
    val isFromAI: Boolean,
    val cachedAt: Long = System.currentTimeMillis()
)
