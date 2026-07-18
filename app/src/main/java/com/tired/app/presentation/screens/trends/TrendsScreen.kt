package com.tired.app.presentation.screens.trends

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.getValue
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
import com.tired.app.presentation.components.SimpleLineChart
import com.tired.app.presentation.model.UsageScreenState
import com.tired.app.presentation.theme.CardWhite
import com.tired.app.presentation.theme.Ink500
import com.tired.app.presentation.theme.Ink700
import com.tired.app.presentation.theme.Peach500
import com.tired.app.presentation.theme.Teal500
import com.tired.app.presentation.theme.fatigueScoreColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrendsScreen(
    viewModel: TrendsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.loadTrends()
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
                        text = "Trends",
                        style = MaterialTheme.typography.displayLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    TrendsRefreshButton(onRefresh = viewModel::loadTrends)
                }
            )
        }
    ) { padding ->
        TrendsBackground {
            when (uiState.screenState) {
                UsageScreenState.LOADING -> {
                    TrendsStateContainer(padding) {
                        TrendsStateCard(
                            title = "Loading trends",
                            message = "Gathering the last seven days of activity."
                        )
                    }
                }

                UsageScreenState.PERMISSION_REQUIRED -> {
                    TrendsStateContainer(padding) {
                        TrendsStateCard(
                            title = "Weekly trends need Usage Access",
                            message = "Enable Usage Access to read your seven-day behavior pattern and compare fatigue over time.",
                            actionLabel = "Open Usage Access",
                            onAction = { openUsageAccessSettings(context) }
                        )
                    }
                }

                UsageScreenState.EMPTY -> {
                    TrendsStateContainer(padding) {
                        TrendsStateCard(
                            title = "No trend data yet",
                            message = "There is not enough real UsageStats data over the last week yet. Come back after more device activity."
                        )
                    }
                }

                UsageScreenState.CONTENT -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp)
                    ) {
                        Spacer(modifier = Modifier.height(8.dp))
                        uiState.error?.let {
                            TrendsInlineErrorCard(it)
                            Spacer(modifier = Modifier.height(14.dp))
                        }
                        WeeklyHeroCard(
                            emoji = uiState.trendEmoji,
                            averageScore = uiState.averageScore,
                            message = uiState.trend
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        TrendsSectionCard(title = "Fatigue Trend") {
                            if (uiState.weeklyScores.isNotEmpty()) {
                                SimpleLineChart(data = uiState.weeklyScores)
                            } else {
                                TrendsPlaceholder("No fatigue score trend available yet.")
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        TrendsSectionCard(title = "Daily Screen Time") {
                            if (uiState.weeklyScreenTime.isNotEmpty()) {
                                SimpleLineChart(
                                    data = uiState.weeklyScreenTime.map { (date, min) -> date to min.toInt() },
                                    lineColor = Peach500
                                )
                            } else {
                                TrendsPlaceholder("No daily screen time trend available yet.")
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        TrendsSectionCard(title = "Daily Snapshot") {
                            uiState.weeklyScores.reversed().forEach { (date, score) ->
                                val screenTime = uiState.weeklyScreenTime.find { it.first == date }?.second ?: 0
                                TrendRow(
                                    label = date.takeLast(5),
                                    screenTime = "${screenTime / 60}h ${screenTime % 60}m",
                                    score = score
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(28.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun TrendsBackground(content: @Composable BoxScope.() -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .size(210.dp)
                .offset(x = 250.dp, y = 120.dp)
                .background(Teal500.copy(alpha = 0.10f), CircleShape)
        )
        Box(
            modifier = Modifier
                .size(180.dp)
                .offset(x = (-60).dp, y = 300.dp)
                .background(Peach500.copy(alpha = 0.08f), CircleShape)
        )
        content()
    }
}

@Composable
private fun TrendsRefreshButton(onRefresh: () -> Unit) {
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
private fun WeeklyHeroCard(
    emoji: String,
    averageScore: Int,
    message: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = Ink700),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = emoji,
                style = MaterialTheme.typography.displayLarge
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Weekly average",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.72f)
            )
            Text(
                text = "$averageScore",
                style = MaterialTheme.typography.displayLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.90f)
            )
        }
    }
}

@Composable
private fun TrendsSectionCard(
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
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
private fun TrendRow(
    label: String,
    screenTime: String,
    score: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = Ink500
        )
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                text = screenTime,
                style = MaterialTheme.typography.bodyMedium,
                color = Ink500
            )
            Text(
                text = "Score $score",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = fatigueScoreColor(score)
            )
        }
    }
}

@Composable
private fun TrendsStateContainer(
    padding: PaddingValues,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun TrendsStateCard(
    title: String,
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = Ink500
            )
            if (actionLabel != null && onAction != null) {
                Spacer(modifier = Modifier.height(18.dp))
                OutlinedButton(onClick = onAction) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
private fun TrendsPlaceholder(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = Ink500
    )
}

@Composable
private fun TrendsInlineErrorCard(error: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
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
