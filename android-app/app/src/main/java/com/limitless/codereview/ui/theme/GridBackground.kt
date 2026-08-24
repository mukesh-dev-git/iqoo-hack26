package com.limitless.codereview.ui.theme

import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The faint dot-grid backdrop used across every pitch artifact (dashboard mockup, walkthrough,
 * architecture diagram, 30-hour plan) — replicated natively here so the real app matches
 * instead of only the web mockups looking the part.
 */
fun Modifier.gridBackground(
    lineColor: Color = Color.White.copy(alpha = 0.045f),
    step: Dp = 28.dp,
): Modifier = this
    .background(AppBg)
    .drawBehind {
        val stepPx = step.toPx()
        var x = 0f
        while (x <= size.width) {
            drawLine(lineColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
            x += stepPx
        }
        var y = 0f
        while (y <= size.height) {
            drawLine(lineColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            y += stepPx
        }
    }
