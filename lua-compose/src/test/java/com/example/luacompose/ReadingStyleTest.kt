package com.example.luacompose

import androidx.compose.ui.graphics.Color
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.pow

class ReadingStyleTest {
    @Test fun `feature themes override supported colors and retain unspecified colors`() {
        val base = readingColors(false)
        val colors = base.withFeatureColors(mapOf("primary" to 0xFF123456L, "secondary" to 0xFF654321L))
        org.junit.Assert.assertEquals(androidx.compose.ui.graphics.Color(0xFF123456L), colors.primary)
        org.junit.Assert.assertEquals(androidx.compose.ui.graphics.Color(0xFF654321L), colors.secondary)
        org.junit.Assert.assertEquals(base.surface, colors.surface)
    }

    @Test fun `reading images use the bookplate while other images keep the avatar`() {
        assertEquals(R.drawable.default_reading_icon, defaultImageResource(reading = true))
        assertEquals(R.drawable.default_avatar, defaultImageResource(reading = false))
    }

    @Test fun `trailing icon validates type and respects field and button enabled states`() {
        for ((fieldEnabled, iconEnabled) in listOf(true to true, false to true, true to false)) {
            val session = LuaSession("""
                local value = ''
                return {
                  render = function() return ui.textField {value=value, label='URL', action='url', enabled=$fieldEnabled,
                    trailingIcon=ui.iconButton {icon='paste', label='Paste URL', action='paste', enabled=$iconEnabled}} end,
                  onEvent = function(event) if event.action == 'paste' then value='pasted' end end
                }
            """.trimIndent())
            session.start()
            val field = (session.dispatch(LuaEvent.Action("paste")) as LuaUiResult.Success).root as UiNode.TextField
            assertEquals(if (fieldEnabled && iconEnabled) "pasted" else "", field.value)
            session.close()
        }
        for (icon in listOf("false", "ui.text {text='Wrong'}", "ui.iconButton {icon='paste', label='', action='paste'}")) {
            assertTrue(LuaUiEngine().evaluate("return ui.textField {value='', label='URL', action='url', trailingIcon=$icon}") is LuaUiResult.Failure)
        }
    }

    @Test fun `new properties parse and old defaults remain`() {
        fun parse(source: String) = (LuaUiEngine().evaluate("return $source") as LuaUiResult.Success).root
        assertEquals(false, (parse("ui.row {}") as UiNode.Row).wrap)
        assertEquals(null, (parse("ui.card {}") as UiNode.Card).action)
        assertEquals(false, (parse("ui.textField {value='', label='Note', action='note'}") as UiNode.TextField).multiline)
        assertEquals(UiTextTone.Default, (parse("ui.text {text='Text'}") as UiNode.Text).tone)
        assertEquals(UiTextStyle.Badge, (parse("ui.text {text='Unread', style='badge'}") as UiNode.Text).style)
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
            assertContrast(colors.onPrimary, colors.primary)
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
