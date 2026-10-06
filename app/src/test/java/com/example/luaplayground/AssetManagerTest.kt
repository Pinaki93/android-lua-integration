package com.example.luaplayground

import org.junit.Assert.assertEquals
import org.junit.Test

class AssetManagerTest {
    @Test fun `names returns only sorted Lua files`() {
        val scripts = AssetManager(
            listFiles = { arrayOf("z.lua", "notes.txt", "a.lua", "LUA") },
            readFile = { "" },
        )

        assertEquals(listOf("a.lua", "z.lua"), scripts.names())
    }

    @Test fun `names handles a missing asset directory`() {
        val scripts = AssetManager(listFiles = { null }, readFile = { "" })

        assertEquals(emptyList<String>(), scripts.names())
    }

    @Test fun `read delegates the exact script name`() {
        var requested = ""
        val scripts = AssetManager(
            listFiles = { emptyArray() },
            readFile = { name -> requested = name; "print('ok')" },
        )

        assertEquals("print('ok')", scripts.read("hello.lua"))
        assertEquals("hello.lua", requested)
    }

    @Test fun `dashboard reads its app asset`() {
        val assets = AssetManager(
            listFiles = { emptyArray() },
            readFile = { "console:$it" },
            readDashboardFile = { "dashboard" },
        )

        assertEquals("dashboard", assets.readDashboard())
    }

    @Test fun `todo reads its app asset`() {
        val assets = AssetManager(
            listFiles = { emptyArray() },
            readFile = { "console:$it" },
            readTodoFile = { "todo" },
        )

        assertEquals("todo", assets.readTodo())
    }
}
