package com.tired.app.data.local.datasource

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.tired.app.domain.model.UserSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsDataSource @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val REMINDER_ENABLED = booleanPreferencesKey("reminder_enabled")
        val REMINDER_HOUR = intPreferencesKey("reminder_hour")
        val REMINDER_MINUTE = intPreferencesKey("reminder_minute")
        val DAILY_GOAL = intPreferencesKey("daily_goal")
        val LAST_CONSOLIDATED_DATE = stringPreferencesKey("last_consolidated_date")
    }

    suspend fun getLastConsolidatedDate(): String? {
        return context.settingsDataStore.data.map { it[Keys.LAST_CONSOLIDATED_DATE] }.firstOrNull()
    }

    suspend fun saveLastConsolidatedDate(date: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.LAST_CONSOLIDATED_DATE] = date
        }
    }

    fun observeSettings(): Flow<UserSettings> {
        return context.settingsDataStore.data.map { prefs ->
            UserSettings(
                reminderEnabled = prefs[Keys.REMINDER_ENABLED] ?: false,
                reminderHour = prefs[Keys.REMINDER_HOUR] ?: 22,
                reminderMinute = prefs[Keys.REMINDER_MINUTE] ?: 0,
                dailyScreenTimeGoalMinutes = prefs[Keys.DAILY_GOAL] ?: 240
            )
        }
    }

    suspend fun updateSettings(settings: UserSettings) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.REMINDER_ENABLED] = settings.reminderEnabled
            prefs[Keys.REMINDER_HOUR] = settings.reminderHour
            prefs[Keys.REMINDER_MINUTE] = settings.reminderMinute
            prefs[Keys.DAILY_GOAL] = settings.dailyScreenTimeGoalMinutes
        }
    }
}
