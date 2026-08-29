package com.limitless.codereview.settings

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.edit

private const val PREFS_NAME = "codereview_settings"
private const val KEY_LAPTOP_URL = "laptop_base_url"
const val DEFAULT_LAPTOP_URL = "http://192.168.1.100:8000"

/** Persist and read the laptop bridge base URL via SharedPreferences. */
fun saveLaptopUrl(context: Context, url: String) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit { putString(KEY_LAPTOP_URL, url.trimEnd('/')) }
}

fun loadLaptopUrl(context: Context): String =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(KEY_LAPTOP_URL, DEFAULT_LAPTOP_URL) ?: DEFAULT_LAPTOP_URL

/**
 * Bottom-sheet style settings panel — tap the ⚙ button in MainActivity to open.
 * Lets the user enter the laptop's LAN IP so we don't need to recompile for every demo.
 *
 * Typical value: http://192.168.X.Y:8000  (find X.Y via `ipconfig` on the laptop)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LaptopSettingsSheet(
    currentUrl: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember(currentUrl) { mutableStateOf(currentUrl) }
    val isValid = draft.startsWith("http://") || draft.startsWith("https://")

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Laptop Bridge Settings", style = MaterialTheme.typography.titleMedium)

            Text(
                "Enter your laptop's local IP. Run `ipconfig` on the laptop and look for " +
                "the IPv4 address on your shared Wi-Fi adapter.",
                style = MaterialTheme.typography.bodySmall,
            )

            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                label = { Text("Laptop URL") },
                placeholder = { Text("http://192.168.1.42:8000") },
                isError = draft.isNotBlank() && !isValid,
                supportingText = {
                    if (draft.isNotBlank() && !isValid)
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
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Button(
                    onClick = { onSave(draft) },
                    enabled = isValid,
                ) { Text("Save") }
            }
        }
    }
}
