package com.example.luacompose

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LuaUiTest {
    @Test
    fun `validated gaps map directly to dp`() {
        assertEquals(0.dp, 0.toDp())
        assertEquals(16.dp, 16.toDp())
        assertEquals(1_000.dp, 1_000.toDp())
    }

    @Test
    fun `text styles map to Material typography`() {
        val body = TextStyle(fontSize = 11.sp)
        val title = TextStyle(fontSize = 22.sp)
        val metric = TextStyle(fontSize = 33.sp)
        val typography = Typography(
            bodyLarge = body,
            titleLarge = title,
            headlineMedium = metric,
        )

        assertEquals(body, UiTextStyle.Body.textStyle(typography))
        assertEquals(title, UiTextStyle.Title.textStyle(typography))
        assertEquals(metric, UiTextStyle.Metric.textStyle(typography))
    }

    @Test
    fun `button callback propagates its named action exactly`() {
        val actions = mutableListOf<String>()

        action("dashboard.refresh-v2", actions::add).invoke()

        assertEquals(listOf("dashboard.refresh-v2"), actions)
    }

    @Test
    fun `typed callbacks preserve exact values and disabled controls are silent`() {
        val inputs = mutableListOf<UiInput>()
        textInput("todo.draft", true, inputs::add).invoke("😀")
        checkedInput("todo.toggle.item-1", true, inputs::add).invoke(false)
        textInput("todo.draft", false, inputs::add).invoke("ignored")
        checkedInput("todo.toggle.item-1", false, inputs::add).invoke(true)
        action("todo.add", {}, enabled = false).invoke()

        assertEquals(
            listOf(
                UiInput.TextChanged("todo.draft", "😀"),
                UiInput.CheckedChanged("todo.toggle.item-1", false),
            ),
            inputs,
        )
    }

    @Test
    fun `failure text is safe and does not expose engine details`() {
        val detail = "secret runtime detail"
        val failure = LuaUiResult.Failure(listOf(LuaUiError(LuaUiError.Kind.Runtime, detail)))

        assertEquals("Unable to render UI.", failure.displayText())
        assertFalse(failure.displayText().contains(detail))
    }
}
