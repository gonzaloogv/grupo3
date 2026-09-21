package com.grupo3.freno.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Danger = Color(0xFFB42318)
val OnDanger = Color(0xFFFFFFFF)
val DangerSoft = Color(0xFFFFF1F0)
val SafetyBlue = Color(0xFF1D4ED8)
val Ink = Color(0xFF162033)
val InkMuted = Color(0xFF526176)
val Surface = Color(0xFFFFFFFF)
val Canvas = Color(0xFFF6F8FB)
val Border = Color(0xFFD9E0EA)
val Safe = Color(0xFF16794A)
val Focus = Color(0xFFF59E0B)

private val FrenoColors = lightColorScheme(
    primary = SafetyBlue,
    onPrimary = Color.White,
    secondary = Safe,
    error = Danger,
    onError = OnDanger,
    errorContainer = DangerSoft,
    background = Canvas,
    onBackground = Ink,
    surface = Surface,
    onSurface = Ink,
    outline = Border,
)

private val FrenoTypography = Typography(
    displayMedium = TextStyle(fontSize = 40.sp, lineHeight = 48.sp, fontWeight = FontWeight.Bold),
    headlineLarge = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold),
    bodyLarge = TextStyle(fontSize = 20.sp, lineHeight = 30.sp),
    bodyMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    labelLarge = TextStyle(fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold),
    labelMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold),
)

@Composable
fun FrenoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = FrenoColors,
        typography = FrenoTypography,
        content = content,
    )
}
