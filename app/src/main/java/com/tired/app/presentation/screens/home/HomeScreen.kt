package com.tired.app.presentation.screens.home

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tired.app.presentation.components.CategoryBar
import com.tired.app.presentation.components.FatigueScoreRing
import com.tired.app.presentation.components.SuggestionCard
import com.tired.app.presentation.model.UsageScreenState
import com.tired.app.presentation.theme.Blush50
import com.tired.app.presentation.theme.CardWhite
import com.tired.app.presentation.theme.Coral500
import com.tired.app.presentation.theme.Ink500
import com.tired.app.data.remote.model.BackendAntiFatiguePlan
import com.tired.app.presentation.theme.Ink700
import com.tired.app.presentation.theme.Ink900
import com.tired.app.presentation.theme.Sand50
import com.tired.app.presentation.theme.Peach500
import com.tired.app.presentation.theme.Teal500
import com.tired.app.presentation.theme.categoryColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Tired?",
                        style = MaterialTheme.typography.displayLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    RefreshButton(onRefresh = viewModel::refresh)
                }
            )
        }
    ) { padding ->
        ScreenBackground {
            when (uiState.screenState) {
                UsageScreenState.LOADING -> LoadingStateContent(padding)
                UsageScreenState.PERMISSION_REQUIRED -> PermissionStateContent(
                    padding = padding,
                    error = uiState.error,
                    onOpenSettings = { openUsageAccessSettings(context) },
                    onRefresh = viewModel::refresh
                )

                UsageScreenState.EMPTY -> EmptyStateContent(
                    padding = padding,
                    error = uiState.error,
                    onRefresh = viewModel::refresh
                )

                UsageScreenState.CONTENT -> ContentStateContent(
                    padding = padding,
                    uiState = uiState,
                    onRefresh = viewModel::refresh
                )
            }
        }
    }
}

@Composable
private fun ScreenBackground(content: @Composable BoxScope.() -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .size(220.dp)
                .offset(x = (-64).dp, y = (-18).dp)
                .background(Peach500.copy(alpha = 0.11f), CircleShape)
        )
        Box(
            modifier = Modifier
                .size(180.dp)
                .offset(x = 280.dp, y = 120.dp)
                .background(Teal500.copy(alpha = 0.10f), CircleShape)
        )
        content()
    }
}

@Composable
private fun RefreshButton(onRefresh: () -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        IconButton(onClick = onRefresh) {
            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
        }
    }
}

@Composable
private fun LoadingStateContent(padding: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = CardWhite),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                androidx.compose.material3.CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(14.dp))
                Text("Analyzing your device activity...")
            }
        }
    }
}

@Composable
private fun PermissionStateContent(
    padding: PaddingValues,
    error: String?,
    onOpenSettings: () -> Unit,
    onRefresh: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        error?.let {
            InlineErrorCard(error = it)
            Spacer(modifier = Modifier.height(16.dp))
        }
        HeroStateCard(
            title = "Usage Access is required",
            message = "Enable Usage Access so Tired can read screen time, app launches, and your seven-day pattern.",
            actionLabel = "Open Usage Access",
            onAction = onOpenSettings
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            onClick = onRefresh,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("I enabled it. Check again")
        }
    }
}

@Composable
private fun EmptyStateContent(
    padding: PaddingValues,
    error: String?,
    onRefresh: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        error?.let {
            InlineErrorCard(error = it)
            Spacer(modifier = Modifier.height(16.dp))
        }
        HeroStateCard(
            title = "Not enough activity yet",
            message = "Usage Access is on, but there is not enough real UsageStats data for today yet. Try again a little later."
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            onClick = onRefresh,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Refresh")
        }
    }
}

@Composable
private fun ContentStateContent(
    padding: PaddingValues,
    uiState: HomeUiState,
    onRefresh: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        uiState.error?.let { error ->
            InlineErrorCard(error = error)
            Spacer(modifier = Modifier.height(16.dp))
        }

        ScoreHeroCard(score = uiState.fatigueScore)

        Spacer(modifier = Modifier.height(18.dp))

        FatigueBanner(
            title = uiState.fatigueType.displayName,
            description = uiState.fatigueType.description,
            emoji = uiState.fatigueType.emoji
        )

        Spacer(modifier = Modifier.height(18.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val hours = uiState.totalScreenTimeMinutes / 60
            val mins = uiState.totalScreenTimeMinutes % 60
            HomeMetricCard(
                modifier = Modifier.weight(1f),
                title = "Screen Time",
                value = if (hours > 0) "${hours}h ${mins}m" else "${mins}m",
                emoji = "◔"
            )
            HomeMetricCard(
                modifier = Modifier.weight(1f),
                title = "Launches",
                value = "${uiState.totalLaunchCount}",
                emoji = "↯"
            )
        }

        Spacer(modifier = Modifier.height(18.dp))
        ActivePlanSection(plan = uiState.activePlan)

        if (uiState.summary.isNotEmpty()) {
            Spacer(modifier = Modifier.height(18.dp))
            AnalysisHintCard(message = uiState.analysisRefreshHint)
            Spacer(modifier = Modifier.height(12.dp))
            HeroStateCard(
                title = "Today's Summary",
                message = uiState.summary,
                tinted = false
            )
        }

        if (uiState.suggestions.isNotEmpty()) {
            Spacer(modifier = Modifier.height(18.dp))
            SuggestionCard(suggestions = uiState.suggestions)
        }

        if (uiState.topApps.isNotEmpty()) {
            Spacer(modifier = Modifier.height(18.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = CardWhite),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(22.dp)) {
                    Text(
                        text = "Top Apps Today",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    uiState.topApps.forEach { app ->
                        CategoryBar(
                            categoryName = app.appName,
                            emoji = app.category.emoji,
                            minutes = app.totalTimeMinutes,
                            totalMinutes = uiState.totalScreenTimeMinutes,
                            color = categoryColor(app.category.ordinal)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))
        OutlinedButton(
            onClick = onRefresh,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Refresh Real Data")
        }
        Spacer(modifier = Modifier.height(28.dp))
    }
}

@Composable
private fun AnalysisHintCard(message: String) {
    if (message.isBlank()) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            style = MaterialTheme.typography.bodySmall,
            color = Ink500
        )
    }
}

@Composable
private fun ScoreHeroCard(score: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = Blush50),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 18.dp),
            contentAlignment = Alignment.Center
        ) {
            FatigueScoreRing(score = score, size = 210.dp)
        }
    }
}

@Composable
private fun FatigueBanner(
    title: String,
    description: String,
    emoji: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Coral500),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = emoji, style = MaterialTheme.typography.headlineLarge)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.9f)
            )
        }
    }
}

@Composable
private fun HomeMetricCard(
    title: String,
    value: String,
    emoji: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 16.dp)) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(18.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(text = emoji, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = Ink500
            )
        }
    }
}

@Composable
private fun HeroStateCard(
    title: String,
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    tinted: Boolean = true
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (tinted) CardWhite else CardWhite
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(22.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = Ink500
            )
            if (actionLabel != null && onAction != null) {
                Spacer(modifier = Modifier.height(16.dp))
                androidx.compose.material3.Button(onClick = onAction) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
private fun InlineErrorCard(error: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Text(
            text = error,
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.onErrorContainer
        )
    }
}

private fun openUsageAccessSettings(context: Context) {
    val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

@Composable
private fun ActivePlanSection(plan: BackendAntiFatiguePlan?) {
    if (plan != null) {
        val actionDesc = when (plan.recoveryActionType) {
            "WHITE_NOISE_RAIN" -> "🌧️ 雨声白噪音"
            "BREATHING_EXERCISE" -> "🌬️ 4-7-8 腹式呼吸"
            "MICRO_STRETCH" -> "🧘 微动作闭目伸展"
            "SCREEN_OFF_REST" -> "📱 手机倒扣闭目休息"
            else -> plan.recoveryActionType
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = CardWhite),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .background(Teal500.copy(alpha = 0.14f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "🛡️ 当前防疲劳策略",
                            style = MaterialTheme.typography.labelSmall,
                            color = Teal500,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Box(
                        modifier = Modifier
                            .background(Peach500.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "✓ 已同步端侧",
                            style = MaterialTheme.typography.labelSmall,
                            color = Peach500,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = plan.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Ink900
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("重点监控时段", style = MaterialTheme.typography.labelSmall, color = Ink500)
                        Text(plan.targetTimeWindow, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = Ink700)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("连续使用阈值", style = MaterialTheme.typography.labelSmall, color = Ink500)
                        Text("每 ${plan.maxContinuousMinutes} 分钟", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = Ink700)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Sand50, RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Column {
                        Text(
                            text = "温和提醒语：",
                            style = MaterialTheme.typography.labelSmall,
                            color = Ink500
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "\"${plan.promptMessage}\"",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Ink900,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "软着陆动作：$actionDesc (${plan.actionDurationMinutes}分钟)",
                            style = MaterialTheme.typography.labelSmall,
                            color = Teal500,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                if (plan.microTips.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "落地贴士：${plan.microTips.joinToString(" • ")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Ink500
                    )
                }
            }
        }
    } else {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = CardWhite),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Row(
                modifier = Modifier.padding(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "💡", style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "定制专属防疲劳方案",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Ink900
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "前往 AI 对话告知你的作息与高危时段，AI 将为你生成端侧守护方案。",
                        style = MaterialTheme.typography.bodySmall,
                        color = Ink500
                    )
                }
            }
        }
    }
}
