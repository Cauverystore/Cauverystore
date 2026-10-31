package com.cauverystore.android.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Brand colours from the website's design system (frontend/src/styles/design-system.css).
val Teal = Color(0xFF0E5C5C)
val TealLight = Color(0xFFE3F0EF)
val Saving = Color(0xFF1B7F3B)

private val Light = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    primaryContainer = TealLight,
    onPrimaryContainer = Color(0xFF073636),
    secondary = Color(0xFF3E6A69),
    background = Color(0xFFFAFAF8),
    surface = Color.White,
    surfaceVariant = Color(0xFFF1F3F2),
    error = Color(0xFFB3261E),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF7FC8C6),
    onPrimary = Color(0xFF003736),
    primaryContainer = Color(0xFF0E4A4A),
    onPrimaryContainer = Color(0xFFCDEDEB),
    secondary = Color(0xFFA6CFCD),
)

@Composable
fun CauveryTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
