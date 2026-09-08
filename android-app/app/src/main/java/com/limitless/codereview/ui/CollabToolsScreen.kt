package com.limitless.codereview.ui

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.limitless.codereview.ui.theme.*

@Composable
fun CollabToolsScreen(onBack: () -> Unit) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Snippet Vault", "Share Review", "Voice Tasks", "Tech Debt")

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Home") }
            Spacer(Modifier.width(8.dp))
            Text("Team Collaboration", style = MaterialTheme.typography.titleLarge, color = TextHi)
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
            0 -> SnippetVaultScreen()
            1 -> ShareReviewScreen()
            2 -> VoiceTasksScreen()
            3 -> TechDebtScreen()
        }
    }
}

@Composable
fun SnippetVaultScreen() {
    var title by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var snippets by remember { mutableStateOf(listOf("Singleton Pattern" to "object DB {\n  val conn = connect()\n}")) }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Snippet Title") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = code,
                onValueChange = { code = it },
                label = { Text("Code") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )
            Button(
                onClick = {
                    if (title.isNotBlank() && code.isNotBlank()) {
                        snippets = snippets + (title to code)
                        title = ""
                        code = ""
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                Text("Save to Encrypted Vault")
            }
        }
        
        item { Spacer(Modifier.height(8.dp)) }
        
        items(snippets.size) { index ->
            val (t, c) = snippets[index]
            Surface(color = Surface, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(t, style = MaterialTheme.typography.titleMedium, color = Accent)
                    Spacer(Modifier.height(8.dp))
                    Text(c, style = MaterialTheme.typography.bodyMedium, color = TextHi, fontFamily = JetBrainsMono)
                }
            }
        }
    }
}

@Composable
fun ShareReviewScreen() {
    val context = LocalContext.current
    
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Share your last Code Review findings with your team.", color = TextLo)
        Button(
            onClick = {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "DevPulse Code Review")
                    putExtra(Intent.EXTRA_TEXT, "DevPulse found 2 BUGs and 1 WARNING in the latest commit.\n\nLine 42: NullPointerException risk\nLine 108: Unused variable")
                }
                context.startActivity(Intent.createChooser(intent, "Share Review"))
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Export via ShareSheet")
        }
        
        Button(
            onClick = { /* Simulated QR Code pop-up */ },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Surface3, contentColor = TextHi)
        ) {
            Text("Generate P2P QR Code")
        }
    }
}

@Composable
fun VoiceTasksScreen() {
    var result by remember { mutableStateOf("") }
    
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Record a brief standup or meeting snippet, and let the on-device AI extract action items.", color = TextLo)
        
        Button(
            onClick = {
                result = """
                    Extracted Action Items:
                    1. [ ] Fix the memory leak in MainActivity
                    2. [ ] Update the CI/CD pipeline script
                    3. [ ] Review PR #405 by Tuesday
                """.trimIndent()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("🎤 Simulate Voice Recording")
        }
        
        if (result.isNotBlank()) {
            Surface(color = Surface, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                Text(result, modifier = Modifier.padding(12.dp), color = TextHi, fontFamily = JetBrainsMono)
            }
        }
    }
}

@Composable
fun TechDebtScreen() {
    var issue by remember { mutableStateOf("") }
    var debtList by remember { mutableStateOf(listOf("Update Retrofit version (Medium)", "Refactor God class (High)")) }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            OutlinedTextField(
                value = issue,
                onValueChange = { issue = it },
                label = { Text("Log new Technical Debt") },
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    if (issue.isNotBlank()) {
                        debtList = debtList + issue
                        issue = ""
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                Text("Log Issue")
            }
        }
        
        item { Spacer(Modifier.height(8.dp)) }
        
        items(debtList.size) { index ->
            Surface(color = Surface, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                Text(debtList[index], modifier = Modifier.padding(16.dp), color = TextHi)
            }
        }
    }
}
