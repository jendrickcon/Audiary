package com.example.audiary.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val AudiaryColors = darkColorScheme(
    primary = Amber, onPrimary = Ink, primaryContainer = PanelElevated, onPrimaryContainer = Cream,
    secondary = Amber, onSecondary = Ink, secondaryContainer = Panel, onSecondaryContainer = Cream,
    background = Ink, onBackground = Cream,
    surface = Ink, onSurface = Cream, surfaceVariant = Panel, onSurfaceVariant = Muted,
    surfaceContainer = Panel, surfaceContainerLow = Ink, surfaceContainerHigh = PanelElevated,
    outline = Line, outlineVariant = Line
)

private val AudiaryShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable fun AudiaryTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = AudiaryColors, typography = AudiaryTypography, shapes = AudiaryShapes, content = content)
}

object Space {
    val tiny = 4.dp
    val small = 8.dp
    val gap = 12.dp
    val medium = 16.dp
    val page = 20.dp
    val section = 28.dp
    val touch = 48.dp
}
