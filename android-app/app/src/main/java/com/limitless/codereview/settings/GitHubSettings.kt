package com.limitless.codereview.settings

import android.content.Context
import androidx.core.content.edit

private const val KEY_GITHUB_TOKEN = "github_token"

/**
 * Persist and read an optional GitHub personal access token. Needed for private repos and to
 * dodge unauthenticated rate limits. Stored in encrypted preferences; an empty
 * string means anonymous (public repos only).
 */
fun saveGitHubToken(context: Context, token: String) {
    secureSettings(context)
        .edit { putString(KEY_GITHUB_TOKEN, token.trim()) }
}

fun loadGitHubToken(context: Context): String =
    secureSettings(context)
        .getString(KEY_GITHUB_TOKEN, "") ?: ""
