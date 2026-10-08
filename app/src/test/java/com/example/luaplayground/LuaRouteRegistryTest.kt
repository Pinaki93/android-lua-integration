package com.example.luaplayground

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LuaRouteRegistryTest {
    private val registry = LuaRouteRegistry()

    @Test fun `accepts literal and placeholder routes with nested scripts`() {
        assertTrue(registry.accepts(
            "home",
            listOf(
                LuaRoute("home", "home.luac"),
                LuaRoute("items/{itemId}", "features/items/detail.luac"),
            ),
        ))
    }

    @Test fun `rejects ambiguous invalid and native route patterns`() {
        val invalid = listOf(
            listOf(LuaRoute("console", "page.luac")),
            listOf(LuaRoute("item/{id}", "one.luac"), LuaRoute("item/{name}", "two.luac")),
            listOf(LuaRoute("item/{id}/{id}", "page.luac")),
            listOf(LuaRoute("item?id={id}", "page.luac")),
            listOf(LuaRoute("item//id", "page.luac")),
        )

        invalid.forEach { assertFalse(registry.accepts("console", it)) }
    }

    @Test fun `rejects unsafe scripts and unresolved start routes`() {
        listOf("../page.luac", "/page.luac", "features\\page.luac", "page.lua").forEach { script ->
            assertFalse(registry.accepts("console", listOf(LuaRoute("page", script))))
        }
        assertFalse(registry.accepts("item/{id}", listOf(LuaRoute("item/{id}", "item.luac"))))
        assertFalse(registry.accepts("missing", listOf(LuaRoute("page", "page.luac"))))
    }

    @Test fun `storage names are app private basenames`() {
        listOf("todo.json", "feature-x.v2.json", "A_1.json").forEach { assertTrue(validStorageName(it)) }
        listOf("../todo.json", "folder/todo.json", "todo", ".json", "todo.JSON").forEach {
            assertFalse(validStorageName(it))
        }
    }
}
