package com.example.luacompose

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LuaUiEngineTest {
    private val engine = LuaUiEngine()

    @Test
    fun `evaluates every node property in child order`() {
        val result = engine.evaluate(
            """
            return ui.column {
              gap = 16,
              children = {
                ui.text { text = "Dashboard", style = "title" },
                ui.row {
                  gap = 12,
                  children = {
                    ui.card { style = "accent", children = {
                      ui.text { text = "42", style = "metric" },
                      ui.text { text = "users", style = "body" }
                    } }
                  }
                },
                ui.button { text = "Refresh", action = "refresh", style = "quiet" },
                ui.textField { label = "Task", value = state.draft, action = "todo.draft", enabled = false, error = "Required" },
                ui.checkbox { label = "Done", checked = state.done, action = "todo.toggle.1" }
              }
            }
            """.trimIndent(),
            mapOf("draft" to "Draft", "done" to true),
        )

        assertEquals(
            UiNode.Column(
                listOf(
                    UiNode.Text("Dashboard", UiTextStyle.Title),
                    UiNode.Row(
                        listOf(
                            UiNode.Card(
                                listOf(
                                    UiNode.Text("42", UiTextStyle.Metric),
                                    UiNode.Text("users"),
                                ),
                                UiCardStyle.Accent,
                            ),
                        ),
                        gap = 12,
                    ),
                    UiNode.Button("Refresh", "refresh", style = UiButtonStyle.Quiet),
                    UiNode.TextField("Draft", "Task", "todo.draft", enabled = false, error = "Required"),
                    UiNode.Checkbox(true, "Done", "todo.toggle.1"),
                ),
                gap = 16,
            ),
            result.success(),
        )
    }

    @Test
    fun `converts primitive nested and list state`() {
        val result = engine.evaluate(
            """
            return ui.column { children = {
              ui.text { text = state.name },
              ui.text { text = tostring(state.count) },
              ui.text { text = tostring(state.enabled) },
              ui.text { text = state.details.label },
              ui.text { text = state.items[2] },
              ui.text { text = tostring(state.none) }
            } }
            """.trimIndent(),
            mapOf(
                "name" to "Ada",
                "count" to 7,
                "enabled" to true,
                "details" to mapOf("label" to "Admin"),
                "items" to listOf("first", "second"),
                "none" to null,
            ),
        )

        assertEquals(
            listOf("Ada", "7", "true", "Admin", "second", "nil"),
            (result.success() as UiNode.Column).children.map { (it as UiNode.Text).text },
        )
    }

    @Test
    fun `state is read only at every level`() {
        val state = mapOf("name" to "Ada", "nested" to mapOf("role" to "Admin"))
        val script = """
            local top = pcall(function() state.name = "Grace" end)
            local nested = pcall(function() state.nested.role = "User" end)
            return ui.text { text = tostring(top) .. ":" .. tostring(nested) .. ":" .. state.name .. ":" .. state.nested.role }
        """.trimIndent()

        assertEquals(UiNode.Text("false:false:Ada:Admin"), engine.evaluate(script, state).success())
    }

    @Test
    fun `evaluation does not retain state between runs`() {
        val script = "return ui.text { text = state.value }"

        assertEquals(UiNode.Text("first"), engine.evaluate(script, mapOf("value" to "first")).success())
        assertEquals(UiNode.Text("second"), engine.evaluate(script, mapOf("value" to "second")).success())
    }

    @Test
    fun `reports syntax runtime and root errors deterministically`() {
        assertFailure(LuaUiError.Kind.Syntax, "Invalid Lua syntax.", "return ui.text {")
        assertFailure(LuaUiError.Kind.Runtime, "Lua execution failed.", "error('boom')")
        assertFailure(
            LuaUiError.Kind.Validation,
            "Script must return exactly one root node.",
            "local value = 1",
        )
        assertFailure(
            LuaUiError.Kind.Validation,
            "Script must return exactly one root node.",
            "return ui.text { text = 'one' }, ui.text { text = 'two' }",
        )
    }

    @Test
    fun `rejects unknown node fields styles and actions`() {
        assertFailure(
            LuaUiError.Kind.Validation,
            "Unknown node type 'image' at $.",
            "return { type = 'image' }",
        )
        assertFailure(
            LuaUiError.Kind.Validation,
            "Unknown field 'colour' at $.",
            "return ui.text { text = 'hello', colour = 'red' }",
        )
        assertFailure(
            LuaUiError.Kind.Validation,
            "Unknown style 'headline' at $.",
            "return ui.text { text = 'hello', style = 'headline' }",
        )
        assertFailure(
            LuaUiError.Kind.Validation,
            "Unknown action 'Refresh now' at $.",
            "return ui.button { text = 'Refresh', action = 'Refresh now' }",
        )
    }

    @Test
    fun `rejects missing and invalid property values`() {
        assertFailure(
            LuaUiError.Kind.Validation,
            "Missing field 'text' at $.",
            "return ui.text {}",
        )
        assertFailure(
            LuaUiError.Kind.Validation,
            "Field 'text' at $ must be a string.",
            "return ui.text { text = 3 }",
        )
        assertFailure(
            LuaUiError.Kind.Validation,
            "Field 'gap' at $ must be an integer from 0 to 1000.",
            "return ui.row { gap = -1 }",
        )
        assertFailure(
            LuaUiError.Kind.Validation,
            "Field 'children' at $ must be a table.",
            "return ui.card { children = 'no' }",
        )
        assertFailure(
            LuaUiError.Kind.Validation,
            "Children at $ must be a contiguous list.",
            "return ui.column { children = { [2] = ui.text { text = 'two' } } }",
        )
    }

    @Test
    fun `parses controlled fields and strict booleans`() {
        assertEquals(
            UiNode.TextField("😀", "Task", "todo.draft", error = "Retry"),
            engine.evaluate("return ui.textField { value = '😀', label = 'Task', action = 'todo.draft', error = 'Retry' }").success(),
        )
        assertEquals(
            UiNode.Checkbox(false, "Task", "todo.toggle.1", enabled = false),
            engine.evaluate("return ui.checkbox { checked = false, label = 'Task', action = 'todo.toggle.1', enabled = false }").success(),
        )
        assertEquals(
            UiNode.Button("Add", "todo.add", enabled = false),
            engine.evaluate("return ui.button { text = 'Add', action = 'todo.add', enabled = false }").success(),
        )
        listOf("0", "'true'").forEach { value ->
            assertFailure(LuaUiError.Kind.Validation, "Field 'enabled' at $ must be a boolean.",
                "return ui.button { text = 'Add', action = 'todo.add', enabled = $value }")
            assertFailure(LuaUiError.Kind.Validation, "Field 'checked' at $ must be a boolean.",
                "return ui.checkbox { checked = $value, label = 'Task', action = 'todo.toggle.1' }")
        }
        assertFailure(LuaUiError.Kind.Validation, "Missing field 'checked' at $.",
            "return ui.checkbox { label = 'Task', action = 'todo.toggle.1' }")
        assertFailure(LuaUiError.Kind.Validation, "Field 'error' at $ must be a string or nil.",
            "return ui.textField { value = '', label = 'Task', action = 'todo.draft', error = false }")
        assertFailure(LuaUiError.Kind.Validation, "Field 'label' at $ must not be empty.",
            "return ui.checkbox { checked = false, label = '', action = 'todo.toggle.1' }")
        assertFailure(LuaUiError.Kind.Validation, "Unknown field 'colour' at $.",
            "return ui.textField { value = '', label = 'Task', action = 'todo.draft', colour = 'red' }")
    }

    @Test
    fun `rejects cyclic nodes and state`() {
        assertFailure(
            LuaUiError.Kind.Validation,
            "Node cycle at $.children[1].",
            "local root = ui.column {}; root.children = { root }; return root",
        )

        val state = mutableMapOf<String, Any?>()
        state["self"] = state
        assertFailure(
            LuaUiError.Kind.Validation,
            "Cyclic state value at state.self.",
            "return ui.text { text = 'unused' }",
            state,
        )
    }

    @Test
    fun `rejects unsupported state values`() {
        assertFailure(
            LuaUiError.Kind.Validation,
            "Unsupported state value at state.value.",
            "return ui.text { text = 'unused' }",
            mapOf("value" to Any()),
        )
        assertFailure(
            LuaUiError.Kind.Validation,
            "Non-finite state number at state.value.",
            "return ui.text { text = 'unused' }",
            mapOf("value" to Double.NaN),
        )
    }

    @Test
    fun `unsafe capabilities are absent`() {
        val attempts = listOf(
            "return ui.text { text = luajava.bindClass('java.lang.System') }",
            "return ui.text { text = java.lang.System.getProperty('user.home') }",
            "return ui.text { text = android.app.Activity }",
            "return ui.text { text = io.open('/tmp/file') }",
            "return ui.text { text = os.execute('true') }",
            "return ui.text { text = package.loadlib('x', 'y') }",
            "return ui.text { text = debug.getinfo(1) }",
            "return ui.text { text = require('socket') }",
            "return ui.text { text = load('return 1')() }",
            "return ui.text { text = dofile('/tmp/file') }",
        )

        attempts.forEach { script ->
            assertFailure(LuaUiError.Kind.Runtime, "Lua execution failed.", script)
        }
    }

    private fun LuaUiResult.success(): UiNode {
        assertTrue("Expected success but was $this", this is LuaUiResult.Success)
        return (this as LuaUiResult.Success).root
    }

    private fun assertFailure(
        kind: LuaUiError.Kind,
        message: String,
        script: String,
        state: Map<String, Any?> = emptyMap(),
    ) {
        assertEquals(LuaUiResult.Failure(listOf(LuaUiError(kind, message))), engine.evaluate(script, state))
    }
}
