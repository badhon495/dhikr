package com.dhikr.app.core.share

import android.util.Base64

/** Base64 codec seam. The real app uses [AndroidBase64]; the codec's unit tests
 *  inject a `java.util.Base64`-backed double so no `android.util.*` is touched. */
interface Base64Port {
    fun encode(bytes: ByteArray): String
    fun decode(text: String): ByteArray
}

/** `NO_WRAP` on encode (single-line output for the text-share form); `DEFAULT`
 *  on decode (tolerates line wrapping a chat app may have inserted). */
object AndroidBase64 : Base64Port {
    override fun encode(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)
    override fun decode(text: String): ByteArray = Base64.decode(text, Base64.DEFAULT)
}
