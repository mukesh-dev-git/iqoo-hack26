package com.limitless.codereview.ui

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.limitless.codereview.camera.CameraScanScreen
import com.limitless.codereview.engine.Finding
import com.limitless.codereview.engine.LaptopBridgeReviewEngine
import com.limitless.codereview.engine.LlamaCppReviewEngine
import com.limitless.codereview.engine.ReviewEngine
import com.limitless.codereview.engine.Severity
import com.limitless.codereview.github.GitHubClient
import com.limitless.codereview.github.GitHubLinks
import com.limitless.codereview.github.PullRequestRef
import com.limitless.codereview.sample.SampleDiffs
import com.limitless.codereview.settings.LaptopSettingsSheet
import com.limitless.codereview.settings.loadGitHubToken
import com.limitless.codereview.settings.loadLaptopUrl
import com.limitless.codereview.settings.saveGitHubToken
import com.limitless.codereview.settings.saveLaptopUrl
import com.limitless.codereview.ui.theme.*
import com.limitless.codereview.voice.VoiceTrigger
import com.limitless.codereview.sensors.DevPulseSensorManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * On-Device Code Review — Flagship Multi-Modal Dashboard.
 * Supports:
 *  - Private offline static analysis + optional Office Kit Laptop Bridge escalation
 *  - 📷 Camera OCR screen scanning (Google ML Kit)
 *  - 🎤 Voice trigger ("review this")
 *  - 📳 Haptic pulse alerts on critical BUG findings
 *  - 🔧 One-tap interactive "Suggest Fix" code generation
 *  - 📋 Auto-clipboard diff detection & Android Share-to-App
 */
class MainActivity : ComponentActivity() {

    private val onDeviceEngine: ReviewEngine by lazy { LlamaCppReviewEngine(this) }

    // Incoming share targets. Held as snapshot state (not constructor locals) so onNewIntent can
    // update them while the activity is already on screen and the UI recomposes around it.
    private val incomingDiff = mutableStateOf<String?>(null)
    private val incomingPr = mutableStateOf<PullRequestRef?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        handleIncomingIntent(intent)

        setContent {
            CodeReviewTheme {
                var screen by remember { mutableStateOf(AppScreen.Home) }
                // The bundled MVP uses a deterministic offline analyzer. Do not show a fake
                // 900 MB download progress bar when no model is actually being downloaded.
                Box(modifier = Modifier.fillMaxSize().gridBackground()) {
                    when (screen) {
                        AppScreen.Home -> HomeHubScreen(
                            onNavigateToReview = { screen = AppScreen.Review },
                            onNavigateToTools = { screen = AppScreen.Tools },
                            onNavigateToNetwork = { screen = AppScreen.Network },
                            onNavigateToMobile = { screen = AppScreen.Mobile },
                            onNavigateToDocs = { screen = AppScreen.Docs },
                            onNavigateToCollab = { screen = AppScreen.Collab },
                            onNavigateToInsights = { screen = AppScreen.Insights },
                            onNavigateToPrivacy = { screen = AppScreen.Privacy }
                        )
                        AppScreen.Review -> ReviewScreen(
                            onDeviceEngine,
                            initialDiff = incomingDiff.value.orEmpty(),
                            initialPr = incomingPr.value,
                            onShowPipeline = { screen = AppScreen.Pipeline },
                            onShowInsights = { screen = AppScreen.Insights },
                            onShowTools = { screen = AppScreen.Tools },
                            onBack = { screen = AppScreen.Home }
                        )
                        AppScreen.Pipeline -> PipelineTimelineScreen(
                            onBack = { screen = AppScreen.Review }
                        )
                        AppScreen.Insights -> {
                            Column(modifier = Modifier.fillMaxSize()) {
                                Row(modifier = Modifier.padding(8.dp)) {
                                    TextButton(onClick = { screen = AppScreen.Home }) {
                                        Text("← Back to Home")
                                    }
                                }
                                InsightsDashboard()
                            }
                        }
                        AppScreen.Tools -> DeveloperToolsScreen(onBack = { screen = AppScreen.Home })
                        AppScreen.Network -> NetworkToolsScreen(onBack = { screen = AppScreen.Home })
                        AppScreen.Mobile -> MobileToolsScreen(onBack = { screen = AppScreen.Home })
                        AppScreen.Docs -> DocsReferenceScreen(onBack = { screen = AppScreen.Home })
                        AppScreen.Collab -> CollabToolsScreen(onBack = { screen = AppScreen.Home })
                        AppScreen.Privacy -> PrivacyScreen(onBack = { screen = AppScreen.Home })
                        }
                    }
                }
            }
        }

@Composable
fun PlaceholderScreen(title: String, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        TextButton(onClick = onBack) { Text("← Back to Home") }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.headlineMedium, color = Color.White)
        Text("Coming soon in the next phase.", color = Color.Gray)
    }
}

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    /**
     * A share can carry either a GitHub PR link (fetch + review it) or diff text (load it as-is).
     */
    private fun handleIncomingIntent(intent: Intent?) {
        val sharedText = if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            intent.getStringExtra(Intent.EXTRA_TEXT)
        } else null

        val pr = sharedText?.let { GitHubLinks.parsePrUrl(it) }
        if (pr != null) {
            incomingPr.value = pr
            incomingDiff.value = null
        } else if (sharedText != null) {
            incomingDiff.value = sharedText
            incomingPr.value = null
        }
    }
}

private enum class AppScreen { Home, Review, Pipeline, Insights, Tools, Network, Mobile, Docs, Collab, Privacy }

private fun triggerBugHaptic(context: Context) {
    try {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 80, 50, 120), -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(200)
        }
    } catch (_: Exception) {}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    onDeviceEngine: ReviewEngine,
    initialDiff: String = "",
    initialPr: PullRequestRef? = null,
    onShowPipeline: () -> Unit,
    onShowInsights: () -> Unit,
    onShowTools: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var laptopUrl by remember { mutableStateOf(loadLaptopUrl(context)) }
    val laptopEngine by remember(laptopUrl) {
        derivedStateOf<ReviewEngine> { LaptopBridgeReviewEngine(laptopUrl) }
    }
    var githubToken by remember { mutableStateOf(loadGitHubToken(context)) }
    val githubClient = remember(githubToken) { GitHubClient(githubToken) }
    var showSettings by remember { mutableStateOf(false) }
    var isScanningCamera by remember { mutableStateOf(false) }

    var diffText by remember(initialDiff) { mutableStateOf(initialDiff) }
    var findings by remember { mutableStateOf<List<Finding>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var isEscalating by remember { mutableStateOf(false) }
    var hasReviewed by remember { mutableStateOf(false) }
    var reviewedBy by remember { mutableStateOf<String?>(null) }
    var isListening by remember { mutableStateOf(false) }
    var activeSample by remember { mutableStateOf(-1) }
    var isFaceDownLocked by remember { mutableStateOf(false) }
    val currentDiff by rememberUpdatedState(diffText)

    // Check clipboard for diffs
    var clipboardDiff by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = clipboard?.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val text = clip.getItemAt(0).text?.toString() ?: ""
            if (text.isNotBlank() && (text.contains("diff --git") || text.contains("@@ -") || text.lines().any { it.startsWith("+") || it.startsWith("-") })) {
                if (text != diffText) {
                    clipboardDiff = text
                }
            }
        }
    }

    fun runReview(engine: ReviewEngine, source: String, sourceDiff: String = diffText, busyFlag: (Boolean) -> Unit) {
        scope.launch {
            busyFlag(true)
            try {
                val results = engine.review(sourceDiff)
                findings = results
                hasReviewed = true
                reviewedBy = source

                // 📳 Haptic trigger on BUG
                if (results.any { it.severity == Severity.BUG }) {
                    triggerBugHaptic(context)
                }
            } catch (e: Exception) {
                hasReviewed = true
                reviewedBy = "$source (error)"
                findings = listOf(
                    Finding(
                        line = 1,
                        severity = Severity.BUG,
                        message = "Network / Connection error: ${e.localizedMessage ?: "Failed to connect to laptop"}. Check Wi-Fi & laptop URL in settings."
                    )
                )
            } finally {
                busyFlag(false)
            }
        }
    }

    // Hardware interactions are now connected to the active review flow. Face-down temporarily
    // hides the code; shaking retries the current diff. Tilt is intentionally surfaced as a
    // lightweight status hook until the diff list is migrated to a shared scroll state.
    val sensorManager = remember { DevPulseSensorManager(context) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    
    DisposableEffect(sensorManager) {
        sensorManager.onShakeCallback = {
            if (currentDiff.isNotBlank() && !isLoading && !isEscalating) {
                runReview(onDeviceEngine, "on-device (shake retry)", currentDiff) { isLoading = it }
            }
        }
        sensorManager.onFaceDownCallback = { isFaceDownLocked = it }
        sensorManager.onTiltCallback = { tiltAmount ->
            scope.launch {
                listState.scrollBy(tiltAmount * 100f)
            }
        }
        sensorManager.startListening()
        onDispose {
            sensorManager.stopListening()
            sensorManager.onShakeCallback = null
            sensorManager.onFaceDownCallback = null
            sensorManager.onTiltCallback = null
        }
    }

    // GitHub PR share-in: fetch the diff off the API (IO dispatcher — OkHttp is blocking), load
    // it into the editor, and kick off the on-device review immediately. One-shot per PR ref.
    initialPr?.let { ref ->
        LaunchedEffect(ref) {
            isLoading = true
            try {
                val diff = withContext(Dispatchers.IO) { githubClient.fetchPrDiff(ref) }
                diffText = diff
                hasReviewed = false
                findings = emptyList()
                isLoading = false
                runReview(onDeviceEngine, "on-device (GitHub ${ref})") { isLoading = it }
            } catch (e: Exception) {
                isLoading = false
                hasReviewed = true
                reviewedBy = "github (error)"
                findings = listOf(
                    Finding(
                        line = 0,
                        severity = Severity.BUG,
                        message = "Couldn't fetch ${ref} from GitHub: ${e.message}"
                    )
                )
            }
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

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            isScanningCamera = true
        }
    }

    if (isScanningCamera) {
        CameraScanScreen(
            onCodeScanned = { scannedCode ->
                diffText = scannedCode
                isScanningCamera = false
                activeSample = -1
                runReview(onDeviceEngine, "on-device (camera scan)") { isLoading = it }
            },
            onDismiss = { isScanningCamera = false }
        )
        return
    }

    if (isFaceDownLocked) {
        Box(
            modifier = Modifier.fillMaxSize().background(AppBg),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "SESSION LOCKED\\nLift the phone to continue",
                color = TextHi,
                style = MaterialTheme.typography.titleMedium,
                fontFamily = JetBrainsMono
            )
        }
        return
    }

    val shouldOfferEscalation = hasReviewed && reviewedBy?.startsWith("on-device") == true

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) {
                Text("← Back to Home")
            }
        }
        
        // Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "On-Device Code Review",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = TextHi
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "Local analysis by default · Bridge only when you choose it",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextFaint
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Tools",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                    color = Accent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable(onClick = onShowTools)
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                )
                Text(
                    "Insights 📈",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                    color = Accent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable(onClick = onShowInsights)
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                )
                Text(
                    "Pipeline →",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                    color = Accent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable(onClick = onShowPipeline)
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                )
                IconButton(
                    onClick = { showSettings = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = Brass
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        // Dynamic Privacy Badge (Phase 3 integration)
        PrivacyBadge(bytesSent = if (reviewedBy?.contains("laptop") == true) diffText.length.toLong() else 0L)
        Spacer(Modifier.height(10.dp))

        // Clickable Bridge configuration card
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = Surface,
            border = BorderStroke(1.dp, FrameEdge),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { showSettings = true }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("⚡", style = MaterialTheme.typography.bodySmall)
                    Text(
                        text = "Bridge: $laptopUrl",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                        color = TextHi
                    )
                }
                Text(
                    text = "⚙ EDIT IP",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold),
                    color = Brass
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        // Quick-load sample diffs
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

        // Auto-detected clipboard banner
        clipboardDiff?.let { clipText ->
            Spacer(Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = AccentSoft,
                border = BorderStroke(1.dp, Accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        diffText = clipText
                        clipboardDiff = null
                        activeSample = -1
                        runReview(onDeviceEngine, "on-device (clipboard)") { isLoading = it }
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.ContentPaste, contentDescription = null, tint = Accent, modifier = Modifier.size(16.dp))
                        Text(
                            "Diff detected in clipboard — tap to review",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                            color = Accent
                        )
                    }
                    Text("PASTE →", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Accent)
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = diffText,
            onValueChange = {
                diffText = it
                hasReviewed = false
            },
            label = { Text("Paste a diff, load a sample, or scan screen", color = TextFaint) },
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
                .height(150.dp)
        )

        Spacer(Modifier.height(12.dp))

        // Action Buttons Row: Review + 🎤 Voice + 📷 Scan Screen
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { runReview(onDeviceEngine, "on-device") { isLoading = it } },
                enabled = !isLoading && !isEscalating && diffText.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = AppBg),
                modifier = Modifier.weight(1.2f)
            ) {
                Text(if (isLoading) "Reviewing…" else "Review", fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = {
                    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
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
                },
                enabled = !isListening,
                border = BorderStroke(1.dp, FrameEdge),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextHi),
                modifier = Modifier.weight(1f)
            ) {
                Text(if (isListening) "Listening…" else "🎤 Voice")
            }

            OutlinedButton(
                onClick = {
                    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                    if (granted) {
                        isScanningCamera = true
                    } else {
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                },
                border = BorderStroke(1.dp, Brass),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Brass),
                modifier = Modifier.weight(1.1f)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp), tint = Brass)
                    Text("📷 Scan", fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (shouldOfferEscalation) {
            Spacer(Modifier.height(10.dp))
            EscalateButton(
                isEscalating = isEscalating,
                onClick = { runReview(laptopEngine, "laptop bridge") { isEscalating = it } }
            )
        }

        Spacer(Modifier.height(14.dp))

        // Provenance & Metrics Summary Bar
        if (hasReviewed) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${if (findings.any { it.severity == Severity.BUG }) "🔴" else "🟢"} ${findings.size} finding(s)",
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold),
                    color = TextHi
                )
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (reviewedBy?.contains("laptop") == true) BrassSoft else AccentSoft,
                    border = BorderStroke(1.dp, if (reviewedBy?.contains("laptop") == true) Brass else Accent)
                ) {
                    Text(
                        text = if (reviewedBy?.contains("laptop") == true) "⚡ Laptop Bridge (7B)" else "🔒 100% On-Device",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold),
                        color = if (reviewedBy?.contains("laptop") == true) Brass else Accent,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        LazyColumn(state = listState) {
            items(findings) { finding -> FindingRow(finding) }
        }
    }

    if (showSettings) {
        LaptopSettingsSheet(
            currentUrl = laptopUrl,
            currentToken = githubToken,
            onSaveUrl = { newUrl ->
                laptopUrl = newUrl
                saveLaptopUrl(context, newUrl)
                showSettings = false
            },
            onSaveToken = { newToken ->
                githubToken = newToken
                saveGitHubToken(context, newToken)
                showSettings = false
            },
            onDismiss = { showSettings = false }
        )
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

private fun Modifier.dashedBorder(color: Color, shape: RoundedCornerShape) =
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
    var showFix by remember { mutableStateOf(false) }

    val (label, stripeColor, softColor) = when (finding.severity) {
        Severity.BUG -> Triple("BUG", SevBug, SevBugSoft)
        Severity.WARNING -> Triple("WARN", Brass, BrassSoft)
        Severity.INFO -> Triple("INFO", SevInfo, SevInfoSoft)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(softColor)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
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

            // Interactive Suggest Fix trigger for BUGs
            if (finding.severity == Severity.BUG) {
                Text(
                    text = if (showFix) "Hide Fix ↑" else "Suggest Fix 💡",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = JetBrainsMono,
                        fontWeight = FontWeight.Bold
                    ),
                    color = Accent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { showFix = !showFix }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        Spacer(Modifier.height(4.dp))
        Text(finding.message, style = MaterialTheme.typography.bodyMedium, color = TextHi)

        // Expandable Suggest Fix Code Card
        if (showFix && finding.severity == Severity.BUG) {
            Spacer(Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = AppBg,
                border = BorderStroke(1.dp, Accent.copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        "// PROPOSED PATCH",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = JetBrainsMono,
                            fontWeight = FontWeight.Bold
                        ),
                        color = Accent
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        when {
                            finding.message.contains("null", ignoreCase = true) -> "- return user.name\n+ return user?.name ?: \"Unknown\""
                            finding.message.contains("index", ignoreCase = true) || finding.message.contains("bound", ignoreCase = true) -> "- items.subList(start, items.size + 1)\n+ items.subList(start, items.size)"
                            finding.message.contains("sql", ignoreCase = true) || finding.message.contains("injection", ignoreCase = true) -> "- \"SELECT * FROM users WHERE id=\" + id\n+ db.query(\"SELECT * FROM users WHERE id=?\", id)"
                            finding.message.contains("todo", ignoreCase = true) -> "- TODO\n+ // Implement and cover this path before merging"
                            else -> "+ // Add safe null / boundary guard"
                        },
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMono),
                        color = DiffAddText
                    )
                }
            }
        }
    }
}
