package com.tired.app.data.repository

import com.tired.app.data.local.datasource.SettingsDataSource
import com.tired.app.domain.model.UserSettings
import com.tired.app.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val settingsDataSource: SettingsDataSource
) : SettingsRepository {

    override fun observeSettings(): Flow<UserSettings> {
        return settingsDataSource.observeSettings()
    }

    override suspend fun getSettings(): UserSettings {
        return settingsDataSource.observeSettings().first()
    }

    override suspend fun updateSettings(settings: UserSettings) {
        settingsDataSource.updateSettings(settings)
    }
}
