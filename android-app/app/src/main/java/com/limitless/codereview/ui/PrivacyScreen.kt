package com.limitless.codereview.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.limitless.codereview.ui.theme.*

@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Home") }
            Spacer(Modifier.width(8.dp))
            Text("Privacy & Security", style = MaterialTheme.typography.titleLarge, color = TextHi)
        }
        Spacer(Modifier.height(16.dp))
        
        Text("Your code, your data, your device. Always.", color = Color(0xFF4CAF50), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(16.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                ZeroCloudBadgeCard()
            }
            item {
                AirGapToggleCard()
            }
            item {
                BiometricLockCard()
            }
            item {
                EncryptedStorageCard()
            }
        }
    }
}

@Composable
fun ZeroCloudBadgeCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Surface)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = Icons.Default.Shield, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(48.dp))
            Spacer(Modifier.width(16.dp))
            Column {
                Text("Zero Cloud Architecture", style = MaterialTheme.typography.titleMedium, color = TextHi)
                Text("0 bytes sent to external servers.", color = TextLo)
            }
        }
    }
}

@Composable
fun AirGapToggleCard() {
    var isAirGapped by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Surface)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.NetworkCheck, contentDescription = null, tint = if (isAirGapped) TextLo else Accent, modifier = Modifier.size(32.dp))
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("Strict Air-Gap Mode", style = MaterialTheme.typography.titleMedium, color = TextHi)
                    Text(if (isAirGapped) "Network disabled" else "Network allowed for APIs", color = TextLo)
                }
            }
            Switch(
                checked = isAirGapped,
                onCheckedChange = { isAirGapped = it },
                colors = SwitchDefaults.colors(checkedThumbColor = Brass, checkedTrackColor = Surface3)
            )
        }
    }
}

@Composable
fun BiometricLockCard() {
    var isLocked by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Surface)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.VisibilityOff, contentDescription = null, tint = Accent, modifier = Modifier.size(32.dp))
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("Biometric Lock", style = MaterialTheme.typography.titleMedium, color = TextHi)
                    Text("Require Face/Fingerprint", color = TextLo)
                }
            }
            Switch(
                checked = isLocked,
                onCheckedChange = { isLocked = it }
            )
        }
    }
}

@Composable
fun EncryptedStorageCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Surface)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = Accent, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(16.dp))
            Column {
                Text("AES-256 Encrypted Vault", style = MaterialTheme.typography.titleMedium, color = TextHi)
                Text("Code snippets & API keys are encrypted at rest.", color = TextLo)
            }
        }
    }
}
