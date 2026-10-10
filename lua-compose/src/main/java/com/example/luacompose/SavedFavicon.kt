package com.example.luacompose

import okio.ByteString.Companion.decodeBase64

internal object SavedFavicon {
    fun decode(value: String): ByteArray? = runCatching {
        if (value.length > 43692) return null
        val bytes = value.decodeBase64()?.toByteArray() ?: return null
        image(bytes)?.takeIf { it.contentEquals(bytes) }
    }.getOrNull()

    fun image(bytes: ByteArray): ByteArray? {
        if (bytes.isEmpty() || bytes.size > 32768) return null
        val png = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)
        if (bytes.take(8).toByteArray().contentEquals(png)) return bytes
        if (bytes.size >= 3 && bytes[0] == (-1).toByte() && bytes[1] == (-40).toByte() && bytes[2] == (-1).toByte()) return bytes
        if (bytes.size >= 12 && bytes.copyOfRange(0, 4).toString(Charsets.US_ASCII) == "RIFF" && bytes.copyOfRange(8, 12).toString(Charsets.US_ASCII) == "WEBP") return bytes
        // shortcut: ICO supports embedded PNG only; add a DIB decoder when legacy icons are needed.
        if (bytes.size < 6 || !bytes.take(4).toByteArray().contentEquals(byteArrayOf(0, 0, 1, 0))) return null
        val buffer = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        val count = buffer.getShort(4).toInt() and 65535
        if (count > (bytes.size - 6) / 16) return null
        for (index in 0 until count) {
            val size = buffer.getInt(6 + index * 16 + 8)
            val offset = buffer.getInt(6 + index * 16 + 12)
            if (size >= 8 && offset >= 6 + count * 16 && offset <= bytes.size - size && bytes.copyOfRange(offset, offset + 8).contentEquals(png)) {
                return bytes.copyOfRange(offset, offset + size)
            }
        }
        return null
    }
}
