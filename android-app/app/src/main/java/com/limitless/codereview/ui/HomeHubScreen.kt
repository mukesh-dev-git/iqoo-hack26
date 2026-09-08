package com.limitless.codereview.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.limitless.codereview.ui.theme.*

@Composable
fun HomeHubScreen(
    onNavigateToReview: () -> Unit,
    onNavigateToTools: () -> Unit,
    onNavigateToNetwork: () -> Unit,
    onNavigateToMobile: () -> Unit,
    onNavigateToDocs: () -> Unit,
    onNavigateToCollab: () -> Unit,
    onNavigateToInsights: () -> Unit,
    onNavigateToPrivacy: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "DevPulse",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold, fontFamily = JetBrainsMono),
                    color = Accent
                )
                Text(
                    "100% On-Device AI Toolkit",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextHi
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                HomeCard("AI Code Intelligence", "Code review & generation", Icons.Default.Search, onNavigateToReview)
            }
            item {
                HomeCard("Developer Tools", "JSON, JWT, RegEx, Base64", Icons.Default.Settings, onNavigateToTools)
            }
            item {
                HomeCard("Network & API", "REST, cURL, DNS", Icons.Default.Share, onNavigateToNetwork)
            }
            item {
                HomeCard("Mobile Dev Tools", "APK, Layout, ADB", Icons.Default.Phone, onNavigateToMobile)
            }
            item {
                HomeCard("Docs & Reference", "Cheat sheets, Patterns", Icons.Default.Info, onNavigateToDocs)
            }
            item {
                HomeCard("Team Collaboration", "Snippet Vault, P2P", Icons.Default.Person, onNavigateToCollab)
            }
            item {
                HomeCard("Insights", "Usage & Bug Stats", Icons.Default.List, onNavigateToInsights)
            }
            item {
                HomeCard("Privacy & Security", "Zero cloud, Vault", Icons.Default.Lock, onNavigateToPrivacy)
            }
        }
    }
}

@Composable
fun HomeCard(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Accent,
                modifier = Modifier.size(32.dp)
            )
            
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = TextHi
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextLo,
                    maxLines = 2
                )
            }
        }
    }
}
