package com.tired.app.presentation.theme

import androidx.compose.ui.graphics.Color

val Ink900 = Color(0xFF18202C)
val Ink700 = Color(0xFF3D4552)
val Ink500 = Color(0xFF5E6470)
val Ink300 = Color(0xFF9097A5)

val Sand50 = Color(0xFFF4F1EA)
val Sand100 = Color(0xFFECE7E0)
val CardWhite = Color(0xFFFFFFFF)
val Blush50 = Color(0xFFFFF7F2)

val Coral500 = Color(0xFFD95B64)
val Peach500 = Color(0xFFE47A5A)
val Teal500 = Color(0xFF5A9F97)
val Gold500 = Color(0xFFE7B86A)
val Lilac500 = Color(0xFF8C7BC4)

val FatigueGreen = Color(0xFF4CAF50)
val FatigueYellow = Color(0xFFFFC107)
val FatigueOrange = Color(0xFFFF9800)
val FatigueRed = Color(0xFFF44336)
val FatigueDarkRed = Color(0xFFB71C1C)

val CategorySocial = Color(0xFF2196F3)
val CategoryVideo = Color(0xFFE91E63)
val CategoryGame = Color(0xFF9C27B0)
val CategoryNews = Color(0xFFFF9800)
val CategoryShopping = Color(0xFFFF5722)
val CategoryWork = Color(0xFF4CAF50)
val CategoryTool = Color(0xFF607D8B)
val CategoryHealth = Color(0xFF00BCD4)
val CategoryMusic = Color(0xFF8BC34A)
val CategoryOther = Color(0xFF9E9E9E)

val DarkBackground = Color(0xFF121212)
val DarkSurface = Color(0xFF1E1E1E)
val DarkCard = Color(0xFF2C2C2C)

fun fatigueScoreColor(score: Int): Color {
    return when {
        score >= 80 -> FatigueDarkRed
        score >= 60 -> FatigueRed
        score >= 40 -> FatigueOrange
        score >= 20 -> FatigueYellow
        else -> FatigueGreen
    }
}

fun categoryColor(ordinal: Int): Color {
    val colors = listOf(
        CategorySocial, CategoryVideo, CategoryGame, CategoryNews,
        CategoryShopping, CategoryWork, CategoryTool, CategoryHealth,
        CategoryMusic, CategoryOther
    )
    return colors.getOrElse(ordinal) { CategoryOther }
}
