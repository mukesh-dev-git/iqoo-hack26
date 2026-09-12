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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.limitless.codereview.engine.Finding
import com.limitless.codereview.engine.LaptopBridgeReviewEngine
import com.limitless.codereview.engine.ReviewEngine
import com.limitless.codereview.engine.Severity
import com.limitless.codereview.engine.StubReviewEngine
import com.limitless.codereview.sample.SampleDiffs
import com.limitless.codereview.settings.LaptopSettingsSheet
import com.limitless.codereview.settings.loadLaptopUrl
import com.limitless.codereview.settings.saveLaptopUrl
import com.limitless.codereview.ui.theme.*
import com.limitless.codereview.voice.VoiceTrigger
import kotlinx.coroutines.launch

/**
 * The app's main screen — a chat-with-IQF interface over the same on-device review pipeline
 * the dashboard used to expose as a plain paste-and-review form. Paste (or voice-load) a diff
 * into the composer, IQF replies with findings; if the on-device pass comes back empty or the
 * diff is large, escalate to the laptop bridge from the reply. See /CONTRACT.md for the
 * interface and escalation rule this implements.
 *
 * ☰ → "New session" pushes SessionScreen — the "clone a workspace, ask the agent to explore/
 * edit/run" entry point from the pitch materials. Today it just seeds the chat with whatever
 * you type there and runs it through the same ReviewEngine; there's no real workspace/file
 * access yet (see TASKS.md — cloning and a real editor are still open work).
 *
 * Visual language matches the pitch materials deliberately (dark, jade/brass, monospace for
 * code, serif for the IQF voice) — see ui/theme/ for the shared tokens.
 *
 * TODO (Delfi): swap `onDeviceEngine` below to LlamaCppReviewEngine once it works.
 *
 * Laptop bridge URL is configurable at runtime via the ☰ menu → settings (Nambert) — no
 * recompile needed to point at a different laptop IP. See settings/LaptopSettings.kt.
 */
class MainActivity : ComponentActivity() {

    // Swap this single line to change which engine powers the on-device pass.
    private val onDeviceEngine: ReviewEngine = StubReviewEngine()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CodeReviewTheme {
                var screen by remember { mutableStateOf(AppScreen.Review) }
                // Set once when a session is started from SessionScreen, read once when
                // ReviewScreen next mounts (see its `initialDiffText` param) to seed the chat.
                var pendingMessage by remember { mutableStateOf<String?>(null) }
                Box(modifier = Modifier.fillMaxSize().gridBackground()) {
                    when (screen) {
                        AppScreen.Review -> ReviewScreen(
                            onDeviceEngine,
                            onShowPipeline = { screen = AppScreen.Pipeline },
                            onNewSession = { screen = AppScreen.Session },
                            initialDiffText = pendingMessage
                        )
                        AppScreen.Session -> SessionScreen(
                            workspacePath = DEFAULT_WORKSPACE_PATH,
                            onBack = { screen = AppScreen.Review },
                            onStartSession = { message ->
                                pendingMessage = message
                                screen = AppScreen.Review
                            }
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

private enum class AppScreen { Review, Session, Pipeline }

private const val ESCALATION_LINE_THRESHOLD = 40
private const val ON_DEVICE_MODEL_LABEL = "Qwen2.5 Coder 1.5B active"

// Placeholder until repo cloning lands (TASKS.md) — the workspace a "New session" is against.
private const val DEFAULT_WORKSPACE_PATH = "~/projects/current-repo"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    onDeviceEngine: ReviewEngine,
    onShowPipeline: () -> Unit,
    onNewSession: () -> Unit,
    initialDiffText: String? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Laptop URL is read from SharedPreferences and the engine re-created when it changes —
    // set it via ☰ → settings, no recompile needed for a different laptop IP at the event.
    var laptopUrl by remember { mutableStateOf(loadLaptopUrl(context)) }
    val laptopEngine by remember(laptopUrl) {
        derivedStateOf<ReviewEngine> { LaptopBridgeReviewEngine(laptopUrl) }
    }
    var showSettings by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var attachExpanded by remember { mutableStateOf(false) }
    var maskDiff by remember { mutableStateOf(false) } // 👁 — hide the diff from over-the-shoulder glances

    // Seeded once from SessionScreen's "Message the coding agent…" composer, if that's how we
    // got here — read once at mount, not kept in sync with the parameter afterwards.
    var diffText by remember { mutableStateOf(initialDiffText ?: "") }
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

    // A session started elsewhere lands here already carrying its first message — run it
    // immediately instead of waiting for another tap on send.
    LaunchedEffect(Unit) {
        if (!initialDiffText.isNullOrBlank()) {
            runReview(onDeviceEngine, "on-device") { isLoading = it }
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

    val modelStatusLabel = if (reviewedBy?.contains("laptop") == true) {
        "laptop bridge · qwen2.5-coder:7b"
    } else {
        ON_DEVICE_MODEL_LABEL
    }

    val hasConversation = diffText.isNotBlank() || hasReviewed

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Top bar — ☰ menu (how it works / settings) and 👁 privacy toggle, mirrors the
        // chat-shell mockup instead of the old inline headline + text links.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.Menu, contentDescription = "Menu", tint = TextHi)
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("New session") },
                        onClick = { menuExpanded = false; onNewSession() }
                    )
                    DropdownMenuItem(
                        text = { Text("How it works") },
                        onClick = { menuExpanded = false; onShowPipeline() }
                    )
                    DropdownMenuItem(
                        text = { Text("Settings") },
                        onClick = { menuExpanded = false; showSettings = true }
                    )
                }
            }
            IconButton(onClick = { maskDiff = !maskDiff }) {
                Icon(
                    if (maskDiff) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = "Toggle diff privacy",
                    tint = TextHi
                )
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (!hasConversation) {
                HeroSection()
            } else {
                Transcript(
                    diffText = diffText,
                    masked = maskDiff,
                    isLoading = isLoading,
                    hasReviewed = hasReviewed,
                    reviewedBy = reviewedBy,
                    findings = findings,
                    shouldOfferEscalation = shouldOfferEscalation,
                    isEscalating = isEscalating,
                    onEscalate = { runReview(laptopEngine, "laptop bridge") { isEscalating = it } }
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        ComposerBar(
            diffText = diffText,
            onDiffChange = { diffText = it; hasReviewed = false },
            modelStatusLabel = modelStatusLabel,
            attachExpanded = attachExpanded,
            onToggleAttach = { attachExpanded = !attachExpanded },
            laptopUrl = laptopUrl,
            onSampleSelected = { sample ->
                diffText = sample
                hasReviewed = false
                findings = emptyList()
                attachExpanded = false
            },
            onOpenSettings = { showSettings = true },
            isListening = isListening,
            onMicClick = { onMicClick() },
            canSend = diffText.isNotBlank() && !isLoading && !isEscalating,
            onSend = { runReview(onDeviceEngine, "on-device") { isLoading = it } }
        )
    }

    if (showSettings) {
        LaptopSettingsSheet(
            currentUrl = laptopUrl,
            onSave = { newUrl ->
                laptopUrl = newUrl
                saveLaptopUrl(context, newUrl)
                showSettings = false
            },
            onDismiss = { showSettings = false }
        )
    }
}

/**
 * "New session" — the workspace/agent framing from the pitch mockup, reached via ☰ → New
 * session. There's no real clone/file-explore yet (TASKS.md), so sending a message here just
 * hands it to ReviewScreen as the opening chat message over the same ReviewEngine.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionScreen(
    workspacePath: String,
    onBack: () -> Unit,
    onStartSession: (String) -> Unit,
) {
    var message by remember { mutableStateOf("") }
    var maskMessage by remember { mutableStateOf(false) } // 👁 — hide the prompt while typing it

    fun send() {
        if (message.isNotBlank()) onStartSession(message)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextHi)
            }
            IconButton(onClick = { maskMessage = !maskMessage }) {
                Icon(
                    if (maskMessage) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = "Toggle prompt privacy",
                    tint = TextHi
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Text(
            "New Session",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = TextHi
        )
        Spacer(Modifier.height(4.dp))
        Text(
            workspacePath,
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
            color = TextFaint
        )

        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                "Ask the coding agent to explore, edit, or run something in this workspace.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextFaint,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TextField(
                value = message,
                onValueChange = { message = it },
                placeholder = { Text("Message the coding agent…", color = TextFaint) },
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMono, color = TextHi),
                visualTransformation = if (maskMessage) PasswordVisualTransformation('•') else VisualTransformation.None,
                shape = RoundedCornerShape(14.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Surface,
                    unfocusedContainerColor = Surface,
                    focusedIndicatorColor = Accent,
                    unfocusedIndicatorColor = FrameEdge,
                    cursorColor = Accent,
                ),
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { send() }, enabled = message.isNotBlank()) {
                Icon(
                    Icons.Default.Send,
                    contentDescription = "Start session",
                    tint = if (message.isNotBlank()) Accent else TextFaint
                )
            }
        }
    }
}

/** Landing state before the first message — logo mark, IQF's greeting, the NPU tagline. */
@Composable
private fun HeroSection() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .border(2.dp, Brass, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("Q", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 30.sp, color = Brass)
        }
        Spacer(Modifier.height(20.dp))
        Text(
            "Let's iQuest on and on,\nDelfi.\nAre you ready?",
            fontFamily = FontFamily.Serif,
            fontSize = 26.sp,
            lineHeight = 34.sp,
            textAlign = TextAlign.Center,
            color = TextHi
        )
        Spacer(Modifier.height(14.dp))
        Text(
            "Running on your iQOO's NPU — no cloud, no laptop needed.",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = TextFaint
        )
    }
}

/** Post-first-message state — the diff as a sent message, IQF's reply, escalation offer. */
@Composable
private fun Transcript(
    diffText: String,
    masked: Boolean,
    isLoading: Boolean,
    hasReviewed: Boolean,
    reviewedBy: String?,
    findings: List<Finding>,
    shouldOfferEscalation: Boolean,
    isEscalating: Boolean,
    onEscalate: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { UserBubble(diffText, masked) }
        if (isLoading) {
            item { TypingBubble("Reviewing on-device…") }
        }
        if (hasReviewed) {
            item { AssistantResultBubble(reviewedBy, findings) }
        }
        if (shouldOfferEscalation) {
            item {
                Box(modifier = Modifier.widthIn(max = 320.dp)) {
                    EscalateButton(isEscalating = isEscalating, onClick = onEscalate)
                }
            }
        }
    }
}

@Composable
private fun UserBubble(text: String, masked: Boolean) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Surface3)
                .padding(12.dp)
        ) {
            Text(
                if (masked) "•••" else text,
                fontFamily = JetBrainsMono,
                style = MaterialTheme.typography.bodySmall,
                color = TextHi,
                maxLines = if (masked) 1 else 12
            )
        }
    }
}

@Composable
private fun TypingBubble(label: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(Surface)
                .border(1.dp, FrameEdge, RoundedCornerShape(14.dp))
                .padding(12.dp)
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                color = TextFaint
            )
        }
    }
}

@Composable
private fun AssistantResultBubble(reviewedBy: String?, findings: List<Finding>) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Column(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Surface)
                .border(1.dp, FrameEdge, RoundedCornerShape(14.dp))
                .padding(12.dp)
        ) {
            Text(
                "reviewed ${reviewedBy ?: ""} · ${findings.size} finding(s)",
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMono),
                color = if (reviewedBy?.contains("laptop") == true) Accent else TextFaint
            )
            if (findings.isEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text("No issues found on-device.", style = MaterialTheme.typography.bodySmall, color = TextLo)
            } else {
                Spacer(Modifier.height(8.dp))
                findings.forEach { finding -> FindingRow(finding) }
            }
        }
    }
}

/** Chat composer — model status strip, "Chat with IQF…" input, attach/model/mic/send row. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ComposerBar(
    diffText: String,
    onDiffChange: (String) -> Unit,
    modelStatusLabel: String,
    attachExpanded: Boolean,
    onToggleAttach: () -> Unit,
    laptopUrl: String,
    onSampleSelected: (String) -> Unit,
    onOpenSettings: () -> Unit,
    isListening: Boolean,
    onMicClick: () -> Unit,
    canSend: Boolean,
    onSend: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Surface2)
            .border(1.dp, FrameEdge, RoundedCornerShape(20.dp))
            .padding(14.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                "On-device model (offline)",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                color = TextFaint
            )
            Text(
                modelStatusLabel,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold),
                color = Accent
            )
        }

        if (attachExpanded) {
            Spacer(Modifier.height(10.dp))
            Text(
                "bridge: $laptopUrl",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                color = TextFaint
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SampleDiffs.ALL.forEachIndexed { index, sample ->
                    SampleChip(
                        label = "Sample ${index + 1}",
                        active = false,
                        onClick = { onSampleSelected(sample.content) }
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        TextField(
            value = diffText,
            onValueChange = onDiffChange,
            placeholder = { Text("Chat with IQF…", color = TextFaint) },
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMono, color = TextHi),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                cursorColor = Accent,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp, max = 140.dp)
        )

        Spacer(Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                IconButton(onClick = onToggleAttach) {
                    Icon(Icons.Default.Add, contentDescription = "Attach a sample diff", tint = TextLo)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable(onClick = onOpenSettings)
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = "Model & bridge settings", tint = Brass, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Q…", style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono), color = Brass)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IconButton(onClick = onMicClick, enabled = !isListening) {
                    Icon(
                        Icons.Default.Mic,
                        contentDescription = if (isListening) "Listening" else "Voice trigger",
                        tint = if (isListening) Accent else TextLo
                    )
                }
                IconButton(onClick = onSend, enabled = canSend) {
                    Icon(
                        Icons.Default.Send,
                        contentDescription = "Send for review",
                        tint = if (canSend) Accent else TextFaint
                    )
                }
            }
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
