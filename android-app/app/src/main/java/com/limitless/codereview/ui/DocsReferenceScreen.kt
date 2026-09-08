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
fun DocsReferenceScreen(onBack: () -> Unit) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Cheat Sheets", "Design Patterns", "Git Helper", "Error Codes", "Algo Visualizer", "Interview Prep")

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Home") }
            Spacer(Modifier.width(8.dp))
            Text("Docs & Reference", style = MaterialTheme.typography.titleLarge, color = TextHi)
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
            0 -> CheatSheetsScreen()
            1 -> DesignPatternsScreen()
            2 -> GitHelperScreen()
            3 -> ErrorCodesScreen()
            4 -> AlgoVisualizerScreen()
            5 -> InterviewPrepScreen()
        }
    }
}

@Composable
fun CheatSheetsScreen() {
    val sheets = listOf("Git", "SQL", "Regex", "Vim", "Docker", "Kotlin", "Python")
    var selectedSheet by remember { mutableStateOf(sheets.first()) }
    
    val content = when (selectedSheet) {
        "Git" -> "git init\ngit clone <url>\ngit add .\ngit commit -m \"msg\"\ngit push origin main"
        "SQL" -> "SELECT * FROM table\nWHERE condition\nORDER BY column DESC\nLIMIT 10"
        "Regex" -> "^ - start of string\n$ - end of string\n. - any character\n* - 0 or more"
        "Vim" -> "i - insert mode\nEsc - command mode\n:w - save\n:q - quit"
        "Docker" -> "docker build -t name .\ndocker run -p 8080:8080 name\ndocker ps"
        "Kotlin" -> "val - read-only\nvar - mutable\nfun name() {}\ndata class User(val name: String)"
        "Python" -> "def func():\n  pass\n\nlist = [1, 2, 3]\ndict = {'key': 'value'}"
        else -> ""
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScrollableTabRow(
            selectedTabIndex = sheets.indexOf(selectedSheet),
            containerColor = Surface,
            contentColor = Accent,
            edgePadding = 0.dp
        ) {
            sheets.forEach { sheet ->
                Tab(
                    selected = selectedSheet == sheet,
                    onClick = { selectedSheet = sheet },
                    text = { Text(sheet) }
                )
            }
        }
        
        Surface(color = Surface, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().weight(1f)) {
            Text(content, modifier = Modifier.padding(16.dp), color = TextHi, fontFamily = JetBrainsMono)
        }
    }
}

@Composable
fun DesignPatternsScreen() {
    val patterns = listOf(
        "Singleton" to "Ensure a class only has one instance, and provide a global point of access to it.\n\nobject NetworkClient {\n  fun get() {}\n}",
        "Observer" to "Define a one-to-many dependency between objects so that when one object changes state, all its dependents are notified and updated automatically.",
        "Factory Method" to "Define an interface for creating an object, but let subclasses decide which class to instantiate.",
        "Builder" to "Separate the construction of a complex object from its representation so that the same construction process can create different representations."
    )
    
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(patterns.size) { index ->
            val (name, desc) = patterns[index]
            Surface(color = Surface, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(name, style = MaterialTheme.typography.titleMedium, color = Accent)
                    Spacer(Modifier.height(8.dp))
                    Text(desc, style = MaterialTheme.typography.bodyMedium, color = TextHi, fontFamily = JetBrainsMono)
                }
            }
        }
    }
}

@Composable
fun GitHelperScreen() {
    var intent by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = intent,
            onValueChange = { intent = it },
            label = { Text("What do you want to do?") },
            placeholder = { Text("e.g. undo last commit, discard changes") },
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = {
                result = when {
                    intent.contains("undo", ignoreCase = true) -> "git reset --soft HEAD~1"
                    intent.contains("discard", ignoreCase = true) -> "git restore .\ngit clean -fd"
                    intent.contains("branch", ignoreCase = true) -> "git checkout -b <new-branch>"
                    intent.contains("stash", ignoreCase = true) -> "git stash\ngit stash pop"
                    else -> "git help"
                }
            },
            enabled = intent.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Find Command")
        }
        
        if (result.isNotBlank()) {
            Surface(color = Surface, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                Text(result, modifier = Modifier.padding(12.dp), color = TextHi, fontFamily = JetBrainsMono)
            }
        }
    }
}

@Composable
fun ErrorCodesScreen() {
    var code by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }

    val errorMap = mapOf(
        "400" to "Bad Request - The server could not understand the request due to invalid syntax.",
        "401" to "Unauthorized - Authentication is required and has failed or has not yet been provided.",
        "403" to "Forbidden - The client does not have access rights to the content.",
        "404" to "Not Found - The server can not find the requested resource.",
        "500" to "Internal Server Error - The server has encountered a situation it doesn't know how to handle.",
        "502" to "Bad Gateway - The server, while acting as a gateway or proxy, received an invalid response.",
        "503" to "Service Unavailable - The server is not ready to handle the request."
    )

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = code,
            onValueChange = { 
                code = it
                result = errorMap[it] ?: if (it.isNotBlank()) "Unknown code. Try 400, 404, 500, etc." else ""
            },
            label = { Text("Enter HTTP Status or Error Code") },
            modifier = Modifier.fillMaxWidth()
        )
        
        if (result.isNotBlank()) {
            Surface(color = Surface, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                Text(result, modifier = Modifier.padding(12.dp), color = TextHi)
            }
        }
    }
}

@Composable
fun AlgoVisualizerScreen() {
    val algos = listOf("Bubble Sort", "Binary Search", "BFS", "DFS")
    var selectedAlgo by remember { mutableStateOf(algos.first()) }
    
    val content = when (selectedAlgo) {
        "Bubble Sort" -> "1. Compare adjacent elements\n2. Swap if out of order\n3. Repeat until no swaps needed\n\nO(n^2) time complexity"
        "Binary Search" -> "1. Find middle element\n2. If target == mid, return\n3. If target < mid, search left half\n4. If target > mid, search right half\n\nO(log n) time complexity (Array must be sorted)"
        "BFS" -> "1. Enqueue starting node\n2. While queue not empty:\n   a. Dequeue node\n   b. Visit node\n   c. Enqueue all unvisited neighbors\n\nUses a Queue (FIFO)"
        "DFS" -> "1. Push starting node\n2. While stack not empty:\n   a. Pop node\n   b. Visit node\n   c. Push all unvisited neighbors\n\nUses a Stack (LIFO)"
        else -> ""
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScrollableTabRow(
            selectedTabIndex = algos.indexOf(selectedAlgo),
            containerColor = Surface,
            contentColor = Accent,
            edgePadding = 0.dp
        ) {
            algos.forEach { algo ->
                Tab(
                    selected = selectedAlgo == algo,
                    onClick = { selectedAlgo = algo },
                    text = { Text(algo) }
                )
            }
        }
        
        Surface(color = Surface, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().weight(1f)) {
            Text(content, modifier = Modifier.padding(16.dp), color = TextHi, fontFamily = JetBrainsMono)
        }
    }
}

@Composable
fun InterviewPrepScreen() {
    val questions = listOf(
        "Two Sum" to "Given an array of integers nums and an integer target, return indices of the two numbers such that they add up to target.\n\nHint: Use a Hash Map to store seen values and their indices to achieve O(n) time.",
        "Reverse Linked List" to "Given the head of a singly linked list, reverse the list, and return the reversed list.\n\nHint: Maintain prev, current, and next pointers. Iterate through, redirecting current.next to prev.",
        "Valid Parentheses" to "Given a string s containing just the characters '(', ')', '{', '}', '[' and ']', determine if the input string is valid.\n\nHint: Use a stack. Push opening brackets, pop and check for matching closing brackets."
    )
    
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(questions.size) { index ->
            var showHint by remember { mutableStateOf(false) }
            val (q, a) = questions[index]
            
            Surface(color = Surface, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(q, style = MaterialTheme.typography.titleMedium, color = Accent)
                    Spacer(Modifier.height(8.dp))
                    if (showHint) {
                        Text(a, style = MaterialTheme.typography.bodyMedium, color = TextHi)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (showHint) "Hide Hint" else "Reveal Hint",
                        color = Brass,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.clickable { showHint = !showHint }.padding(vertical = 4.dp)
                    )
                }
            }
        }
    }
}
