package com.limitless.codereview.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Calls the FastAPI server in /laptop-bridge over HTTP on shared Wi-Fi.
 */
class LaptopBridgeReviewEngine(
    private val laptopBaseUrl: String = "http://192.168.1.100:8000"
) : ReviewEngine {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class ReviewRequest(val diff: String)

    @Serializable
    private data class ReviewResponse(val findings: List<WireFinding>)

    @Serializable
    private data class WireFinding(val line: Int, val severity: String, val message: String)

    override suspend fun review(diff: String): List<Finding> = withContext(Dispatchers.IO) {
        val cleanUrl = laptopBaseUrl.trim().trimEnd('/')
        val body = json.encodeToString(ReviewRequest.serializer(), ReviewRequest(diff))
            .toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("$cleanUrl/review")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: ""
                throw IOException("Server returned HTTP ${response.code}: $errorBody")
            }
            val text = response.body?.string() ?: "{\"findings\":[]}"
            val parsed = json.decodeFromString(ReviewResponse.serializer(), text)
            parsed.findings.map {
                val sev = try {
                    Severity.valueOf(it.severity.uppercase())
                } catch (e: Exception) {
                    Severity.WARNING
                }
                Finding(it.line, sev, it.message)
            }
        }
    }
}
