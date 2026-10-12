package com.tired.app.presentation.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tired.app.presentation.theme.CardWhite
import com.tired.app.presentation.theme.Ink500
import com.tired.app.presentation.theme.Ink700
import com.tired.app.presentation.theme.Peach500
import com.tired.app.presentation.theme.Teal500

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    var showEditProfileDialog by remember { mutableStateOf(false) }

    if (showEditProfileDialog) {
        EditProfileDialog(
            initialProfile = userProfile,
            onDismiss = { showEditProfileDialog = false },
            onSave = { updated ->
                viewModel.updateUserProfile(updated)
                showEditProfileDialog = false
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.displayLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            )
        }
    ) { padding ->
        SettingsBackground {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                SettingsHeroCard()

                Spacer(modifier = Modifier.height(18.dp))

                SettingsSectionCard(
                    icon = Icons.Default.SmartToy,
                    title = "AI Memory Profile"
                ) {
                    Text(
                        text = "长期画像记忆 (Long-term Memory)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Ink700
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "AI 会牢记这些核心特征，并在每次对话与建议中量身定制，拒绝大话套话。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Ink500
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    ProfileItemRow("⚠️ 高危时段", userProfile.dangerTimeRange)
                    Spacer(modifier = Modifier.height(8.dp))
                    ProfileItemRow("📱 易耗竭习惯", userProfile.triggerAppHabits)
                    Spacer(modifier = Modifier.height(8.dp))
                    ProfileItemRow("🎧 回血偏好", userProfile.reliefPreferences)
                    Spacer(modifier = Modifier.height(8.dp))
                    ProfileItemRow("💼 身份与场景", userProfile.contextBio)

                    Spacer(modifier = Modifier.height(16.dp))
                    androidx.compose.material3.OutlinedButton(
                        onClick = { showEditProfileDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("编辑我的数字健康画像")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                SettingsSectionCard(
                    icon = Icons.Default.Notifications,
                    title = "Reminders"
                ) {
                    SettingsToggleRow(
                        title = "Bedtime reminder",
                        subtitle = "A daily nudge at ${settings.reminderHour}:${String.format("%02d", settings.reminderMinute)} to start putting the phone down.",
                        checked = settings.reminderEnabled,
                        onCheckedChange = { viewModel.updateReminder(it) }
                    )

                    if (settings.reminderEnabled) {
                        Spacer(modifier = Modifier.height(14.dp))
                        var sliderValue by remember(settings.reminderHour) {
                            mutableFloatStateOf(settings.reminderHour.toFloat())
                        }
                        Text(
                            text = "Reminder time: ${sliderValue.toInt()}:00",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Ink700
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Slider(
                            value = sliderValue,
                            onValueChange = { sliderValue = it },
                            onValueChangeFinished = {
                                viewModel.updateReminder(true, sliderValue.toInt(), 0)
                            },
                            valueRange = 20f..24f,
                            steps = 3
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                SettingsSectionCard(
                    icon = Icons.Default.Flag,
                    title = "Daily Goal"
                ) {
                    var goalSlider by remember(settings.dailyScreenTimeGoalMinutes) {
                        mutableFloatStateOf(settings.dailyScreenTimeGoalMinutes.toFloat())
                    }
                    val goalHours = goalSlider.toInt() / 60
                    val goalMins = goalSlider.toInt() % 60
                    Text(
                        text = "Target screen time: ${goalHours}h ${goalMins}m",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Set a realistic ceiling for the day so the dashboard can stay honest about your pace.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Ink500
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Slider(
                        value = goalSlider,
                        onValueChange = { goalSlider = it },
                        onValueChangeFinished = {
                            viewModel.updateDailyGoal(goalSlider.toInt())
                        },
                        valueRange = 60f..480f,
                        steps = 13
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                SettingsSectionCard(
                    icon = Icons.Default.Info,
                    title = "About"
                ) {
                    Text(
                        text = "Tired? v1.0.0",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "A fatigue-tracking and energy-management app built around real phone behavior.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Ink500
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Powered by UsageStats behavior analysis and an integrated Qwen AI assistant.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Ink500
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))
            }
        }
    }
}

@Composable
private fun SettingsBackground(content: @Composable BoxScope.() -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .size(210.dp)
                .offset(x = (-60).dp, y = 40.dp)
                .background(Peach500.copy(alpha = 0.10f), CircleShape)
        )
        Box(
            modifier = Modifier
                .size(180.dp)
                .offset(x = 290.dp, y = 280.dp)
                .background(Teal500.copy(alpha = 0.08f), CircleShape)
        )
        content()
    }
}

@Composable
private fun SettingsHeroCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = Ink700),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.12f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Box(
                        modifier = Modifier.padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.SmartToy,
                            contentDescription = null,
                            tint = Color.White
                        )
                    }
                }
                Spacer(modifier = Modifier.height(0.dp))
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Qwen AI is built in",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Open the AI Chat tab anytime. It can use today’s device activity automatically, so there’s no API key setup left to do.",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.88f)
            )
        }
    }
}

@Composable
private fun SettingsSectionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Peach500.copy(alpha = 0.12f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Box(
                        modifier = Modifier.padding(10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(modifier = Modifier.size(10.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Ink500
            )
        }
        Spacer(modifier = Modifier.size(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun ProfileItemRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = Ink700
        )
    }
}

@Composable
private fun EditProfileDialog(
    initialProfile: com.tired.app.domain.model.UserDigitalProfile,
    onDismiss: () -> Unit,
    onSave: (com.tired.app.domain.model.UserDigitalProfile) -> Unit
) {
    var dangerTime by remember { mutableStateOf(initialProfile.dangerTimeRange) }
    var triggerHabits by remember { mutableStateOf(initialProfile.triggerAppHabits) }
    var reliefPref by remember { mutableStateOf(initialProfile.reliefPreferences) }
    var contextBio by remember { mutableStateOf(initialProfile.contextBio) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "编辑 AI 记忆画像",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                androidx.compose.material3.OutlinedTextField(
                    value = dangerTime,
                    onValueChange = { dangerTime = it },
                    label = { Text("易疲惫/高危时段") },
                    modifier = Modifier.fillMaxWidth()
                )
                androidx.compose.material3.OutlinedTextField(
                    value = triggerHabits,
                    onValueChange = { triggerHabits = it },
                    label = { Text("易耗竭习惯 (如短视频)") },
                    modifier = Modifier.fillMaxWidth()
                )
                androidx.compose.material3.OutlinedTextField(
                    value = reliefPref,
                    onValueChange = { reliefPref = it },
                    label = { Text("回血偏好 (如白噪音、拒绝说教)") },
                    modifier = Modifier.fillMaxWidth()
                )
                androidx.compose.material3.OutlinedTextField(
                    value = contextBio,
                    onValueChange = { contextBio = it },
                    label = { Text("生活场景 (如高脑力消耗)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.Button(
                onClick = {
                    onSave(
                        initialProfile.copy(
                            dangerTimeRange = dangerTime.trim(),
                            triggerAppHabits = triggerHabits.trim(),
                            reliefPreferences = reliefPref.trim(),
                            contextBio = contextBio.trim()
                        )
                    )
                }
            ) {
                Text("保存画像")
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
