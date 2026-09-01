package com.dhikr.app.core.share

import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Serializes / parses the routine-share payload in both delivery forms:
 * a pretty-JSON `.dhikrroutine` file, and a `DHIKR-ROUTINE-v1:` single-line
 * string (base64 of gzip of minified JSON). Pure apart from the injected
 * [Base64Port]. Every failure raises [ShareFormatException] and mutates nothing.
 */
class RoutineShareCodec(private val base64: Base64Port) {

    private val prettyJson = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }
    private val compactJson = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun encodeFile(file: RoutineShareFile): String =
        prettyJson.encodeToString(RoutineShareFile.serializer(), file)

    fun encodeText(file: RoutineShareFile): String {
        val minified = compactJson.encodeToString(RoutineShareFile.serializer(), file)
        return SHARE_TEXT_PREFIX + base64.encode(gzip(minified))
    }

    fun decode(raw: String): RoutineShareFile {
        val text = raw.trim()
        val jsonText = when {
            text.startsWith(SHARE_TEXT_PREFIX) -> gunzip(text.removePrefix(SHARE_TEXT_PREFIX))
            text.startsWith("DHIKR-ROUTINE-") -> throw ShareFormatException(MSG_NOT_OURS)
            else -> text
        }
        val file = try {
            compactJson.decodeFromString(RoutineShareFile.serializer(), jsonText)
        } catch (e: Exception) {
            throw ShareFormatException(MSG_NOT_OURS)
        }
        if (file.format != SHARE_FORMAT) throw ShareFormatException(MSG_NOT_OURS)
        if (file.version > SHARE_VERSION) throw ShareFormatException(MSG_NEWER)
        return file
    }

    private fun gzip(s: String): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(s.toByteArray(Charsets.UTF_8)) }
        return out.toByteArray()
    }

    private fun gunzip(b64: String): String {
        val bytes = try {
            base64.decode(b64)
        } catch (e: Exception) {
            throw ShareFormatException(MSG_NOT_OURS)
        }
        return try {
            GZIPInputStream(ByteArrayInputStream(bytes)).use {
                it.readBytes().toString(Charsets.UTF_8)
            }
        } catch (e: Exception) {
            throw ShareFormatException(MSG_NOT_OURS)
        }
    }
}
