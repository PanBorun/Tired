package com.tired.app.presentation.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.tired.app.data.remote.model.BackendTraitItem
import com.tired.app.presentation.theme.CardWhite
import com.tired.app.presentation.theme.FatigueOrange
import com.tired.app.presentation.theme.Ink300
import com.tired.app.presentation.theme.Ink500
import com.tired.app.presentation.theme.Ink700
import com.tired.app.presentation.theme.Ink900
import com.tired.app.presentation.theme.Peach500
import com.tired.app.presentation.theme.Sand50
import com.tired.app.presentation.theme.Teal500

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val traits by viewModel.traits.collectAsStateWithLifecycle()
    val isLoadingTraits by viewModel.isLoadingTraits.collectAsStateWithLifecycle()
    var showAddTraitDialog by remember { mutableStateOf(false) }

    if (showAddTraitDialog) {
        AddTraitDialog(
            onDismiss = { showAddTraitDialog = false },
            onConfirm = { category, content, status ->
                viewModel.addTrait(category, content, status)
                showAddTraitDialog = false
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

                // 阶段 2：自演化长期画像记忆卡片
                SettingsSectionCard(
                    icon = Icons.Default.SmartToy,
                    title = "AI Memory Profile"
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "自演化长期画像记忆",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Ink700
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "AI 在日常对话中自动提炼感知与求证（如换夜班），并以此作为定制策略的核心依据。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Ink500
                            )
                        }
                        IconButton(onClick = { viewModel.loadTraits() }) {
                            if (isLoadingTraits) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = "刷新记忆", tint = Ink500)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (traits.isEmpty()) {
                        Text(
                            text = "暂无画像特征记录。与 AI 正常对话，它会自动发现并记录你的习惯与生活动态。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Ink300,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    } else {
                        traits.forEach { trait ->
                            TraitCardItem(
                                trait = trait,
                                onDelete = { viewModel.deleteTrait(trait.id) }
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = { showAddTraitDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("手动记录或修正画像特征")
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
                        text = "Tired? v2.1.0",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "A fatigue-tracking and energy-management app built around real phone behavior and Tool-Calling Agent.",
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
private fun TraitCardItem(
    trait: BackendTraitItem,
    onDelete: () -> Unit
) {
    val categoryName = when (trait.category) {
        "WORK_SCHEDULE" -> "作息节奏 ⏰"
        "TRIGGER_HABIT" -> "失控诱因 📱"
        "SYMPTOM" -> "身心反应 ⚡"
        "RELIEF_PREF" -> "安抚偏好 🎧"
        "LIFE_CONTEXT" -> "生活背景 💼"
        else -> trait.category
    }

    val isPending = trait.status == "PENDING_CONFIRM"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Sand50),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 类别标签
                    Box(
                        modifier = Modifier
                            .background(Peach500.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = categoryName,
                            style = MaterialTheme.typography.labelSmall,
                            color = Peach500,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // 生命周期状态标签 (PENDING_CONFIRM vs ACTIVE)
                    if (isPending) {
                        Box(
                            modifier = Modifier
                                .background(FatigueOrange.copy(alpha = 0.16f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "⚠️ 待跟进求证",
                                style = MaterialTheme.typography.labelSmall,
                                color = FatigueOrange,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .background(Teal500.copy(alpha = 0.16f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "✓ 已生效基准",
                                style = MaterialTheme.typography.labelSmall,
                                color = Teal500,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "删除特征",
                        tint = Ink300,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = trait.content,
                style = MaterialTheme.typography.bodyMedium,
                color = Ink900,
                fontWeight = FontWeight.Medium
            )

            if (trait.sourceDate.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "记录日期: ${trait.sourceDate}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Ink500
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun AddTraitDialog(
    onDismiss: () -> Unit,
    onConfirm: (category: String, content: String, status: String) -> Unit
) {
    val categories = listOf(
        "WORK_SCHEDULE" to "作息节奏",
        "TRIGGER_HABIT" to "失控诱因",
        "RELIEF_PREF" to "安抚偏好",
        "SYMPTOM" to "身心反应",
        "LIFE_CONTEXT" to "生活背景"
    )

    var selectedCategory by remember { mutableStateOf("WORK_SCHEDULE") }
    var content by remember { mutableStateOf("") }
    var isPending by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "新增/修正画像特征",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "选择特征分类：",
                    style = MaterialTheme.typography.labelMedium,
                    color = Ink700
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categories.forEach { (catKey, catLabel) ->
                        FilterChip(
                            selected = selectedCategory == catKey,
                            onClick = { selectedCategory = catKey },
                            label = { Text(catLabel) }
                        )
                    }
                }

                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("特征具体内容 (如：新换了工作需要倒夜班)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "标记为近期待跟进求证",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "开启后，AI 下次制定方案前会先向你主动确认该状态是否还在持续",
                            style = MaterialTheme.typography.labelSmall,
                            color = Ink500
                        )
                    }
                    Switch(
                        checked = isPending,
                        onCheckedChange = { isPending = it }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (content.isNotBlank()) {
                        val status = if (isPending) "PENDING_CONFIRM" else "ACTIVE"
                        onConfirm(selectedCategory, content.trim(), status)
                    }
                },
                enabled = content.isNotBlank()
            ) {
                Text("保存至后端记忆")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
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
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Tool-Calling Agent Active",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "The AI now autonomously queries your hardware screen stats and evolves its long-term memory of your habits & lifestyle.",
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
