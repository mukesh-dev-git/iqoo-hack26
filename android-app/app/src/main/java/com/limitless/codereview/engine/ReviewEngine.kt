package com.limitless.codereview.engine

/**
 * The one interface every workstream builds against. See /CONTRACT.md at the repo root
 * for the full spec (including the laptop-bridge wire format).
 *
 * Do not change this file's shape without updating CONTRACT.md and pinging the team —
 * everyone's code depends on this staying stable.
 */
interface ReviewEngine {
    suspend fun review(diff: String): List<Finding>
}

data class Finding(
    val line: Int,
    val severity: Severity,
    val message: String
)

enum class Severity { INFO, WARNING, BUG }
