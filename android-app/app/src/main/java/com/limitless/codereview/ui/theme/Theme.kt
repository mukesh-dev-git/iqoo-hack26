package com.limitless.codereview.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AppDarkColorScheme = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF06110D),
    secondary = Brass,
    onSecondary = Color(0xFF2A1C04),
    background = AppBg,
    onBackground = TextHi,
    surface = Surface,
    onSurface = TextHi,
    surfaceVariant = Surface2,
    onSurfaceVariant = TextLo,
    error = SevBug,
    onError = Color(0xFF2A0D0A),
    errorContainer = SevBugSoft,
    onErrorContainer = SevBug,
    outline = FrameEdge,
)

@Composable
fun CodeReviewTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppDarkColorScheme,
        content = content,
    )
}
