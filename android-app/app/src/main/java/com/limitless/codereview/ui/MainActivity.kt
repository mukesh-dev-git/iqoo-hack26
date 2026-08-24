package com.limitless.codereview.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.limitless.codereview.engine.Finding
import com.limitless.codereview.engine.ReviewEngine
import com.limitless.codereview.engine.Severity
import com.limitless.codereview.engine.StubReviewEngine
import kotlinx.coroutines.launch

/**
 * Minimal shell: paste a diff, hit "Review", see findings. Deliberately dumb — this is the
 * scaffold everyone builds on top of, not the final UI. Uses StubReviewEngine by default;
 * see /CONTRACT.md and the engine package for how the real engines plug in.
 *
 * TODO (Mukesh): wire the mic button to VoiceTrigger.
 * TODO (Delfi): swap `engine` below to LlamaCppReviewEngine once it works.
 * TODO (Nambert): add the "send to laptop" escalation button using LaptopBridgeReviewEngine.
 */
class MainActivity : ComponentActivity() {

    // Swap this single line to change which engine powers the app.
    private val engine: ReviewEngine = StubReviewEngine()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                ReviewScreen(engine)
            }
        }
    }
}

@Composable
fun ReviewScreen(engine: ReviewEngine) {
    var diffText by remember { mutableStateOf("") }
    var findings by remember { mutableStateOf<List<Finding>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("On-Device Code Review", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = diffText,
            onValueChange = { diffText = it },
            label = { Text("Paste a diff") },
            modifier = Modifier.fillMaxWidth().weight(1f, fill = false).height(180.dp)
        )

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = {
                scope.launch {
                    isLoading = true
                    findings = engine.review(diffText)
                    isLoading = false
                }
            },
            enabled = !isLoading
        ) {
            Text(if (isLoading) "Reviewing…" else "Review")
        }

        Spacer(Modifier.height(16.dp))

        LazyColumn {
            items(findings) { finding -> FindingRow(finding) }
        }
    }
}

@Composable
fun FindingRow(finding: Finding) {
    val label = when (finding.severity) {
        Severity.BUG -> "BUG"
        Severity.WARNING -> "WARN"
        Severity.INFO -> "INFO"
    }
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("[$label] line ${finding.line}", style = MaterialTheme.typography.labelLarge)
            Text(finding.message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
