package com.limitless.codereview.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.limitless.codereview.ui.theme.*

private data class PipelineStep(
    val label: String,
    val title: String,
    val description: String,
    val done: Boolean,
    val tag: String,
)

private val STEPS = listOf(
    PipelineStep(
        "1", "Trigger — voice or paste",
        "Say \"review this\" or paste/load a diff. Voice runs through Android's SpeechRecognizer, wired and tested on-device.",
        done = true, tag = "TODAY"
    ),
    PipelineStep(
        "2", "Inline review, on-device",
        "The diff goes to ReviewEngine.review() — a working stub today while the CPU llama.cpp model finishes wiring; the Hexagon NPU via GenieX at the event, same interface.",
        done = true, tag = "TODAY · AT EVENT"
    ),
    PipelineStep(
        "3", "Escalation decision",
        "If the diff is over ~40 lines, or the on-device pass returns nothing on something that should have flagged, the app offers to send it deeper.",
        done = true, tag = "TODAY"
    ),
    PipelineStep(
        "4", "Crossing to the laptop",
        "Diff moves over Wi-Fi today, Office Kit at the event, to laptop-bridge. Two real backends: Ollama 7B, and an NPU backend validated end-to-end (6.74× vs CPU).",
        done = true, tag = "TODAY"
    ),
    PipelineStep(
        "5", "Result, back on the phone",
        "Findings return as JSON, parsed, and land in the same list — tagged by which tier produced them. Zero cloud calls, start to finish.",
        done = true, tag = "TODAY"
    ),
)

@Composable
fun PipelineTimelineScreen(onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "← back",
                color = Accent,
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMono),
                modifier = Modifier.clickable(onClick = onBack)
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "How it works",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = TextHi
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Same pipeline the app actually runs — trigger to result.",
            style = MaterialTheme.typography.bodySmall,
            color = TextFaint
        )
        Spacer(Modifier.height(20.dp))

        LazyColumn {
            items(STEPS) { step -> TimelineEntry(step) }
        }
    }
}

@Composable
private fun TimelineEntry(step: PipelineStep) {
    Row(modifier = Modifier.fillMaxWidth()) {
        // spine + dot
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(28.dp)) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(Surface)
                    .border(2.dp, if (step.done) Accent else Brass, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (step.done) Accent else Brass)
                )
            }
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .weight(1f)
                    .background(FrameEdge)
            )
        }

        Spacer(Modifier.width(14.dp))

        // card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 22.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Surface)
                .border(1.dp, if (step.done) Accent else FrameEdge, RoundedCornerShape(12.dp))
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "STEP ${step.label}",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                    color = TextFaint
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(AccentSoft)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        step.tag,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                        color = Accent,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(step.title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = TextHi)
            Spacer(Modifier.height(4.dp))
            Text(step.description, style = MaterialTheme.typography.bodySmall, color = TextLo)
        }
    }
}
