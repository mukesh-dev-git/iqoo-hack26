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

/**
 * TODO (Nambert): this is the "escalation to laptop" tier. It calls the FastAPI server in
 * /laptop-bridge over plain HTTP on shared Wi-Fi — see /CONTRACT.md for the exact wire format.
 * This stands in for the real Office Kit transport until that SDK is available at the event;
 * swapping the transport later should not require changing this class's public shape.
 *
 * TODO: replace `laptopBaseUrl` with a settings field / QR-code pairing instead of hardcoding
 * an IP, once you know how you want pairing to work for the demo.
 */
class LaptopBridgeReviewEngine(
    private val laptopBaseUrl: String = "http://192.168.1.100:8000"
) : ReviewEngine {

    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class ReviewRequest(val diff: String)

    @Serializable
    private data class ReviewResponse(val findings: List<WireFinding>)

    @Serializable
    private data class WireFinding(val line: Int, val severity: String, val message: String)

    override suspend fun review(diff: String): List<Finding> = withContext(Dispatchers.IO) {
        val body = json.encodeToString(ReviewRequest.serializer(), ReviewRequest(diff))
            .toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("$laptopBaseUrl/review")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Laptop bridge returned ${response.code}")
            }
            val text = response.body?.string() ?: "{\"findings\":[]}"
            val parsed = json.decodeFromString(ReviewResponse.serializer(), text)
            parsed.findings.map {
                Finding(it.line, Severity.valueOf(it.severity), it.message)
            }
        }
    }
}
