package com.example.luaplayground

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LuaEngineTest {
    private val engine = LuaEngine()

    @Test fun `app execution exposes strict route registration hooks`() {
        val routes = mutableListOf<Pair<String, String>>()
        var start = ""
        val result = engine.executeApp(
            "registerRoute('item/{id}', 'features/item.luac'); setStartRoute('home')".encodeToByteArray(),
            registerRoute = { route, script -> routes += route to script },
            setStartRoute = { start = it },
        )

        assertTrue(result.succeeded)
        assertEquals(listOf("item/{id}" to "features/item.luac"), routes)
        assertEquals("home", start)
    }

    @Test fun `app registration rejects wrong argument counts and types`() {
        listOf("registerRoute('only-one')", "registerRoute('route', 2)", "setStartRoute('one', 'two')").forEach {
            assertFalse(engine.executeApp(it.encodeToByteArray(), { _, _ -> }, {}).succeeded)
        }
    }
}
