package com.dhikr.app.core.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.Base64 as JavaBase64

/** `android.util.Base64` is stubbed in unit tests, so the codec is driven with
 *  a real `java.util.Base64`-backed port. getMimeDecoder tolerates whitespace,
 *  matching `android.util.Base64.DEFAULT`. */
private object JavaBase64Port : Base64Port {
    override fun encode(bytes: ByteArray): String = JavaBase64.getEncoder().encodeToString(bytes)
    override fun decode(text: String): ByteArray = JavaBase64.getMimeDecoder().decode(text)
}

class RoutineShareCodecTest {

    private val codec = RoutineShareCodec(JavaBase64Port)

    private fun sample() = RoutineShareFile(
        format = SHARE_FORMAT,
        version = SHARE_VERSION,
        createdAt = 42L,
        appVersionName = "1.0",
        routines = listOf(
            ShareRoutine("Morning", listOf(ShareRoutineStep("subhan", 0, 33))),
        ),
        tasbih = listOf(
            ShareTasbih(id = "c1", name = "Mine", arabic = "a", lapTarget = 10, lapCount = 1),
        ),
    )

    @Test
    fun encodeFile_thenDecode_isEqual() {
        assertEquals(sample(), codec.decode(codec.encodeFile(sample())))
    }

    @Test
    fun encodeText_isSingleLine_withPrefix_andDecodesEqual() {
        val text = codec.encodeText(sample())
        assertTrue(text.startsWith(SHARE_TEXT_PREFIX))
        assertEquals(1, text.lines().size)
        assertEquals(sample(), codec.decode(text))
    }

    @Test
    fun decode_acceptsRawPrettyJson_noPrefix() {
        val pretty = codec.encodeFile(sample())
        assertTrue(pretty.contains("\n"))
        assertEquals(sample(), codec.decode(pretty))
    }

    @Test
    fun decode_rejectsWrongTextPrefix() {
        val body = codec.encodeText(sample()).removePrefix(SHARE_TEXT_PREFIX)
        assertThrows { codec.decode("DHIKR-ROUTINE-v2:$body") }
    }

    @Test
    fun decode_rejectsTruncatedBase64() {
        val text = codec.encodeText(sample())
        assertThrows { codec.decode(text.substring(0, text.length - 6)) }
    }

    @Test
    fun decode_rejectsValidBase64OfNonGzip() {
        val b64 = JavaBase64.getEncoder().encodeToString("not gzip at all".toByteArray())
        assertThrows { codec.decode(SHARE_TEXT_PREFIX + b64) }
    }

    @Test
    fun decode_rejectsEmptyObject() {
        assertThrows { codec.decode("{}") }
    }

    @Test
    fun decode_rejectsBackupFile() {
        assertThrows { codec.decode("""{"format":"dhikr.backup","version":1}""") }
    }

    private fun assertThrows(block: () -> Unit) {
        try {
            block()
            fail("expected ShareFormatException")
        } catch (e: ShareFormatException) {
            // expected
        }
    }
}
