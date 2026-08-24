package com.limitless.codereview.engine

import kotlinx.coroutines.delay

/**
 * Returns canned findings after a fake delay, so the UI and voice-trigger flow can be built
 * and demoed end-to-end before LlamaCppReviewEngine is wired up. This is what MainActivity
 * uses by default — swap in LlamaCppReviewEngine once it's ready (see NOTES_LLAMACPP.md).
 */
class StubReviewEngine : ReviewEngine {
    override suspend fun review(diff: String): List<Finding> {
        delay(600) // simulate inference latency
        if (diff.isBlank()) return emptyList()

        return listOf(
            Finding(1, Severity.INFO, "Stub engine active — swap in LlamaCppReviewEngine for real review."),
            Finding(3, Severity.WARNING, "Example finding: unused variable."),
            Finding(7, Severity.BUG, "Example finding: possible null pointer dereference.")
        )
    }
}
