package com.example.luacompose

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LuaSessionTest {
    @Test fun `retains closures and admits only matching enabled events`() {
        val session = LuaSession(
            """
            local value = ""
            return {
              render = function() return ui.textField { label = "Value", value = value, action = "change" } end,
              onEvent = function(event) value = value .. event.value end
            }
            """.trimIndent(),
        )
        assertEquals("", session.start().field().value)
        assertEquals("a", session.dispatch(LuaEvent.TextChanged("change", "a")).field().value)
        assertEquals("ab", session.dispatch(LuaEvent.TextChanged("change", "b")).field().value)
        assertEquals("ab", session.dispatch(LuaEvent.Action("change")).field().value)
        assertEquals("ab", session.dispatch(LuaEvent.TextChanged("other", "x")).field().value)
    }

    @Test fun `store exchanges JSON tables and capability is frozen`() {
        var bytes: ByteArray? = null
        val store = JsonStore({ bytes }, { bytes = it }, { bytes = null })
        val session = LuaSession(
            """
            local value = store.read()
            if value == nil then store.create({name = "Ada", values = {1, 2}}); value = store.read() end
            local frozen = not pcall(function() store.read = nil end)
            return {
              render = function() return ui.text { text = value.name .. tostring(value.values[2]) .. tostring(frozen) } end,
              onEvent = function() end
            }
            """.trimIndent(), store,
        )
        assertEquals(UiNode.Text("Ada2true"), session.start().root())
        assertEquals("{\"name\":\"Ada\",\"values\":[1,2]}", bytes!!.toString(Charsets.UTF_8))
    }

    @Test fun `rejects invalid JSON tables and render writes without backend access`() {
        var writes = 0
        val store = JsonStore({ null }, { writes++ }, {})
        val invalid = LuaSession(
            """
            local message = ""
            return {
              render = function() return ui.button { text = message, action = "go" } end,
              onEvent = function() local value = {}; value.self = value; message = tostring(pcall(store.create, value)) end
            }
            """.trimIndent(), store,
        )
        invalid.start()
        assertEquals("false", (invalid.dispatch(LuaEvent.Action("go")).root() as UiNode.Button).text)
        assertEquals(0, writes)

        val duringRender = LuaSession(
            """
            return {
              render = function() return ui.text { text = tostring(pcall(store.create, {x = 1})) } end,
              onEvent = function() end
            }
            """.trimIndent(), store,
        )
        assertEquals(UiNode.Text("false"), duringRender.start().root())
        assertEquals(0, writes)
    }

    @Test fun `sandbox failures are terminal and sessions are independent`() {
        val unsafe = LuaSession("return { render = function() return ui.text { text = io.open('/tmp/x') } end, onEvent = function() end }")
        assertTrue(unsafe.start() is LuaUiResult.Failure)
        assertEquals(unsafe.start(), unsafe.dispatch(LuaEvent.Action("anything")))

        val script = "local n=0; return {render=function() return ui.button{text=tostring(n),action='go'} end,onEvent=function() n=n+1 end}"
        val first = LuaSession(script)
        val second = LuaSession(script)
        first.start(); second.start()
        assertEquals("1", (first.dispatch(LuaEvent.Action("go")).root() as UiNode.Button).text)
        assertEquals("0", (second.start().root() as UiNode.Button).text)
    }

    @Test fun `tree limits stay categorized and close is terminal`() {
        val limited = LuaSession(
            """
            return {
              render = function()
                local root = ui.text { text = "end" }
                for _ = 1, 33 do root = ui.column { children = { root } } end
                return root
              end,
              onEvent = function() end
            }
            """.trimIndent(),
        )
        val failure = limited.start() as LuaUiResult.Failure
        assertEquals(LuaUiError.Kind.Limit, failure.errors.single().kind)

        val session = LuaSession("return {render=function() return ui.button{text='x',action='go'} end,onEvent=function() end}")
        session.start()
        session.close()
        assertTrue(session.dispatch(LuaEvent.Action("go")) is LuaUiResult.Failure)
    }

    private fun LuaUiResult.root() = (this as LuaUiResult.Success).root
    private fun LuaUiResult.field() = root() as UiNode.TextField
}
