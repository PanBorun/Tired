package com.tired.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.tired.app.domain.model.UserDigitalProfile

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val id: Int = 1,
    val dangerTimeRange: String = "23:00 - 01:30",
    val triggerAppHabits: String = "短视频、社交平台信息流",
    val reliefPreferences: String = "偏好白噪音/轻音乐，讨厌大道理说教，温和同理心",
    val contextBio: String = "白天高强度脑力消耗，晚上下班后容易通过手机报复性放松",
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toDomain(): UserDigitalProfile = UserDigitalProfile(
        dangerTimeRange = dangerTimeRange,
        triggerAppHabits = triggerAppHabits,
        reliefPreferences = reliefPreferences,
        contextBio = contextBio
    )

    companion object {
        fun fromDomain(domain: UserDigitalProfile, id: Int = 1): UserProfileEntity {
            return UserProfileEntity(
                id = id,
                dangerTimeRange = domain.dangerTimeRange,
                triggerAppHabits = domain.triggerAppHabits,
                reliefPreferences = domain.reliefPreferences,
                contextBio = domain.contextBio,
                updatedAt = System.currentTimeMillis()
            )
        }
    }
}
