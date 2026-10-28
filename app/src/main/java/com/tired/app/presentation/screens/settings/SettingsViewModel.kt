package com.tired.app.presentation.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tired.app.data.remote.model.BackendTraitItem
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

    private val _traits = MutableStateFlow<List<BackendTraitItem>>(emptyList())
    val traits: StateFlow<List<BackendTraitItem>> = _traits.asStateFlow()

    private val _isLoadingTraits = MutableStateFlow(false)
    val isLoadingTraits: StateFlow<Boolean> = _isLoadingTraits.asStateFlow()

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
        loadTraits()
    }

    fun loadTraits() {
        viewModelScope.launch {
            _isLoadingTraits.value = true
            chatRepository.getProfileTraits()
                .onSuccess { list ->
                    _traits.value = list
                }
                .onFailure {
                    // 若后端暂时无法连接，填充默认基准数据作为离线显示兜底
                    if (_traits.value.isEmpty()) {
                        _traits.value = listOf(
                            BackendTraitItem(1, "default_user", "WORK_SCHEDULE", "常规工作日早九晚六作息", "ACTIVE", "HIGH", "2026-10-28"),
                            BackendTraitItem(2, "default_user", "TRIGGER_HABIT", "睡前短视频、社交平台信息流无意识滑屏", "ACTIVE", "HIGH", "2026-10-28"),
                            BackendTraitItem(3, "default_user", "RELIEF_PREF", "偏好白噪音与轻柔雨声，反感大道理说教，注重温和同理心", "ACTIVE", "HIGH", "2026-10-28"),
                            BackendTraitItem(4, "default_user", "LIFE_CONTEXT", "白天高强度脑力消耗，晚上下班后容易通过手机报复性放松", "ACTIVE", "HIGH", "2026-10-28")
                        )
                    }
                }
            _isLoadingTraits.value = false
        }
    }

    fun addTrait(category: String, content: String, status: String = "ACTIVE") {
        viewModelScope.launch {
            chatRepository.addProfileTrait(category, content, status)
                .onSuccess {
                    loadTraits()
                }
        }
    }

    fun updateTrait(traitId: Int, category: String, content: String, status: String) {
        viewModelScope.launch {
            chatRepository.updateProfileTrait(traitId, category, content, status)
                .onSuccess {
                    loadTraits()
                }
        }
    }

    fun deleteTrait(traitId: Int) {
        viewModelScope.launch {
            chatRepository.deleteProfileTrait(traitId)
                .onSuccess {
                    loadTraits()
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
