package com.limitless.codereview.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.limitless.codereview.engine.Finding
import com.limitless.codereview.engine.LaptopBridgeReviewEngine
import com.limitless.codereview.engine.ReviewEngine
import com.limitless.codereview.engine.Severity
import com.limitless.codereview.engine.StubReviewEngine
import com.limitless.codereview.sample.SampleDiffs
import com.limitless.codereview.voice.VoiceTrigger
import kotlinx.coroutines.launch

/**
 * The app's main screen — the "dashboard". Paste (or voice-load) a diff, hit Review, see
 * findings; if the on-device pass comes back empty or the diff is large, escalate to the
 * laptop bridge. See /CONTRACT.md for the interface and escalation rule this implements.
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
            MaterialTheme {
                ReviewScreen(onDeviceEngine, laptopEngine)
            }
        }
    }
}

private const val ESCALATION_LINE_THRESHOLD = 40

@Composable
fun ReviewScreen(onDeviceEngine: ReviewEngine, laptopEngine: ReviewEngine) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var diffText by remember { mutableStateOf("") }
    var findings by remember { mutableStateOf<List<Finding>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var isEscalating by remember { mutableStateOf(false) }
    var hasReviewed by remember { mutableStateOf(false) }
    var reviewedBy by remember { mutableStateOf<String?>(null) }
    var isListening by remember { mutableStateOf(false) }

    fun runReview(engine: ReviewEngine, source: String, busyFlag: (Boolean) -> Unit) {
        scope.launch {
            busyFlag(true)
            findings = engine.review(diffText)
            hasReviewed = true
            reviewedBy = source
            busyFlag(false)
        }
    }

    // Mic permission + voice trigger. "review this" (or anything containing "review") fires
    // the same on-device review path as the button.
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

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("On-Device Code Review", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(4.dp))
        Text(
            "Runs on this phone. Nothing leaves the device unless you explicitly escalate.",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(12.dp))

        // Quick-load sample diffs — makes demoing/testing painless without a real repo handy.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SampleDiffs.ALL.forEachIndexed { index, sample ->
                OutlinedButton(onClick = {
                    diffText = sample.content
                    hasReviewed = false
                    findings = emptyList()
                }) {
                    Text("Sample ${index + 1}")
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = diffText,
            onValueChange = {
                diffText = it
                hasReviewed = false
            },
            label = { Text("Paste a diff, or load a sample above") },
            modifier = Modifier.fillMaxWidth().weight(1f, fill = false).height(160.dp)
        )

        Spacer(Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { runReview(onDeviceEngine, "on-device") { isLoading = it } },
                enabled = !isLoading && !isEscalating && diffText.isNotBlank()
            ) {
                Text(if (isLoading) "Reviewing…" else "Review")
            }

            OutlinedButton(
                onClick = { onMicClick() },
                enabled = !isListening
            ) {
                Text(if (isListening) "Listening…" else "🎤 Voice")
            }
        }

        if (shouldOfferEscalation) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { runReview(laptopEngine, "laptop bridge") { isEscalating = it } },
                enabled = !isEscalating
            ) {
                Text(if (isEscalating) "Sending to laptop…" else "Send to laptop for deeper review")
            }
        }

        Spacer(Modifier.height(16.dp))

        if (hasReviewed) {
            Text(
                "Reviewed ${reviewedBy ?: ""} · ${findings.size} finding(s)",
                style = MaterialTheme.typography.labelMedium
            )
            Spacer(Modifier.height(8.dp))
        }

        LazyColumn {
            items(findings) { finding -> FindingRow(finding) }
        }
    }
}

@Composable
fun FindingRow(finding: Finding) {
    val (label, containerColor) = when (finding.severity) {
        Severity.BUG -> "BUG" to MaterialTheme.colorScheme.errorContainer
        Severity.WARNING -> "WARN" to Color(0xFFFFE9C8)
        Severity.INFO -> "INFO" to MaterialTheme.colorScheme.surfaceVariant
    }
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("[$label] line ${finding.line}", style = MaterialTheme.typography.labelLarge)
            Text(finding.message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
