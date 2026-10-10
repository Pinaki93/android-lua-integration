package com.example.luacompose

import org.junit.Assert.*
import org.junit.Test

class LuaScaffoldTest {
    private val engine = LuaUiEngine()

    @Test fun `bottom sheet validates its tree actions and radius`() {
        val result = engine.evaluate("""
            return ui.scaffold { content = ui.text { text = 'Body' },
              bottomSheet = ui.bottomSheet { content = ui.button { text = 'Read', action = 'read' }, dismissAction = 'dismiss' }
            }
        """) as LuaUiResult.Success
        assertEquals(28, (result.root as UiNode.Scaffold).bottomSheet!!.cornerRadius)
        for (radius in listOf("-1", "65", "1.5", "'32'", "false")) {
            assertTrue(engine.evaluate("return ui.bottomSheet { content = ui.text { text = 'Body' }, dismissAction = 'dismiss', cornerRadius = $radius }") is LuaUiResult.Failure)
        }
        for (fields in listOf("dismissAction = 'dismiss'", "content = ui.text { text = 'Body' }, dismissAction = '../bad'")) {
            assertTrue(engine.evaluate("return ui.bottomSheet { $fields }") is LuaUiResult.Failure)
        }
        for (radius in listOf(0, 64)) {
            assertTrue(engine.evaluate("return ui.bottomSheet { content = ui.text { text = 'Body' }, dismissAction = 'dismiss', cornerRadius = $radius }") is LuaUiResult.Success)
        }
    }

    @Test fun `scaffold slots are optional and alert fields are optional`() {
        val content = UiNode.Text("Body")
        assertEquals(LuaUiResult.Success(UiNode.Scaffold(content)), engine.evaluate("return ui.scaffold { content = ui.text { text = 'Body' } }"))
        val result = engine.evaluate("""
            return ui.scaffold {
              content = ui.text { text = 'Body' },
              toolbar = ui.toolbar { title = 'Title', backAction = 'back', children = {
                ui.iconButton { icon = 'add', label = 'Add', action = 'add' }
              }, overflow = { ui.button { text = 'Retry', action = 'retry', enabled = false } } },
              alert = ui.alert { dismissAction = 'dismiss' },
              snackbar = ui.snackbar { text = 'Saved', dismissAction = 'clear' }
            }
        """) as LuaUiResult.Success
        val scaffold = result.root as UiNode.Scaffold
        assertEquals(UiNode.Alert(null, null, null, null, "dismiss"), scaffold.alert)
        assertEquals(UiNode.Snackbar("Saved", "clear"), scaffold.snackbar)
        assertEquals("back", scaffold.toolbar!!.backAction)
        assertFalse((scaffold.toolbar.overflow.single() as UiNode.Button).enabled)
    }

    @Test fun `invalid slots menus actions and fields fail validation`() {
        for (script in listOf(
            "ui.scaffold {}",
            "ui.scaffold { content = ui.text { text = 'Body' }, toolbar = ui.text { text = 'Wrong' } }",
            "ui.toolbar { title = 'Title', children = { ui.text { text = 'Wrong' } } }",
            "ui.toolbar { title = 'Title', overflow = { ui.iconButton { icon = 'add', label = 'Add', action = 'add' } } }",
            "ui.toolbar { title = 'Title', backAction = '../back' }",
            "ui.alert { dismissAction = 'dismiss', positive = ui.text { text = 'Wrong' } }",
            "ui.alert { dismissAction = 'dismiss', subtitle = false }",
            "ui.snackbar { text = 'Saved', dismissAction = 'clear', extra = true }",
            "ui.toolbar { title = 'Title', overflow = { [2] = ui.button { text = 'Retry', action = 'retry' } } }",
        )) assertTrue(script, engine.evaluate("return $script") is LuaUiResult.Failure)
    }

    @Test fun `alert admits only its buttons and dismissal then restores toolbar and content actions`() {
        val session = LuaSession("""
            local visible, message, count = true, 'Saved', 0
            return {
              render = function() return ui.scaffold {
                content = ui.button { text = tostring(count), action = 'body' },
                toolbar = ui.toolbar { title = 'Title', backAction = 'back', overflow = {
                  ui.button { text = 'Retry', action = 'retry' },
                  ui.button { text = 'Disabled', action = 'disabled', enabled = false }
                } },
                alert = visible and ui.alert { dismissAction = 'dismiss', positive = ui.button { text = 'OK', action = 'confirm' } } or nil,
                snackbar = message and ui.snackbar { text = message, dismissAction = 'clear' } or nil
              } end,
              onEvent = function(event)
                if event.action == 'dismiss' or event.action == 'confirm' then visible = false
                elseif event.action == 'clear' then message = nil
                else count = count + 1 end
              end
            }
        """)
        fun root(result: LuaUiResult) = (result as LuaUiResult.Success).root as UiNode.Scaffold
        session.start()
        for (action in listOf("body", "back", "retry", "clear")) {
            val scaffold = root(session.dispatch(LuaEvent.Action(action)))
            assertEquals("0", (scaffold.content as UiNode.Button).text)
            assertNotNull(scaffold.alert)
            assertNotNull(scaffold.snackbar)
        }
        assertNull(root(session.dispatch(LuaEvent.Action("confirm"))).alert)
        assertEquals("0", (root(session.dispatch(LuaEvent.Action("disabled"))).content as UiNode.Button).text)
        for ((index, action) in listOf("body", "back", "retry").withIndex()) {
            assertEquals((index + 1).toString(), (root(session.dispatch(LuaEvent.Action(action))).content as UiNode.Button).text)
        }
        assertNull(root(session.dispatch(LuaEvent.Action("clear"))).snackbar)
        session.close()
    }
}
