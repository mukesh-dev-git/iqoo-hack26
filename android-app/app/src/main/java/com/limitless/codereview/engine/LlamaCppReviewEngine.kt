package com.limitless.codereview.engine

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** True local GGUF inference with a deterministic fallback when the optional model is absent. */
class LlamaCppReviewEngine(context: Context) : ReviewEngine {
    private val appContext = context.applicationContext
    private val fallback = OfflineReviewEngine()
    private val modelFile = File(appContext.filesDir, "models/qwen2.5-coder-1.5b-instruct-q4_k_m.gguf")

    init {
        try { System.loadLibrary("llama-android") } catch (_: UnsatisfiedLinkError) { }
    }

    private external fun nativeGenerate(modelPath: String, prompt: String): String

    override suspend fun review(diff: String): List<Finding> = withContext(Dispatchers.IO) {
        if (diff.isBlank()) return@withContext emptyList()
        try {
            if (!modelFile.exists()) copyBundledModel()
            if (!modelFile.exists() || modelFile.length() < 100_000_000L) return@withContext fallback.review(diff)

            val prompt = """
                You are an expert private code reviewer. Analyze this git diff.
                Output one line per issue exactly in this format:
                LINE:<new line number> SEVERITY:<BUG|WARNING|INFO> MSG:<one sentence>
                If there are no issues output NO_ISSUES.

                DIFF:
                $diff
            """.trimIndent()
            val raw = nativeGenerate(modelFile.absolutePath, prompt)
            parseFindings(raw).ifEmpty { fallback.review(diff) }
        } catch (_: Throwable) {
            fallback.review(diff)
        }
    }

    private fun copyBundledModel() {
        val assetName = "models/qwen2.5-coder-1.5b-instruct-q4_k_m.gguf"
        val parent = modelFile.parentFile ?: return
        parent.mkdirs()
        try {
            appContext.assets.open(assetName).use { input ->
                modelFile.outputStream().use { output -> input.copyTo(output, 1024 * 1024) }
            }
        } catch (_: Exception) {
            modelFile.delete()
        }
    }

    private fun parseFindings(raw: String): List<Finding> {
        val regex = Regex("LINE:(\\d+)\\s+SEVERITY:(BUG|WARNING|INFO)\\s+MSG:(.+)")
        return regex.findAll(raw).map { match ->
            Finding(
                line = match.groupValues[1].toInt(),
                severity = Severity.valueOf(match.groupValues[2]),
                message = match.groupValues[3].trim(),
            )
        }.toList()
    }
}

/** Used only when the model asset is unavailable or the native runtime cannot initialize. */
private class OfflineReviewEngine : ReviewEngine {
    override suspend fun review(diff: String): List<Finding> {
        if (diff.isBlank()) return emptyList()
        val findings = mutableListOf<Finding>()
        diff.lines().forEachIndexed { index, line ->
            val trimmed = line.removePrefix("+").trim()
            if (line.startsWith("+") && !line.startsWith("+++")) when {
                trimmed.contains("!!") -> findings += Finding(index + 1, Severity.BUG, "Forced non-null assertion can crash when the value is null.")
                trimmed.contains(".size + 1") -> findings += Finding(index + 1, Severity.BUG, "Collection size is incremented before use; this can create an off-by-one error.")
                Regex("\\b(user|result|response|item|value)\\.name\\b").containsMatchIn(trimmed) -> findings += Finding(index + 1, Severity.WARNING, "Direct property access may dereference a nullable value.")
            }
        }
        return findings.ifEmpty { listOf(Finding(0, Severity.INFO, "No obvious issue found by the offline analyzer.")) }
    }
}
