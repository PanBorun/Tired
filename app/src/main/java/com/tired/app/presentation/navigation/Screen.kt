package com.tired.app.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(
    val route: String,
    val title: String,
    val navTitle: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    data object Home : Screen("home", "Home", "Home", Icons.Filled.Home, Icons.Outlined.Home)
    data object Analysis : Screen("analysis", "Analysis", "Stats", Icons.Filled.Analytics, Icons.Outlined.Analytics)
    data object Trends : Screen("trends", "Trends", "Trend", Icons.Filled.TrendingUp, Icons.Outlined.TrendingUp)
    data object Assistant : Screen("assistant", "AI Chat", "AI", Icons.Filled.SmartToy, Icons.Outlined.SmartToy)
    data object Settings : Screen("settings", "Settings", "Set", Icons.Filled.Settings, Icons.Outlined.Settings)

    companion object {
        val bottomBarItems = listOf(Home, Analysis, Trends, Assistant, Settings)
    }
}
