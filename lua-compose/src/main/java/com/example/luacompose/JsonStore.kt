package com.example.luacompose

import android.util.AtomicFile
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import kotlinx.serialization.json.Json

class JsonStore(
    read: () -> ByteArray?,
    write: (ByteArray) -> Unit,
    delete: () -> Unit,
) {
    private val readBytes = read
    private val writeBytes = write
    private val deleteBytes = delete
    fun create(json: String) = synchronized(lock) {
        existing()?.let { fail(JsonStoreError.ALREADY_EXISTS) }
        persist(input(json))
    }

    fun read(): String? = synchronized(lock) { existing()?.second }

    fun update(json: String) = synchronized(lock) {
        existing() ?: fail(JsonStoreError.NOT_FOUND)
        persist(input(json))
    }

    fun delete() = synchronized(lock) {
        if (existing() == null) return@synchronized
        try {
            deleteBytes()
        } catch (_: Exception) {
            fail(JsonStoreError.UNAVAILABLE)
        }
    }

    private fun existing(): Pair<ByteArray, String>? {
        val bytes = try {
            readBytes()
        } catch (failure: JsonStoreException) {
            throw failure
        } catch (_: Exception) {
            fail(JsonStoreError.UNAVAILABLE)
        } ?: return null
        if (bytes.size > MAX_DOCUMENT_BYTES) fail(JsonStoreError.INVALID_DATA)
        val text = decode(bytes)
        validate(text, JsonStoreError.INVALID_DATA)
        return bytes to text
    }

    private fun persist(bytes: ByteArray) {
        try {
            writeBytes(bytes)
        } catch (failure: JsonStoreException) {
            throw failure
        } catch (_: Exception) {
            fail(JsonStoreError.UNAVAILABLE)
        }
    }

    private fun input(json: String): ByteArray {
        val bytes = encode(json)
        if (bytes.size > MAX_DOCUMENT_BYTES) fail(JsonStoreError.QUOTA_EXCEEDED)
        validate(json, JsonStoreError.INVALID_JSON)
        return bytes
    }

    companion object {
        const val MAX_DOCUMENT_BYTES = 256 * 1024
        private val lock = Any()

        private fun validate(json: String, error: JsonStoreError) {
            try {
                Json.parseToJsonElement(json)
            } catch (_: Exception) {
                fail(error)
            }
        }

        private fun encode(value: String): ByteArray = try {
            val buffer = StandardCharsets.UTF_8.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .encode(java.nio.CharBuffer.wrap(value))
            ByteArray(buffer.remaining()).also(buffer::get)
        } catch (_: Exception) {
            fail(JsonStoreError.INVALID_JSON)
        }

        private fun decode(bytes: ByteArray): String = try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: Exception) {
            fail(JsonStoreError.INVALID_DATA)
        }
    }
}

internal enum class JsonStoreError {
    ALREADY_EXISTS,
    NOT_FOUND,
    INVALID_JSON,
    INVALID_DATA,
    QUOTA_EXCEEDED,
    UNAVAILABLE,
}

internal class JsonStoreException(val error: JsonStoreError) : RuntimeException(error.name)

private fun fail(error: JsonStoreError): Nothing = throw JsonStoreException(error)

fun persistentJsonStore(file: File): JsonStore {
    val atomic = AtomicFile(file)
    return JsonStore(
        read = {
            val backup = File(file.path + ".bak")
            if (!file.exists() && !backup.exists()) null
            else atomic.openRead().use { it.readBounded(JsonStore.MAX_DOCUMENT_BYTES) }
        },
        write = { bytes ->
            file.parentFile?.let { directory ->
                if (!directory.exists() && !directory.mkdirs() && !directory.isDirectory) {
                    throw java.io.IOException("Storage unavailable")
                }
            }
            var output: FileOutputStream? = null
            try {
                output = atomic.startWrite()
                output.write(bytes)
                output.flush()
                output.fd.sync()
                atomic.finishWrite(output)
                output = null
                if (!bytes.contentEquals(atomic.openRead().use { it.readBounded(JsonStore.MAX_DOCUMENT_BYTES) })) {
                    throw java.io.IOException("Storage unavailable")
                }
            } catch (failure: Exception) {
                output?.let(atomic::failWrite)
                throw failure
            }
        },
        delete = atomic::delete,
    )
}

private fun java.io.InputStream.readBounded(limit: Int): ByteArray {
    val output = ByteArrayOutputStream(minOf(limit, 8192))
    val buffer = ByteArray(8192)
    while (output.size() < limit) {
        val count = read(buffer, 0, minOf(buffer.size, limit - output.size()))
        if (count < 0) return output.toByteArray()
        output.write(buffer, 0, count)
    }
    if (read() != -1) fail(JsonStoreError.INVALID_DATA)
    return output.toByteArray()
}
