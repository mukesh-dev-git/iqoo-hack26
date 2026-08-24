package com.limitless.codereview.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.limitless.codereview.R

// JetBrains Mono for anything code/technical (diffs, findings' line/severity tags, status
// lines, interface names) — the one deliberate typographic signal that this is a dev tool,
// not a generic app. Everything else uses the system default sans, which already reads clean.
val JetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
)
