package com.example.luacompose

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonStoreTest {
    private var bytes: ByteArray? = null
    private var writes = 0
    private var deletes = 0
    private var readFailure = false
    private var writeFailure = false

    private fun store() = JsonStore(
        read = { if (readFailure) error("private read detail"); bytes?.copyOf() },
        write = { if (writeFailure) error("private write detail"); writes++; bytes = it.copyOf() },
        delete = { deletes++; bytes = null },
    )

    @Test fun `strict CRUD preserves nested JSON and unicode`() {
        val store = store()
        assertNull(store.read())
        store.delete()
        assertEquals(0, deletes)
        val first = """{"nested":[true,3,"Crème 😀"]}"""
        store.create(first)
        assertEquals(first, store.read())
        assertError(JsonStoreError.ALREADY_EXISTS) { store.create("{}") }
        store.update("[1,2,3]")
        assertEquals("[1,2,3]", store().read())
        store.delete()
        store.delete()
        assertEquals(1, deletes)
        assertError(JsonStoreError.NOT_FOUND) { store.update("{}") }
        store.create("null")
        assertEquals("null", store.read())
    }

    @Test fun `invalid input and exact quota are checked before writing`() {
        listOf("", "{", "[1,]").forEach { assertError(JsonStoreError.INVALID_JSON) { store().create(it) } }
        assertError(JsonStoreError.INVALID_JSON) { store().create("\uD800") }
        val exact = "\"" + "x".repeat(JsonStore.MAX_DOCUMENT_BYTES - 2) + "\""
        store().create(exact)
        assertEquals(JsonStore.MAX_DOCUMENT_BYTES, bytes!!.size)
        bytes = null
        assertError(JsonStoreError.QUOTA_EXCEEDED) { store().create(exact + " ") }
    }

    @Test fun `corrupt storage blocks every mutation without changing bytes`() {
        val corrupt = listOf("{".toByteArray(), byteArrayOf(0xC3.toByte()), ByteArray(JsonStore.MAX_DOCUMENT_BYTES + 1))
        corrupt.forEach { value ->
            bytes = value
            val before = bytes!!.copyOf()
            assertError(JsonStoreError.INVALID_DATA) { store().read() }
            assertError(JsonStoreError.INVALID_DATA) { store().create("{}") }
            assertError(JsonStoreError.INVALID_DATA) { store().update("{}") }
            assertError(JsonStoreError.INVALID_DATA) { store().delete() }
            assertTrue(before.contentEquals(bytes!!))
        }
    }

    @Test fun `backend failures are stable and failed writes preserve committed data`() {
        store().create("{\"old\":true}")
        val before = bytes!!.copyOf()
        writeFailure = true
        assertError(JsonStoreError.UNAVAILABLE) { store().update("{\"new\":true}") }
        assertTrue(before.contentEquals(bytes!!))
        writeFailure = false
        store().update("{\"new\":true}")
        readFailure = true
        assertError(JsonStoreError.UNAVAILABLE) { store().read() }
    }

    @Test fun `concurrent operations are serialized`() {
        val pool = Executors.newFixedThreadPool(8)
        repeat(100) { index -> pool.submit { if (index == 0) store().create("0") else runCatching { store().update(index.toString()) } } }
        pool.shutdown()
        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS))
        assertTrue(store().read()!!.toInt() in 0..99)
    }

    private fun assertError(expected: JsonStoreError, block: () -> Unit) {
        val failure = assertThrows(JsonStoreException::class.java, block)
        assertEquals(expected, failure.error)
        assertEquals(expected.name, failure.message)
    }
}
