package com.example.luaplayground

import com.example.luacompose.JsonStore
import com.example.luacompose.LuaEvent
import com.example.luacompose.LuaNavigation
import com.example.luacompose.LuaSession
import com.example.luacompose.LuaUiResult
import com.example.luacompose.UiNode
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaygroundLuaTest {
    @Test fun `playground renders every example and navigates from its buttons`() {
        val routes = mutableListOf<String>()
        val session = LuaSession(
            File("../lua/playground.lua").readText(),
            storage = { JsonStore({ null }, {}, {}) },
            navigation = LuaNavigation(navigate = { routes += it }, back = {}),
        )

        val start = session.start()
        assertEquals(
            listOf("Lua Compose dashboard", "Console input and output", "Persistent todo form"),
            start.nodes().filterIsInstance<UiNode.Button>().map { it.text },
        )
        session.dispatch(LuaEvent.Action("playground.dashboard"))
        session.dispatch(LuaEvent.Action("playground.console"))
        session.dispatch(LuaEvent.Action("playground.todo"))
        assertEquals(listOf("dashboard", "console", "todo"), routes)
    }

    private fun LuaUiResult.nodes(): List<UiNode> = when (this) {
        is LuaUiResult.Failure -> emptyList()
        is LuaUiResult.Success -> root.flatten()
    }

    private fun UiNode.flatten(): List<UiNode> = listOf(this) + when (this) {
        is UiNode.Card -> children.flatMap { it.flatten() }
        is UiNode.Column -> children.flatMap { it.flatten() }
        is UiNode.Row -> children.flatMap { it.flatten() }
        else -> emptyList()
    }
}
