package com.tired.app.presentation.screens.analysis

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.tired.app.presentation.components.SimpleBarChart
import com.tired.app.presentation.components.SimplePieChart
import com.tired.app.presentation.model.UsageScreenState
import com.tired.app.presentation.theme.CardWhite
import com.tired.app.presentation.theme.Gold500
import com.tired.app.presentation.theme.Ink500
import com.tired.app.presentation.theme.Ink700
import com.tired.app.presentation.theme.Peach500
import com.tired.app.presentation.theme.Teal500
import com.tired.app.presentation.theme.categoryColor
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisScreen(
    viewModel: AnalysisViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val today = LocalDate.now()
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

    var selectedDayOffset by remember { mutableIntStateOf(0) }
    val dateOptions = (0 downTo -6).map { offset ->
        val date = today.plusDays(offset.toLong())
        date.format(DateTimeFormatter.ISO_LOCAL_DATE) to when (offset) {
            0 -> "Today"
            -1 -> "Yesterday"
            else -> date.format(DateTimeFormatter.ofPattern("MMM d"))
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Analysis",
                        style = MaterialTheme.typography.displayLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    AnalysisRefreshButton(onRefresh = viewModel::refresh)
                }
            )
        }
    ) { padding ->
        AnalysisBackground {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
            ) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    dateOptions.forEachIndexed { index, (date, label) ->
                        DayPill(
                            label = label,
                            selected = index == selectedDayOffset,
                            onClick = {
                                selectedDayOffset = index
                                viewModel.selectDate(date)
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                when (uiState.screenState) {
                    UsageScreenState.LOADING -> {
                        AnalysisStateCard(
                            title = "Preparing analysis",
                            message = "Reading device activity and building your day map."
                        )
                    }

                    UsageScreenState.PERMISSION_REQUIRED -> {
                        AnalysisStateCard(
                            title = "Analysis needs Usage Access",
                            message = "Turn on Usage Access to unlock category split, hourly pattern, and top app details.",
                            actionLabel = "Open Usage Access",
                            onAction = { openUsageAccessSettings(context) }
                        )
                    }

                    UsageScreenState.EMPTY -> {
                        AnalysisStateCard(
                            title = "Not enough data yet",
                            message = "This date does not have enough real UsageStats data yet. Spend a little more time in apps, then check again."
                        )
                    }

                    UsageScreenState.CONTENT -> {
                        uiState.error?.let {
                            AnalysisInlineErrorCard(it)
                            Spacer(modifier = Modifier.height(14.dp))
                        }
                        AnalysisHeroCard(
                            totalMinutes = uiState.totalScreenTimeMinutes,
                            peakHour = uiState.peakHour,
                            appCount = uiState.appUsageList.size
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        AnalysisSectionCard(title = "Category Split") {
                            if (uiState.categoryBreakdown.isNotEmpty()) {
                                SimplePieChart(data = uiState.categoryBreakdown)
                                Spacer(modifier = Modifier.height(16.dp))
                                uiState.categoryBreakdown
                                    .toList()
                                    .sortedByDescending { it.second }
                                    .take(5)
                                    .forEach { (category, minutes) ->
                                        CategoryBar(
                                            categoryName = category.displayName,
                                            emoji = category.emoji,
                                            minutes = minutes,
                                            totalMinutes = uiState.totalScreenTimeMinutes.coerceAtLeast(1),
                                            color = categoryColor(category.ordinal)
                                        )
                                    }
                            } else {
                                SectionPlaceholder("No category data available yet.")
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        AnalysisSectionCard(title = "Hourly Pattern") {
                            if (uiState.hourlyDistribution.any { it.value > 0 }) {
                                SimpleBarChart(
                                    data = uiState.hourlyDistribution,
                                    barColor = Gold500
                                )
                            } else {
                                SectionPlaceholder("No hourly distribution available yet.")
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        AnalysisSectionCard(title = "Top Apps") {
                            if (uiState.appUsageList.isNotEmpty()) {
                                uiState.appUsageList.take(8).forEach { app ->
                                    CategoryBar(
                                        categoryName = app.appName,
                                        emoji = app.category.emoji,
                                        minutes = app.totalTimeMinutes,
                                        totalMinutes = uiState.totalScreenTimeMinutes.coerceAtLeast(1),
                                        color = categoryColor(app.category.ordinal)
                                    )
                                }
                            } else {
                                SectionPlaceholder("No app-level detail available yet.")
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))
            }
        }
    }
}

@Composable
private fun AnalysisBackground(content: @Composable BoxScope.() -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .size(210.dp)
                .offset(x = (-68).dp, y = (-22).dp)
                .background(Peach500.copy(alpha = 0.11f), CircleShape)
        )
        Box(
            modifier = Modifier
                .size(170.dp)
                .offset(x = 280.dp, y = 180.dp)
                .background(Teal500.copy(alpha = 0.09f), CircleShape)
        )
        content()
    }
}

@Composable
private fun AnalysisRefreshButton(onRefresh: () -> Unit) {
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
private fun DayPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary else CardWhite
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 11.dp),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = if (selected) Color.White else Ink700
        )
    }
}

@Composable
private fun AnalysisHeroCard(
    totalMinutes: Long,
    peakHour: Int,
    appCount: Int
) {
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = Ink700),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = "Day Snapshot",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.72f)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m",
                style = MaterialTheme.typography.displayLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Peak activity around ${peakHour}:00 across $appCount tracked apps.",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.88f)
            )
            Spacer(modifier = Modifier.height(18.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HeroBadge(label = "Peak", value = "${peakHour}:00")
                HeroBadge(label = "Apps", value = "$appCount")
            }
        }
    }
}

@Composable
private fun HeroBadge(
    label: String,
    value: String
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.10f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.64f)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun AnalysisSectionCard(
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
private fun AnalysisStateCard(
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
private fun SectionPlaceholder(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = Ink500
    )
}

@Composable
private fun AnalysisInlineErrorCard(error: String) {
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
