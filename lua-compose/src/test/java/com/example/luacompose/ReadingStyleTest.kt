package com.example.luacompose

import androidx.compose.ui.graphics.Color
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.pow

class ReadingStyleTest {
    @Test fun `new properties parse and old defaults remain`() {
        fun parse(source: String) = (LuaUiEngine().evaluate("return $source") as LuaUiResult.Success).root
        assertEquals(false, (parse("ui.row {}") as UiNode.Row).wrap)
        assertEquals(null, (parse("ui.card {}") as UiNode.Card).action)
        assertEquals(false, (parse("ui.textField {value='', label='Note', action='note'}") as UiNode.TextField).multiline)
        assertEquals(UiTextTone.Default, (parse("ui.text {text='Text'}") as UiNode.Text).tone)
        assertTrue((parse("ui.row {wrap=true}") as UiNode.Row).wrap)
        assertEquals("open", (parse("ui.card {action='open'}") as UiNode.Card).action)
        assertTrue((parse("ui.textField {value='', label='Note', action='note', multiline=true}") as UiNode.TextField).multiline)
        for (tone in UiTextTone.entries) assertEquals(tone, (parse("ui.text {text='Text', tone='${tone.name.lowercase()}'}") as UiNode.Text).tone)
        for (style in listOf(UiButtonStyle.Filter, UiButtonStyle.Selected, UiButtonStyle.Destructive)) {
            assertEquals(style, (parse("ui.button {text='Go', action='go', style='${style.name.lowercase()}'}") as UiNode.Button).style)
        }
    }

    @Test fun `invalid properties fail at the Lua boundary`() {
        for (source in listOf("ui.row {wrap=1}", "ui.card {action='Bad action'}", "ui.card {action=false}",
            "ui.text {text='Text', tone='unknown'}", "ui.text {text='Text', tone=true}",
            "ui.textField {value='', label='Note', action='note', multiline='yes'}",
            "ui.button {text='Go', action='go', style='unknown'}")) {
            assertTrue(source, LuaUiEngine().evaluate("return $source") is LuaUiResult.Failure)
        }
    }

    @Test fun `both palettes pair readable text with every card and button background`() {
        for (dark in listOf(false, true)) {
            val colors = readingColors(dark)
            for (style in UiCardStyle.entries) assertContrast(style.contentColor(colors), style.containerColor(colors))
            for (style in UiButtonStyle.entries.filterNot { it == UiButtonStyle.Quiet }) {
                assertContrast(style.contentColor(colors), style.containerColor(colors))
            }
            for (background in listOf(colors.background, colors.surface, colors.surfaceContainerLow)) {
                for (foreground in listOf(colors.onSurface, colors.onSurfaceVariant, colors.primary, colors.tertiary)) {
                    assertContrast(foreground, background)
                }
            }
        }
    }

    private fun assertContrast(foreground: Color, background: Color) {
        fun luminance(color: Color): Double {
            fun linear(value: Float): Double = if (value <= 0.04045f) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
            return 0.2126 * linear(color.red) + 0.7152 * linear(color.green) + 0.0722 * linear(color.blue)
        }
        val first = luminance(foreground)
        val second = luminance(background)
        val ratio = (maxOf(first, second) + 0.05) / (minOf(first, second) + 0.05)
        assertTrue("Contrast $ratio for $foreground on $background", ratio >= 4.5)
    }
}
