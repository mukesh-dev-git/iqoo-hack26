package com.limitless.codereview.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.limitless.codereview.engine.Finding
import com.limitless.codereview.engine.LaptopBridgeReviewEngine
import com.limitless.codereview.engine.ReviewEngine
import com.limitless.codereview.engine.Severity
import com.limitless.codereview.engine.StubReviewEngine
import com.limitless.codereview.sample.SampleDiffs
import com.limitless.codereview.ui.theme.*
import com.limitless.codereview.voice.VoiceTrigger
import kotlinx.coroutines.launch

/**
 * The app's main screen — the "dashboard". Paste (or voice-load) a diff, hit Review, see
 * findings; if the on-device pass comes back empty or the diff is large, escalate to the
 * laptop bridge. See /CONTRACT.md for the interface and escalation rule this implements.
 *
 * Visual language matches the pitch materials deliberately (dark, jade/brass, monospace for
 * code) — see ui/theme/ for the shared tokens.
 *
 * TODO (Delfi): swap `onDeviceEngine` below to LlamaCppReviewEngine once it works.
 * TODO (Nambert): point `laptopEngine`'s base URL at your laptop's actual local IP for testing.
 */
class MainActivity : ComponentActivity() {

    // Swap this single line to change which engine powers the on-device pass.
    private val onDeviceEngine: ReviewEngine = StubReviewEngine()
    private val laptopEngine: ReviewEngine = LaptopBridgeReviewEngine()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CodeReviewTheme {
                var screen by remember { mutableStateOf(AppScreen.Review) }
                Box(modifier = Modifier.fillMaxSize().gridBackground()) {
                    when (screen) {
                        AppScreen.Review -> ReviewScreen(
                            onDeviceEngine, laptopEngine,
                            onShowPipeline = { screen = AppScreen.Pipeline }
                        )
                        AppScreen.Pipeline -> PipelineTimelineScreen(
                            onBack = { screen = AppScreen.Review }
                        )
                    }
                }
            }
        }
    }
}

private enum class AppScreen { Review, Pipeline }

private const val ESCALATION_LINE_THRESHOLD = 40

@Composable
fun ReviewScreen(onDeviceEngine: ReviewEngine, laptopEngine: ReviewEngine, onShowPipeline: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var diffText by remember { mutableStateOf("") }
    var findings by remember { mutableStateOf<List<Finding>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var isEscalating by remember { mutableStateOf(false) }
    var hasReviewed by remember { mutableStateOf(false) }
    var reviewedBy by remember { mutableStateOf<String?>(null) }
    var isListening by remember { mutableStateOf(false) }
    var activeSample by remember { mutableStateOf(-1) }

    fun runReview(engine: ReviewEngine, source: String, busyFlag: (Boolean) -> Unit) {
        scope.launch {
            busyFlag(true)
            findings = engine.review(diffText)
            hasReviewed = true
            reviewedBy = source
            busyFlag(false)
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            isListening = true
            VoiceTrigger(context) { command ->
                isListening = false
                if (command.contains("review", ignoreCase = true)) {
                    runReview(onDeviceEngine, "on-device (voice)") { isLoading = it }
                }
            }.startListening()
        }
    }

    fun onMicClick() {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            isListening = true
            VoiceTrigger(context) { command ->
                isListening = false
                if (command.contains("review", ignoreCase = true)) {
                    runReview(onDeviceEngine, "on-device (voice)") { isLoading = it }
                }
            }.startListening()
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Escalation rule from /CONTRACT.md: offer "send to laptop" once a review has run and
    // either the diff is large or the on-device pass came back with nothing.
    val shouldOfferEscalation = hasReviewed &&
        reviewedBy?.startsWith("on-device") == true &&
        (diffText.lines().size > ESCALATION_LINE_THRESHOLD || findings.isEmpty())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column {
                Text(
                    "On-Device Code Review",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = TextHi
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Runs on this phone. Nothing leaves the device unless you explicitly escalate.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextFaint
                )
            }
            Text(
                "how it works →",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                color = Accent,
                modifier = Modifier.clickable(onClick = onShowPipeline)
            )
        }
        Spacer(Modifier.height(16.dp))

        // Quick-load sample diffs.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SampleDiffs.ALL.forEachIndexed { index, sample ->
                SampleChip(
                    label = "Sample ${index + 1}",
                    active = activeSample == index,
                    onClick = {
                        diffText = sample.content
                        activeSample = index
                        hasReviewed = false
                        findings = emptyList()
                    }
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = diffText,
            onValueChange = {
                diffText = it
                hasReviewed = false
            },
            label = { Text("Paste a diff, or load a sample above", color = TextFaint) },
            textStyle = MaterialTheme.typography.bodySmall.copy(
                fontFamily = JetBrainsMono, color = TextHi
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Surface,
                unfocusedContainerColor = Surface,
                focusedBorderColor = Accent,
                unfocusedBorderColor = FrameEdge,
                cursorColor = Accent,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .height(160.dp)
        )

        Spacer(Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { runReview(onDeviceEngine, "on-device") { isLoading = it } },
                enabled = !isLoading && !isEscalating && diffText.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = AppBg)
            ) {
                Text(if (isLoading) "Reviewing…" else "Review", fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = { onMicClick() },
                enabled = !isListening,
                border = androidx.compose.foundation.BorderStroke(1.dp, FrameEdge),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextHi)
            ) {
                Text(if (isListening) "Listening…" else "🎤 Voice")
            }
        }

        if (shouldOfferEscalation) {
            Spacer(Modifier.height(10.dp))
            EscalateButton(
                isEscalating = isEscalating,
                onClick = { runReview(laptopEngine, "laptop bridge") { isEscalating = it } }
            )
        }

        Spacer(Modifier.height(16.dp))

        if (hasReviewed) {
            Text(
                "reviewed ${reviewedBy ?: ""} · ${findings.size} finding(s)",
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMono),
                color = if (reviewedBy?.contains("laptop") == true) Accent else TextFaint
            )
            Spacer(Modifier.height(10.dp))
        }

        LazyColumn {
            items(findings) { finding -> FindingRow(finding) }
        }
    }
}

@Composable
private fun SampleChip(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) Brass else Surface)
            .border(1.dp, if (active) Brass else FrameEdge, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
            color = if (active) AppBg else TextLo,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun EscalateButton(isEscalating: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(Surface3)
            .dashedBorder(Brass, RoundedCornerShape(9.dp))
            .clickable(enabled = !isEscalating, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(
            if (isEscalating) "Sending to laptop…" else "Send to laptop for deeper review",
            style = MaterialTheme.typography.labelMedium,
            color = TextHi,
            fontWeight = FontWeight.Bold
        )
        if (!isEscalating) {
            Text("→", color = Brass, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** Simple dashed-border modifier — Compose has no built-in for this. */
private fun Modifier.dashedBorder(color: androidx.compose.ui.graphics.Color, shape: RoundedCornerShape) =
    this.drawBehind {
        val strokeWidthPx = 1.5.dp.toPx()
        val cornerPx = 9.dp.toPx()
        val stroke = Stroke(
            width = strokeWidthPx,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f)
        )
        val path = Path().apply {
            addRoundRect(
                androidx.compose.ui.geometry.RoundRect(
                    left = strokeWidthPx / 2,
                    top = strokeWidthPx / 2,
                    right = size.width - strokeWidthPx / 2,
                    bottom = size.height - strokeWidthPx / 2,
                    cornerRadius = CornerRadius(cornerPx, cornerPx)
                )
            )
        }
        drawPath(path, color = color, style = stroke)
    }

@Composable
fun FindingRow(finding: Finding) {
    val (label, stripeColor, softColor) = when (finding.severity) {
        Severity.BUG -> Triple("BUG", SevBug, SevBugSoft)
        Severity.WARNING -> Triple("WARN", Brass, BrassSoft)
        Severity.INFO -> Triple("INFO", SevInfo, SevInfoSoft)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(softColor)
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(stripeColor)
        )
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(stripeColor)
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                        color = AppBg,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    "line ${finding.line}",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                    color = TextFaint
                )
            }
            Spacer(Modifier.height(3.dp))
            Text(finding.message, style = MaterialTheme.typography.bodyMedium, color = TextHi)
        }
    }
}
