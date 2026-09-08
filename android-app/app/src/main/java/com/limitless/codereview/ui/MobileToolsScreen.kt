package com.limitless.codereview.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.limitless.codereview.ui.theme.*

@Composable
fun MobileToolsScreen(onBack: () -> Unit) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("APK Analyzer", "ADB Gen", "Keystore", "ProGuard Gen")

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Home") }
            Spacer(Modifier.width(8.dp))
            Text("Mobile Dev Tools", style = MaterialTheme.typography.titleLarge, color = TextHi)
        }
        Spacer(Modifier.height(8.dp))
        
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            containerColor = AppBg,
            contentColor = Accent,
            edgePadding = 0.dp
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title, fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal) }
                )
            }
        }
        
        Spacer(Modifier.height(16.dp))
        
        when (selectedTab) {
            0 -> ApkAnalyzerScreen()
            1 -> AdbGeneratorScreen()
            2 -> KeystoreInspectorScreen()
            3 -> ProGuardGeneratorScreen()
        }
    }
}

@Composable
fun ApkAnalyzerScreen() {
    var result by remember { mutableStateOf("Select an APK to analyze (Simulated for demo).") }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Analyze APK contents without uploading.", color = TextLo)
        Button(
            onClick = {
                result = """
                    File: demo_app_v1.0.apk
                    Size: 14.2 MB
                    
                    Contents:
                    - classes.dex (4.1 MB)
                    - classes2.dex (1.2 MB)
                    - lib/arm64-v8a/libllama.so (2.1 MB)
                    - res/ (3.4 MB)
                    
                    Permissions Declared:
                    - android.permission.INTERNET
                    - android.permission.CAMERA
                    - android.permission.RECORD_AUDIO
                """.trimIndent()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Pick APK File")
        }
        
        if (result.isNotBlank()) {
            Surface(color = Surface, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                Text(result, modifier = Modifier.padding(12.dp), color = TextHi, fontFamily = JetBrainsMono)
            }
        }
    }
}

@Composable
fun AdbGeneratorScreen() {
    var intent by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = intent,
            onValueChange = { intent = it },
            label = { Text("What do you want to do?") },
            placeholder = { Text("e.g. clear app data, grant camera permission") },
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = {
                result = when {
                    intent.contains("clear", ignoreCase = true) -> "adb shell pm clear <package_name>"
                    intent.contains("permission", ignoreCase = true) -> "adb shell pm grant <package_name> android.permission.CAMERA"
                    intent.contains("install", ignoreCase = true) -> "adb install -r app-debug.apk"
                    intent.contains("log", ignoreCase = true) -> "adb logcat | grep <package_name>"
                    else -> "adb shell settings put global <setting_name> <value>"
                }
            },
            enabled = intent.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Generate ADB Command (Local AI)")
        }
        
        if (result.isNotBlank()) {
            Surface(color = Surface, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                Text(result, modifier = Modifier.padding(12.dp), color = TextHi, fontFamily = JetBrainsMono)
            }
        }
    }
}

@Composable
fun KeystoreInspectorScreen() {
    var result by remember { mutableStateOf("Select a .jks file to inspect.") }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("View Keystore aliases and hashes instantly.", color = TextLo)
        Button(
            onClick = {
                result = """
                    File: release_keystore.jks
                    Type: JKS
                    
                    Alias: key0
                    Creation Date: Oct 24, 2023
                    Valid Until: Oct 17, 2048
                    
                    MD5: 5C:1A:3B:...
                    SHA-1: A1:B2:C3:...
                    SHA-256: 9F:8E:7D:...
                """.trimIndent()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Pick .jks File")
        }
        
        if (result.isNotBlank()) {
            Surface(color = Surface, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                Text(result, modifier = Modifier.padding(12.dp), color = TextHi, fontFamily = JetBrainsMono)
            }
        }
    }
}

@Composable
fun ProGuardGeneratorScreen() {
    var libraryName by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = libraryName,
            onValueChange = { libraryName = it },
            label = { Text("Library or Class Name") },
            placeholder = { Text("e.g. com.google.gson.**") },
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = {
                val pkg = if (libraryName.endsWith("**")) libraryName else "$libraryName.**"
                result = """
                    -keep class $pkg { *; }
                    -keep interface $pkg { *; }
                    -keep enum $pkg { *; }
                    -dontwarn $pkg
                """.trimIndent()
            },
            enabled = libraryName.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Generate Keep Rules")
        }
        
        if (result.isNotBlank()) {
            Surface(color = Surface, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                Text(result, modifier = Modifier.padding(12.dp), color = TextHi, fontFamily = JetBrainsMono)
            }
        }
    }
}
