package com.dhikr.app.core.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiResponseParsingTest {

    private val okBody = """
        {
          "candidates": [
            { "content": { "parts": [ { "text": "• First virtue\n• Second virtue" } ] },
              "finishReason": "STOP" }
          ]
        }
    """.trimIndent()

    private val safetyFinishBody = """
        {
          "candidates": [ { "finishReason": "SAFETY", "content": { "parts": [] } } ]
        }
    """.trimIndent()

    private val promptBlockedBody = """
        { "promptFeedback": { "blockReason": "SAFETY" } }
    """.trimIndent()

    private val authErrorBody = """
        { "error": { "code": 403, "message": "API key not valid", "status": "PERMISSION_DENIED" } }
    """.trimIndent()

    @Test
    fun success_extracts_joined_text() {
        val r = parseGeminiResponse(200, okBody)
        assertTrue(r is GeminiResult.Success)
        assertEquals("• First virtue\n• Second virtue", (r as GeminiResult.Success).text)
    }

    @Test
    fun safety_finish_reason_maps_to_blocked() {
        val r = parseGeminiResponse(200, safetyFinishBody)
        assertEquals(GeminiResult.Kind.BLOCKED, (r as GeminiResult.Error).kind)
    }

    @Test
    fun prompt_block_reason_maps_to_blocked() {
        val r = parseGeminiResponse(200, promptBlockedBody)
        assertEquals(GeminiResult.Kind.BLOCKED, (r as GeminiResult.Error).kind)
    }

    @Test
    fun http_401_maps_to_auth() {
        val r = parseGeminiResponse(401, authErrorBody)
        assertEquals(GeminiResult.Kind.AUTH, (r as GeminiResult.Error).kind)
    }

    @Test
    fun http_403_maps_to_auth() {
        val r = parseGeminiResponse(403, authErrorBody)
        assertEquals(GeminiResult.Kind.AUTH, (r as GeminiResult.Error).kind)
    }

    @Test
    fun http_429_maps_to_rate_limit() {
        val r = parseGeminiResponse(429, "{}")
        assertEquals(GeminiResult.Kind.RATE_LIMIT, (r as GeminiResult.Error).kind)
    }

    @Test
    fun http_500_maps_to_unknown() {
        val r = parseGeminiResponse(500, "{}")
        assertEquals(GeminiResult.Kind.UNKNOWN, (r as GeminiResult.Error).kind)
    }

    @Test
    fun garbage_200_body_maps_to_malformed() {
        val r = parseGeminiResponse(200, "not json at all")
        assertEquals(GeminiResult.Kind.MALFORMED, (r as GeminiResult.Error).kind)
    }

    @Test
    fun ok_200_with_no_text_part_maps_to_malformed() {
        val r = parseGeminiResponse(200, """{ "candidates": [ { "content": { "parts": [] }, "finishReason": "STOP" } ] }""")
        assertEquals(GeminiResult.Kind.MALFORMED, (r as GeminiResult.Error).kind)
    }

    @Test
    fun request_body_contains_prompt_and_generation_config() {
        val body = buildRequestBody("Explain SubhanAllah")
        assertTrue(body.contains("Explain SubhanAllah"))
        assertTrue(body.contains("temperature"))
        assertTrue(body.contains("maxOutputTokens"))
    }
}
