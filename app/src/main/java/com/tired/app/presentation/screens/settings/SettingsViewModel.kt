package com.tired.app.presentation.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tired.app.domain.model.UserDigitalProfile
import com.tired.app.domain.model.UserSettings
import com.tired.app.domain.repository.ChatRepository
import com.tired.app.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val chatRepository: ChatRepository
) : ViewModel() {

    private val _settings = MutableStateFlow(UserSettings())
    val settings: StateFlow<UserSettings> = _settings.asStateFlow()

    private val _userProfile = MutableStateFlow(UserDigitalProfile())
    val userProfile: StateFlow<UserDigitalProfile> = _userProfile.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.observeSettings().collect { settings ->
                _settings.value = settings
            }
        }
        viewModelScope.launch {
            chatRepository.observeUserProfile().collect { profile ->
                _userProfile.value = profile
            }
        }
    }

    fun updateReminder(enabled: Boolean, hour: Int = _settings.value.reminderHour, minute: Int = _settings.value.reminderMinute) {
        viewModelScope.launch {
            val updated = _settings.value.copy(
                reminderEnabled = enabled,
                reminderHour = hour,
                reminderMinute = minute
            )
            settingsRepository.updateSettings(updated)
        }
    }

    fun updateDailyGoal(minutes: Int) {
        viewModelScope.launch {
            val updated = _settings.value.copy(dailyScreenTimeGoalMinutes = minutes)
            settingsRepository.updateSettings(updated)
        }
    }

    fun updateUserProfile(profile: UserDigitalProfile) {
        viewModelScope.launch {
            chatRepository.updateUserProfile(profile)
        }
    }
}
