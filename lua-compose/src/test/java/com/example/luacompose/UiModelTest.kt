package com.example.luacompose

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class UiModelTest {
    @Test
    fun `nodes preserve properties and child order`() {
        val title = UiNode.Text("Dashboard", UiTextStyle.Title)
        val metric = UiNode.Text("42", UiTextStyle.Metric)
        val card = UiNode.Card(listOf(metric))
        val row = UiNode.Row(listOf(card), gap = 12)
        val button = UiNode.Button("Refresh", "refresh", enabled = false)
        val field = UiNode.TextField("draft", "Task", "todo.draft", error = "Required")
        val checkbox = UiNode.Checkbox(true, "Task", "todo.toggle.1")

        assertEquals(
            UiNode.Column(listOf(title, row, button, field, checkbox), gap = 16),
            UiNode.Column(listOf(title, row, button, field, checkbox), gap = 16),
        )
        assertEquals(UiTextStyle.Body, UiNode.Text("Body").style)
        assertEquals(0, UiNode.Row(emptyList()).gap)
        assertEquals(true, UiNode.Button("Go", "go").enabled)
        assertEquals(true, UiNode.TextField("", "Name", "name").enabled)
        assertEquals(true, UiNode.Checkbox(false, "Done", "done").enabled)
    }

    @Test
    fun `node and failure lists are immutable snapshots`() {
        val children = mutableListOf<UiNode>(UiNode.Text("First"))
        val root = UiNode.Column(children)
        children += UiNode.Text("Later")

        assertEquals(listOf(UiNode.Text("First")), root.children)
        assertThrows(UnsupportedOperationException::class.java) {
            (root.children as MutableList).add(UiNode.Text("Blocked"))
        }

        val errors = mutableListOf(LuaUiError(LuaUiError.Kind.Validation, "missing text"))
        val failure = LuaUiResult.Failure(errors)
        errors += LuaUiError(LuaUiError.Kind.Limit, "too deep")

        assertEquals(1, failure.errors.size)
        assertThrows(UnsupportedOperationException::class.java) {
            (failure.errors as MutableList).clear()
        }
    }

    @Test
    fun `results retain success root and stable error details`() {
        val root = UiNode.Button("Refresh", "refresh")
        val error = LuaUiError(LuaUiError.Kind.Syntax, "unexpected token")
        val success: LuaUiResult = LuaUiResult.Success(root)
        val failure: LuaUiResult = LuaUiResult.Failure(listOf(error))

        assertEquals(root, (success as LuaUiResult.Success).root)
        assertEquals(error, (failure as LuaUiResult.Failure).errors.single())
    }
}
