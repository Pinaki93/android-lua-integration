package com.example.luacompose

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LuaUiLimitsTest {
    private val engine = LuaUiEngine()

    @Test
    fun `script limit counts UTF-8 bytes`() {
        val prefix = "--"
        val within = prefix + "é".repeat((LuaUiEngine.MAX_SCRIPT_BYTES - prefix.length) / 2)
        assertFailure(LuaUiError.Kind.Validation, "Script must return exactly one root node.", within)

        val over = within + "é"
        assertFailure(
            LuaUiError.Kind.Limit,
            "Script exceeds ${LuaUiEngine.MAX_SCRIPT_BYTES} UTF-8 bytes.",
            over,
        )
    }

    @Test
    fun `tree depth accepts maximum and rejects one more`() {
        assertTrue(engine.evaluate(nestedCards(LuaUiEngine.MAX_DEPTH)) is LuaUiResult.Success)
        assertFailure(
            LuaUiError.Kind.Limit,
            "Tree depth exceeds ${LuaUiEngine.MAX_DEPTH} at ${deepPath(LuaUiEngine.MAX_DEPTH)}.",
            nestedCards(LuaUiEngine.MAX_DEPTH + 1),
        )
    }

    @Test
    fun `node count accepts maximum and rejects one more`() {
        assertTrue(engine.evaluate(columnWithTextNodes(LuaUiEngine.MAX_NODES - 1)) is LuaUiResult.Success)
        assertFailure(
            LuaUiError.Kind.Limit,
            "Node count exceeds ${LuaUiEngine.MAX_NODES} at $.children[${LuaUiEngine.MAX_NODES}].",
            columnWithTextNodes(LuaUiEngine.MAX_NODES),
        )
    }

    @Test
    fun `per-text limit counts Unicode code points`() {
        val exact = "😀".repeat(LuaUiEngine.MAX_TEXT_LENGTH)
        assertTrue(engine.evaluate("return ui.text { text = state.text }", mapOf("text" to exact)) is LuaUiResult.Success)

        assertFailure(
            LuaUiError.Kind.Limit,
            "Text at $ exceeds ${LuaUiEngine.MAX_TEXT_LENGTH} characters.",
            "return ui.text { text = state.text }",
            mapOf("text" to exact + "😀"),
        )
    }

    @Test
    fun `total text limit includes text and button labels`() {
        val text = "x".repeat(LuaUiEngine.MAX_TEXT_LENGTH)
        val exactChildren = (1..10).joinToString(",") { "ui.text { text = state.text }" }
        assertTrue(
            engine.evaluate("return ui.column { children = { $exactChildren } }", mapOf("text" to text))
                is LuaUiResult.Success,
        )

        val overChildren = "$exactChildren, ui.button { text = 'x', action = 'refresh' }"
        assertFailure(
            LuaUiError.Kind.Limit,
            "Total text exceeds ${LuaUiEngine.MAX_TOTAL_TEXT_LENGTH} characters at $.children[11].",
            "return ui.column { children = { $overChildren } }",
            mapOf("text" to text),
        )
    }

    @Test
    fun `form value label and error share Unicode text limits`() {
        val exact = "😀".repeat(LuaUiEngine.MAX_TEXT_LENGTH)
        assertTrue(engine.evaluate(
            "return ui.textField { value = state.text, label = 'Task', action = 'todo.draft' }",
            mapOf("text" to exact),
        ) is LuaUiResult.Success)
        assertTrue(engine.evaluate(
            "return ui.textField { value = '', label = state.text, action = 'todo.draft' }",
            mapOf("text" to exact),
        ) is LuaUiResult.Success)
        assertTrue(engine.evaluate(
            "return ui.textField { value = '', label = 'Task', action = 'todo.draft', error = state.text }",
            mapOf("text" to exact),
        ) is LuaUiResult.Success)
        listOf("value", "label", "error").forEach { field ->
            val script = when (field) {
                "value" -> "return ui.textField { value = state.text, label = 'Task', action = 'todo.draft' }"
                "label" -> "return ui.textField { value = '', label = state.text, action = 'todo.draft' }"
                else -> "return ui.textField { value = '', label = 'Task', action = 'todo.draft', error = state.text }"
            }
            assertFailure(
                LuaUiError.Kind.Limit,
                "Text at $ exceeds ${LuaUiEngine.MAX_TEXT_LENGTH} characters.",
                script,
                mapOf("text" to exact + "😀"),
            )
        }
    }

    @Test
    fun `form text participates in aggregate limit`() {
        val exactText = "x".repeat(LuaUiEngine.MAX_TEXT_LENGTH)
        val children = (1..8).joinToString(",") { "ui.text { text = state.text }" }
        val script = "return ui.column { children = { $children, ui.textField { value = 'x', label = state.text, action = 'todo.draft', error = state.error } } }"
        assertTrue(engine.evaluate(script, mapOf("text" to exactText, "error" to "x".repeat(9_999))).let { it is LuaUiResult.Success })
        assertFailure(
            LuaUiError.Kind.Limit,
            "Total text exceeds ${LuaUiEngine.MAX_TOTAL_TEXT_LENGTH} characters at $.children[9].",
            script,
            mapOf("text" to exactText, "error" to "x".repeat(10_000)),
        )
    }

    private fun nestedCards(depth: Int): String = buildString {
        repeat(depth - 1) { append("ui.card { children = { ") }
        append("ui.text { text = 'leaf' }")
        repeat(depth - 1) { append(" } }") }
        insert(0, "return ")
    }

    private fun deepPath(maxDepth: Int) = buildString {
        append('$')
        repeat(maxDepth) { append(".children[1]") }
    }

    private fun columnWithTextNodes(count: Int): String =
        "return ui.column { children = { " +
            (1..count).joinToString(",") { "ui.text { text = 'x' }" } +
            " } }"

    private fun assertFailure(
        kind: LuaUiError.Kind,
        message: String,
        script: String,
        state: Map<String, Any?> = emptyMap(),
    ) {
        assertEquals(LuaUiResult.Failure(listOf(LuaUiError(kind, message))), engine.evaluate(script, state))
    }
}
