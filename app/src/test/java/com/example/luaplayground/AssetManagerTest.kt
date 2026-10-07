package com.example.luaplayground

import org.junit.Assert.assertEquals
import org.junit.Test

class AssetManagerTest {
    @Test fun `names returns only sorted Lua files`() {
        val scripts = AssetManager(
            listFiles = { arrayOf("z.luac", "notes.txt", "a.luac", "LUAC") },
            readFile = { byteArrayOf() },
        )

        assertEquals(listOf("a.luac", "z.luac"), scripts.names())
    }

    @Test fun `names handles a missing asset directory`() {
        val scripts = AssetManager(listFiles = { null }, readFile = { byteArrayOf() })

        assertEquals(emptyList<String>(), scripts.names())
    }

    @Test fun `read delegates the exact script name`() {
        var requested = ""
        val scripts = AssetManager(
            listFiles = { emptyArray() },
            readFile = { name -> requested = name; "print('ok')".encodeToByteArray() },
        )

        assertEquals("print('ok')", scripts.read("hello.luac").decodeToString())
        assertEquals("hello.luac", requested)
    }

    @Test fun `dashboard reads its app asset`() {
        val assets = AssetManager(
            listFiles = { emptyArray() },
            readFile = { "console:$it".encodeToByteArray() },
            readDashboardFile = { "dashboard".encodeToByteArray() },
        )

        assertEquals("dashboard", assets.readDashboard().decodeToString())
    }

    @Test fun `todo reads its app asset`() {
        val assets = AssetManager(
            listFiles = { emptyArray() },
            readFile = { "console:$it".encodeToByteArray() },
            readTodoFile = { "todo".encodeToByteArray() },
        )

        assertEquals("todo", assets.readTodo().decodeToString())
    }
}
