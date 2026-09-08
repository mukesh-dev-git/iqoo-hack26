package com.limitless.codereview.github

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * A parsed reference to a specific GitHub pull request.
 */
data class PullRequestRef(
    val owner: String,
    val repo: String,
    val number: Int,
) {
    override fun toString(): String = "$owner/$repo#$number"
}

/**
 * Pulls a GitHub pull-request reference out of arbitrary shared text. The share target for a
 * PR from the GitHub app / Chrome is usually just the URL, but sometimes the description is
 * prefixed or suffixed, so we search for the pattern rather than requiring an exact match.
 */
object GitHubLinks {

    // https://github.com/owner/repo/pull/123 (optionally with scheme/www, or extra path/query)
    private val PR_URL = Regex(
        """(?:https?://)?(?:www\.)?github\.com/([A-Za-z0-9_.-]+)/([A-Za-z0-9_.-]+)/pull/(\d+)""",
        RegexOption.IGNORE_CASE,
    )

    fun parsePrUrl(text: String): PullRequestRef? {
        val m = PR_URL.find(text) ?: return null
        return PullRequestRef(
            m.groupValues[1],
            m.groupValues[2],
            m.groupValues[3].toInt(),
        )
    }

    fun isPrUrl(text: String): Boolean = PR_URL.containsMatchIn(text)
}

/**
 * Fetches unified diffs straight from the GitHub API. The privacy line is that the diff is
 * *downloaded* but never sent to an AI cloud — it only ever feeds the local ReviewEngine.
 * See docs/GITHUB_INTEGRATION_DESIGN.md for the full rationale.
 *
 * A personal access token is optional: public repos work anonymously, but private repos and
 * unauthenticated rate limits need `Authorization: Bearer <token>`.
 */
class GitHubClient(
    private val token: String? = null,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Returns the raw unified diff for a PR. GitHub returns it directly when we ask for the
     * `application/vnd.github.v3.diff` content type — the exact shape ReviewEngine.review()
     * already expects.
     *
     * @throws IOException if the request fails (network error, 404, auth/rate-limit, ...).
     */
    fun fetchPrDiff(ref: PullRequestRef): String {
        val url = "https://api.github.com/repos/${ref.owner}/${ref.repo}/pulls/${ref.number}"
        val builder = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github.v3.diff")
            .header("User-Agent", "iqoo-codereview")
        if (!token.isNullOrBlank()) {
            builder.header("Authorization", "Bearer $token")
        }

        client.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException(
                    "GitHub returned HTTP ${response.code}. " +
                        "If this repo is private or you hit a rate limit, add a Personal Access " +
                        "Token in ⚙ → GitHub token."
                )
            }
            return response.body?.string().orEmpty()
        }
    }
}
