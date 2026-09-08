package com.limitless.codereview.settings

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.edit

private const val KEY_LAPTOP_URL = "laptop_base_url"
const val DEFAULT_LAPTOP_URL = "http://192.168.1.100:8000"

/** Persist and read the laptop bridge base URL via encrypted preferences. */
fun saveLaptopUrl(context: Context, url: String) {
    secureSettings(context)
        .edit { putString(KEY_LAPTOP_URL, url.trimEnd('/')) }
}

fun loadLaptopUrl(context: Context): String =
    secureSettings(context)
        .getString(KEY_LAPTOP_URL, DEFAULT_LAPTOP_URL) ?: DEFAULT_LAPTOP_URL

/**
 * Bottom-sheet style settings panel — tap the ⚙ button in MainActivity to open.
 *
 * Two sections, each saved independently:
 *  1. Laptop bridge URL — the LAN address of laptop-bridge, so we don't need to recompile for
 *     every demo (`ipconfig` on the laptop → http://192.168.X.Y:8000).
 *  2. GitHub token (optional) — needed for private repos and to dodge unauthenticated rate
 *     limits when reviewing a shared PR link. Leave blank for public repos.
 *
 * Cancel dismisses without saving either field.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LaptopSettingsSheet(
    currentUrl: String,
    currentToken: String,
    onSaveUrl: (String) -> Unit,
    onSaveToken: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var urlDraft by remember(currentUrl) { mutableStateOf(currentUrl) }
    val urlIsValid = urlDraft.startsWith("http://") || urlDraft.startsWith("https://")

    var tokenDraft by remember(currentToken) { mutableStateOf(currentToken) }
    val tokenIsValid = tokenDraft.isBlank() || !tokenDraft.contains(' ')

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Settings", style = MaterialTheme.typography.titleMedium)

            // --- Laptop bridge ---
            Text(
                "Laptop Bridge (deeper review tier)",
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                "Enter your laptop's local IP. Run `ipconfig` on the laptop and look for " +
                "the IPv4 address on your shared Wi-Fi adapter.",
                style = MaterialTheme.typography.bodySmall,
            )

            OutlinedTextField(
                value = urlDraft,
                onValueChange = { urlDraft = it },
                label = { Text("Laptop URL") },
                placeholder = { Text("http://192.168.1.42:8000") },
                isError = urlDraft.isNotBlank() && !urlIsValid,
                supportingText = {
                    if (urlDraft.isNotBlank() && !urlIsValid)
                        Text("Must start with http:// or https://")
                    else
                        Text("Format: http://<laptop-ip>:8000")
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                Button(
                    onClick = { onSaveUrl(urlDraft) },
                    enabled = urlIsValid && urlDraft.isNotBlank(),
                ) { Text("Save URL") }
            }

            HorizontalDivider()

            // --- GitHub token ---
            Text(
                "GitHub (optional)",
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                "Personal access token for reviewing shared PR links. Only needed for private " +
                "repos or to avoid unauthenticated rate limits — public repos work without one.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
                value = tokenDraft,
                onValueChange = { tokenDraft = it },
                label = { Text("GitHub token") },
                placeholder = { Text("ghp_…") },
                isError = !tokenIsValid,
                supportingText = {
                    if (!tokenIsValid) Text("Tokens can't contain spaces")
                    else Text("Leave blank to stay anonymous")
                },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                Button(
                    onClick = { onSaveToken(tokenDraft) },
                    enabled = tokenIsValid,
                ) { Text("Save token") }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        }
    }
}
