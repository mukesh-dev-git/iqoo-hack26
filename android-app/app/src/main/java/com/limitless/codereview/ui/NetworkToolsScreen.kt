package com.limitless.codereview.ui

import android.util.Base64
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.limitless.codereview.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.nio.charset.StandardCharsets

@Composable
fun NetworkToolsScreen(onBack: () -> Unit) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("REST Tester", "JWT Decoder", "DNS Lookup", "cURL Builder")

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Home") }
            Spacer(Modifier.width(8.dp))
            Text("Network & API Tools", style = MaterialTheme.typography.titleLarge, color = TextHi)
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
            0 -> RestApiTester()
            1 -> JwtDecoderScreen()
            2 -> DnsLookupScreen()
            3 -> CurlBuilderScreen()
        }
    }
}

@Composable
fun RestApiTester() {
    var url by remember { mutableStateOf("https://jsonplaceholder.typicode.com/todos/1") }
    var method by remember { mutableStateOf("GET") }
    var body by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("GET", "POST", "PUT", "DELETE").forEach { m ->
                    FilterChip(
                        selected = method == m,
                        onClick = { method = m },
                        label = { Text(m) }
                    )
                }
            }
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("URL") },
                modifier = Modifier.fillMaxWidth()
            )
            if (method == "POST" || method == "PUT") {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text("Request Body (JSON)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    loading = true
                    scope.launch {
                        result = withContext(Dispatchers.IO) {
                            try {
                                val connection = URL(url).openConnection() as HttpURLConnection
                                connection.requestMethod = method
                                if (method == "POST" || method == "PUT") {
                                    connection.doOutput = true
                                    connection.setRequestProperty("Content-Type", "application/json")
                                    connection.outputStream.write(body.toByteArray())
                                }
                                val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
                                val resBody = stream?.bufferedReader()?.readText() ?: ""
                                "Status: ${connection.responseCode} ${connection.responseMessage}\n\n$resBody"
                            } catch (e: Exception) {
                                "Error: ${e.message}"
                            }
                        }
                        loading = false
                    }
                },
                enabled = url.isNotBlank() && !loading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (loading) "Sending..." else "Send Request")
            }
        }
        item {
            if (result.isNotBlank()) {
                Text("Response:", style = MaterialTheme.typography.titleMedium, color = TextHi)
                Surface(color = Surface, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(result, modifier = Modifier.padding(12.dp), color = TextHi, fontFamily = JetBrainsMono)
                }
            }
        }
    }
}

@Composable
fun JwtDecoderScreen() {
    var jwt by remember { mutableStateOf("") }
    var decoded by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = jwt,
            onValueChange = { jwt = it },
            label = { Text("Paste JWT here") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3
        )
        Button(
            onClick = {
                try {
                    val parts = jwt.trim().split(".")
                    require(parts.size == 3) { "JWT must contain header.payload.signature" }
                    fun decodePart(part: String): String {
                        val padded = part + "=".repeat((4 - part.length % 4) % 4)
                        return String(Base64.decode(padded, Base64.URL_SAFE), StandardCharsets.UTF_8)
                    }
                    decoded = "HEADER:\n${decodePart(parts[0])}\n\nPAYLOAD:\n${decodePart(parts[1])}\n\nSIGNATURE:\n${parts[2]}"
                } catch (e: Exception) {
                    decoded = "Error: Invalid JWT format"
                }
            },
            enabled = jwt.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Decode JWT (Offline)")
        }
        if (decoded.isNotBlank()) {
            Surface(color = Surface, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                Text(decoded, modifier = Modifier.padding(12.dp), color = TextHi, fontFamily = JetBrainsMono)
            }
        }
    }
}

@Composable
fun DnsLookupScreen() {
    var domain by remember { mutableStateOf("google.com") }
    var result by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = domain,
            onValueChange = { domain = it },
            label = { Text("Domain Name") },
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = {
                loading = true
                scope.launch {
                    result = withContext(Dispatchers.IO) {
                        try {
                            val addresses = InetAddress.getAllByName(domain)
                            addresses.joinToString("\n") { "${it.hostName} -> ${it.hostAddress}" }
                        } catch (e: Exception) {
                            "Error: ${e.message}"
                        }
                    }
                    loading = false
                }
            },
            enabled = domain.isNotBlank() && !loading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (loading) "Resolving..." else "DNS Lookup")
        }
        if (result.isNotBlank()) {
            Surface(color = Surface, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                Text(result, modifier = Modifier.padding(12.dp), color = TextHi, fontFamily = JetBrainsMono)
            }
        }
    }
}

@Composable
fun CurlBuilderScreen() {
    var url by remember { mutableStateOf("https://api.example.com/v1/users") }
    var method by remember { mutableStateOf("GET") }
    var headers by remember { mutableStateOf("Authorization: Bearer TOKEN") }
    var body by remember { mutableStateOf("{\"name\": \"DevPulse\"}") }

    val curlCommand = buildString {
        append("curl -X $method '$url'")
        if (headers.isNotBlank()) {
            headers.lines().forEach { line ->
                if (line.isNotBlank()) append(" \\\n  -H '$line'")
            }
        }
        if (method == "POST" || method == "PUT") {
            if (body.isNotBlank()) {
                append(" \\\n  -d '${body}'")
            }
        }
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("GET", "POST", "PUT", "DELETE").forEach { m ->
                    FilterChip(
                        selected = method == m,
                        onClick = { method = m },
                        label = { Text(m) }
                    )
                }
            }
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("URL") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = headers,
                onValueChange = { headers = it },
                label = { Text("Headers (one per line)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )
            if (method == "POST" || method == "PUT") {
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text("Request Body") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
            }
        }
        item {
            Spacer(Modifier.height(8.dp))
            Text("Generated cURL (Offline):", style = MaterialTheme.typography.titleMedium, color = TextHi)
            Surface(color = Surface, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                Text(curlCommand, modifier = Modifier.padding(12.dp), color = TextHi, fontFamily = JetBrainsMono)
            }
        }
    }
}
