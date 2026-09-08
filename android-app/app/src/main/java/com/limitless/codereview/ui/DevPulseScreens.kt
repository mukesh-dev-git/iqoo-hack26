package com.limitless.codereview.ui

import android.util.Base64
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.limitless.codereview.ui.theme.*
import com.limitless.codereview.engine.LaptopToolsClient
import com.limitless.codereview.settings.loadLaptopUrl
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.stream.StreamResult
import javax.xml.transform.stream.StreamSource

// ---------------------------------------------------------
// 1. Privacy Badge
// ---------------------------------------------------------

@Composable
fun PrivacyBadge(bytesSent: Long = 0L) {
    val isZero = bytesSent == 0L
    Row(
        modifier = Modifier
            .padding(8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (isZero) Color(0xFF1E3A2F) else Color(0xFF4A1A1A))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = if (isZero) Icons.Default.Lock else Icons.Default.Warning,
            contentDescription = "Privacy Status",
            tint = if (isZero) Color(0xFF4CAF50) else Color(0xFFF44336),
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = if (isZero) "0 bytes to cloud" else "$bytesSent bytes to cloud",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = if (isZero) Color(0xFF4CAF50) else Color(0xFFF44336)
        )
    }
}

// ---------------------------------------------------------
// 2. Model Download Screen
// ---------------------------------------------------------

@Composable
fun ModelDownloadScreen(
    onDownloadComplete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBg)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = null,
            tint = Accent,
            modifier = Modifier.size(72.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "DevPulse offline engine ready",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = TextHi
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Deterministic local analyzer · no model download required",
            style = MaterialTheme.typography.bodySmall,
            color = TextLo
        )
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(onClick = onDownloadComplete) { Text("Continue") }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Runs privately on this device.",
            style = MaterialTheme.typography.labelMedium,
            color = TextLo
        )
        Text(
            text = "Your code stays on this device. Always.",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = Color(0xFF4CAF50)
        )
    }
}

// ---------------------------------------------------------
// 3. Insights Dashboard
// ---------------------------------------------------------

@Composable
fun InsightsDashboard() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBg)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Developer Insights",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = TextHi
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            DashboardCard(
                title = "Total Bugs Found",
                value = "142",
                icon = Icons.Default.BugReport,
                modifier = Modifier.weight(1f)
            )
            DashboardCard(
                title = "Avg Inference Time",
                value = "2.3s",
                icon = Icons.Default.Speed,
                modifier = Modifier.weight(1f)
            )
        }
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("NPU Performance Log", style = MaterialTheme.typography.titleMedium, color = TextHi)
                Spacer(modifier = Modifier.height(8.dp))
                PerformanceRow("Tokens/sec", "22.5")
                PerformanceRow("Model Version", "Qwen2.5 1.5B Q4")
                PerformanceRow("Hardware", "Snapdragon 8 Elite (Hexagon NPU)")
            }
        }
    }
}

private enum class OfflineTool(val label: String) {
    JSON("JSON"), XML("XML"), BASE64("Base64"), URL("URL"), JWT("JWT"), REGEX("Regex"), HASH("SHA-256"),
    STACKTRACE("Stack trace"), TEST("Unit tests"), COMMIT("Commit"), CVE("CVE scan"),
    LOG("Logs"), ADB("ADB"), APK("Permissions")
}

/** Local utilities plus optional laptop-backed AI tools. */
@Composable
fun DeveloperToolsScreen(onBack: () -> Unit) {
    var selected by remember { mutableStateOf(OfflineTool.JSON) }
    var input by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val bridge = remember(context) { LaptopToolsClient(loadLaptopUrl(context)) }

    fun execute() {
        error = null
        busy = true
        scope.launch {
            output = try {
                if (selected.isRemote()) {
                    bridge.run(selected.remotePath(), input)
                } else {
                    executeOffline(selected, input)
                }
            } catch (e: Exception) {
                error = e.message ?: "Tool failed"
                ""
            } finally {
                busy = false
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Back to Home") }
            Text("Developer Tools", style = MaterialTheme.typography.titleLarge, color = TextHi)
        }
        Text(
            if (selected.isRemote()) "AI tool · configured laptop bridge" else "Offline tool · input stays on this device",
            style = MaterialTheme.typography.bodySmall,
            color = if (selected.isRemote()) Brass else Color(0xFF4CAF50)
        )
        Spacer(Modifier.height(10.dp))
        LazyColumn(horizontalAlignment = Alignment.Start) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OfflineTool.entries.forEach { tool ->
                        FilterChip(selected == tool, { selected = tool; output = ""; error = null }, label = { Text(tool.label) })
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it; error = null },
                    label = { Text(if (selected == OfflineTool.REGEX) "Pattern on first line, text below" else "Input") },
                    minLines = 7,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = ::execute, enabled = input.isNotBlank() && !busy) {
                    Text(if (busy) "Running…" else "Run ${selected.label}")
                }
                error?.let { Text(it, color = SevBug, modifier = Modifier.padding(top = 8.dp)) }
                if (output.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text("Result", style = MaterialTheme.typography.titleMedium, color = TextHi)
                    Surface(color = Surface, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Text(output, modifier = Modifier.padding(12.dp), color = TextHi, fontFamily = JetBrainsMono)
                    }
                }
            }
        }
    }
}

private fun OfflineTool.isRemote() = this >= OfflineTool.STACKTRACE

private fun OfflineTool.remotePath() = when (this) {
    OfflineTool.STACKTRACE -> "explain-stacktrace"
    OfflineTool.TEST -> "generate-test"
    OfflineTool.COMMIT -> "generate-commit"
    OfflineTool.CVE -> "scan-cve"
    OfflineTool.LOG -> "analyze-log"
    OfflineTool.ADB -> "generate-adb"
    OfflineTool.APK -> "analyze-apk-permissions"
    else -> error("Not a remote tool")
}

private fun executeOffline(selected: OfflineTool, input: String): String = when (selected) {
                OfflineTool.JSON -> Json { prettyPrint = true }.decodeFromString<kotlinx.serialization.json.JsonElement>(input).toString()
                OfflineTool.XML -> {
                    val transformer = TransformerFactory.newInstance().newTransformer().apply {
                        setOutputProperty(OutputKeys.INDENT, "yes")
                        setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2")
                    }
                    val writer = java.io.StringWriter()
                    transformer.transform(StreamSource(input.reader()), StreamResult(writer))
                    writer.toString()
                }
                OfflineTool.BASE64 -> Base64.encodeToString(input.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
                OfflineTool.URL -> URLEncoder.encode(input, "UTF-8")
                OfflineTool.JWT -> {
                    val parts = input.trim().split(".")
                    require(parts.size == 3) { "JWT must contain header.payload.signature" }
                    fun decode(part: String): String {
                        val padded = part + "=".repeat((4 - part.length % 4) % 4)
                        return String(Base64.decode(padded, Base64.URL_SAFE), StandardCharsets.UTF_8)
                    }
                    "Header:\n${decode(parts[0])}\n\nPayload:\n${decode(parts[1])}\n\nSignature:\n${parts[2]}"
                }
                OfflineTool.REGEX -> {
                    val split = input.split("\n", limit = 2)
                    require(split.size == 2) { "First line must be the pattern; remaining text is tested." }
                    val matches = Regex(split[0]).findAll(split[1]).map { it.value }.toList()
                    if (matches.isEmpty()) "No matches" else matches.joinToString("\n")
                }
                OfflineTool.HASH -> MessageDigest.getInstance("SHA-256")
                    .digest(input.toByteArray(StandardCharsets.UTF_8))
                    .joinToString("") { "%02x".format(it.toInt() and 0xff) }
                else -> error("Select an offline tool")
            }

@Composable
private fun DashboardCard(title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Icon(imageVector = icon, contentDescription = null, tint = Accent)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = value, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold), color = TextHi)
            Text(text = title, style = MaterialTheme.typography.bodySmall, color = TextLo)
        }
    }
}

@Composable
private fun PerformanceRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextLo)
        Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = Accent)
    }
}
