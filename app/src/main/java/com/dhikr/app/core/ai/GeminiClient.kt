package com.dhikr.app.core.ai

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

// Flash-Lite: lowest-latency tier, minimal thinking — enough for a short
// fada'il write-up and keeps the non-streaming call well under the timeout.
internal const val GEMINI_MODEL = "gemini-3.5-flash-lite"
private const val GEMINI_BASE =
    "https://generativelanguage.googleapis.com/v1beta/models"
private const val CONNECT_TIMEOUT_MS = 15_000

// Non-streaming generateContent blocks until the model finishes; current flash
// models "think" first, so first byte can land well after 30s.
private const val READ_TIMEOUT_MS = 90_000

/** Outcome of a benefits generation call. */
sealed interface GeminiResult {
    data class Success(val text: String) : GeminiResult
    data class Error(val kind: Kind, val message: String) : GeminiResult

    enum class Kind { NO_KEY, NETWORK, AUTH, RATE_LIMIT, BLOCKED, MALFORMED, UNKNOWN }
}

private val json = Json { ignoreUnknownKeys = true }

// ---- Wire DTOs ----

@Serializable
private data class GeminiRequest(
    val contents: List<Content>,
    val generationConfig: GenerationConfig,
)

@Serializable
private data class Content(val role: String, val parts: List<Part>)

@Serializable
private data class Part(val text: String)

@Serializable
private data class GenerationConfig(
    val temperature: Double,
    val maxOutputTokens: Int,
)

@Serializable
private data class GeminiResponse(
    val candidates: List<Candidate> = emptyList(),
    val promptFeedback: PromptFeedback? = null,
)

@Serializable
private data class Candidate(
    val content: CandidateContent? = null,
    val finishReason: String? = null,
)

@Serializable
private data class CandidateContent(val parts: List<Part> = emptyList())

@Serializable
private data class PromptFeedback(
    @SerialName("blockReason") val blockReason: String? = null,
)

// ---- Pure helpers (unit-tested) ----

internal fun buildRequestBody(prompt: String): String = json.encodeToString(
    GeminiRequest.serializer(),
    GeminiRequest(
        contents = listOf(Content(role = "user", parts = listOf(Part(prompt)))),
        generationConfig = GenerationConfig(temperature = 0.4, maxOutputTokens = 800),
    ),
)

internal fun parseGeminiResponse(httpStatus: Int, body: String): GeminiResult {
    when (httpStatus) {
        in 200..299 -> Unit
        401, 403 -> return GeminiResult.Error(GeminiResult.Kind.AUTH, body.take(300))
        429 -> return GeminiResult.Error(GeminiResult.Kind.RATE_LIMIT, body.take(300))
        else -> return GeminiResult.Error(GeminiResult.Kind.UNKNOWN, "HTTP $httpStatus: ${body.take(300)}")
    }

    val parsed = runCatching { json.decodeFromString(GeminiResponse.serializer(), body) }
        .getOrElse { return GeminiResult.Error(GeminiResult.Kind.MALFORMED, "unparseable response") }

    if (parsed.promptFeedback?.blockReason != null) {
        return GeminiResult.Error(GeminiResult.Kind.BLOCKED, "blocked: ${parsed.promptFeedback.blockReason}")
    }
    val candidate = parsed.candidates.firstOrNull()
        ?: return GeminiResult.Error(GeminiResult.Kind.MALFORMED, "no candidates")
    if (candidate.finishReason == "SAFETY" || candidate.finishReason == "PROHIBITED_CONTENT") {
        return GeminiResult.Error(GeminiResult.Kind.BLOCKED, "finish reason ${candidate.finishReason}")
    }
    val text = candidate.content?.parts?.joinToString("") { it.text }?.trim().orEmpty()
    if (text.isEmpty()) {
        return GeminiResult.Error(GeminiResult.Kind.MALFORMED, "empty text")
    }
    return GeminiResult.Success(text)
}

// ---- Client ----

/** Seam for faking the network call in tests. */
interface GeminiApi {
    suspend fun generateContent(apiKey: String, prompt: String): GeminiResult
}

class GeminiClient : GeminiApi {

    override suspend fun generateContent(apiKey: String, prompt: String): GeminiResult =
        withContext(Dispatchers.IO) {
            val url = URL("$GEMINI_BASE/$GEMINI_MODEL:generateContent?key=$apiKey")
            val conn = url.openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "POST"
                conn.connectTimeout = CONNECT_TIMEOUT_MS
                conn.readTimeout = READ_TIMEOUT_MS
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.outputStream.use { it.write(buildRequestBody(prompt).toByteArray(Charsets.UTF_8)) }

                val status = conn.responseCode
                val stream = if (status in 200..299) conn.inputStream else conn.errorStream
                val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (status !in 200..299) {
                    Log.w("GeminiClient", "HTTP $status from $GEMINI_MODEL: ${body.take(600)}")
                }
                parseGeminiResponse(status, body)
            } catch (e: IOException) {
                Log.w("GeminiClient", "network failure calling $GEMINI_MODEL", e)
                GeminiResult.Error(GeminiResult.Kind.NETWORK, e.message ?: "network error")
            } finally {
                conn.disconnect()
            }
        }
}
