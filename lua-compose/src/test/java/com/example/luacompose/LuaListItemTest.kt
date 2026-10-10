package com.example.luacompose

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LuaListItemTest {
    private val engine = LuaUiEngine()

    @Test fun `list content is composed from ordinary nodes`() {
        val result = engine.evaluate("""
            return ui.listItem { key = 'item.1', children = {
                ui.row { children = {
                    ui.checkbox { checked = true, label = 'Done', showLabel = false, action = 'toggle' },
                    ui.text { text = 'Done', weight = true, strikeThrough = true },
                    ui.iconButton { icon = 'delete', label = 'Delete Done', action = 'delete', enabled = false }
                } }
            } }
        """.trimIndent()) as LuaUiResult.Success
        assertEquals(UiNode.ListItem("item.1", listOf(UiNode.Row(listOf(
            UiNode.Checkbox(true, "Done", "toggle", showLabel = false),
            UiNode.Text("Done", weight = true, strikeThrough = true),
            UiNode.IconButton(UiIcon.Delete, "Delete Done", "delete", enabled = false),
        )))), result.root)
    }

    @Test fun `new fields reject invalid values at the Lua boundary`() {
        val scripts = listOf(
            "return ui.listItem { key = '', children = {} }",
            "return ui.listItem { key = 'item', children = false }",
            "return ui.listItem { key = 'item', text = 'old schema' }",
            "return ui.iconButton { icon = 'unknown', label = 'Delete', action = 'delete' }",
            "return ui.iconButton { icon = 'delete', label = '', action = 'delete' }",
            "return ui.iconButton { icon = 'delete', label = 'Delete', action = 'bad action' }",
            "return ui.iconButton { icon = 'delete', label = 'Delete', action = 'delete', enabled = 1 }",
            "return ui.checkbox { checked = true, label = 'Done', action = 'toggle', showLabel = 1 }",
            "return ui.text { text = 'Done', weight = 1 }",
            "return ui.text { text = 'Done', strikeThrough = 'yes' }",
        )
        scripts.forEach { assertTrue(it, engine.evaluate(it) is LuaUiResult.Failure) }
    }

    @Test fun `all allowed icons map to Compose vectors`() {
        UiIcon.entries.forEach { icon ->
            val result = engine.evaluate("return ui.iconButton { icon = '${icon.name.lowercase()}', label = 'Action', action = 'action' }") as LuaUiResult.Success
            assertEquals(icon, (result.root as UiNode.IconButton).icon)
            assertTrue(icon.imageVector().name.isNotEmpty())
        }
    }

    @Test fun `nested icon actions admit only enabled matching event types`() {
        val session = LuaSession("""
            local count = 0
            return {
                render = function()
                    return ui.listItem { key = 'item', children = { ui.row { children = {
                        ui.text { text = tostring(count) },
                        ui.iconButton { icon = 'delete', label = 'Delete', action = 'delete' },
                        ui.iconButton { icon = 'close', label = 'Close', action = 'disabled', enabled = false }
                    } } } }
                end,
                onEvent = function(event) count = count + 1 end
            }
        """.trimIndent(), { error("Storage is unused") })
        val initial = session.start()
        assertEquals(initial, session.dispatch(LuaEvent.Action("disabled")))
        assertEquals(initial, session.dispatch(LuaEvent.Action("missing")))
        assertEquals(initial, session.dispatch(LuaEvent.CheckedChanged("delete", true)))
        val changed = session.dispatch(LuaEvent.Action("delete")) as LuaUiResult.Success
        val row = (changed.root as UiNode.ListItem).children.single() as UiNode.Row
        assertEquals("1", (row.children.first() as UiNode.Text).text)
    }
}
