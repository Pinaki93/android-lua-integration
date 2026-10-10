package com.example.luacompose

import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LuaUiTest {
    @Test
    fun `list items use compact horizontal padding`() {
        assertEquals(4, LIST_ITEM_HORIZONTAL_PADDING)
    }

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
        val label = TextStyle(fontSize = 9.sp)
        val typography = Typography(
            bodyLarge = body,
            titleLarge = title,
            headlineMedium = metric,
            labelMedium = label,
        )

        assertEquals(body, UiTextStyle.Body.textStyle(typography))
        assertEquals(title, UiTextStyle.Title.textStyle(typography))
        assertEquals(metric, UiTextStyle.Metric.textStyle(typography))
        assertEquals(label, UiTextStyle.Label.textStyle(typography))
    }

    @Test
    fun `card styles map to distinct Material containers`() {
        val colors = lightColorScheme(
            primary = Color.Magenta,
            primaryContainer = Color.Red,
            surfaceContainerLow = Color.Green,
            surfaceContainerHighest = Color.Blue,
            surface = Color.Yellow,
            tertiary = Color.Cyan,
            tertiaryContainer = Color.Gray,
        )

        assertEquals(Color.Blue, UiCardStyle.Default.containerColor(colors))
        assertEquals(Color.Magenta, UiCardStyle.Accent.containerColor(colors))
        assertEquals(Color.Green, UiCardStyle.Subtle.containerColor(colors))
        assertEquals(Color.Yellow, UiCardStyle.Outlined.containerColor(colors))
        assertEquals(Color.Cyan, UiCardStyle.Orange.containerColor(colors))
        assertEquals(Color.Gray, UiCardStyle.LightOrange.containerColor(colors))
    }

    @Test
    fun `orange buttons use the color paired with light orange cards`() {
        val colors = lightColorScheme(tertiary = Color.Cyan, tertiaryContainer = Color.Gray)

        assertEquals(Color.Cyan, UiButtonStyle.Orange.containerColor(colors))
        assertEquals(UiButtonStyle.Orange.containerColor(colors), checkboxColor(colors))
        assertEquals(Color.Gray, UiCardStyle.LightOrange.containerColor(colors))
    }

    @Test
    fun `dividers appear only between adjacent list items`() {
        val item = UiNode.ListItem("task.1", listOf(UiNode.Text("Task")))
        val children = listOf(UiNode.Text("Tasks"), item, item, UiNode.Text("End"))

        assertFalse(hasListDivider(children, 0))
        assertEquals(true, hasListDivider(children, 1))
        assertFalse(hasListDivider(children, 2))
        assertFalse(hasListDivider(children, 3))
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

    @Test
    fun `lazy rendering applies only to enabled root columns`() {
        val column = UiNode.Column(listOf(UiNode.Text("item")), gap = 8)

        assertEquals(column, lazyRoot(column, true))
        assertEquals(null, lazyRoot(column, false))
        assertEquals(null, lazyRoot(UiNode.Text("item"), true))
    }

    @Test
    fun `lazy list items keep their identity when their position changes`() {
        val item = UiNode.ListItem("task.42", listOf(UiNode.Text("Task")))

        assertEquals(lazyItemKey(5, item), lazyItemKey(20, item))
        assertEquals(5, lazyItemKey(5, UiNode.Text("Heading")))
    }
}
