package com.example.luaplayground

import com.example.luacompose.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class LuaFeatureTest {
    @Test fun `feature index registers prefixed routes and shares its theme`() {
        val routes = mutableListOf<Pair<String, String>>()
        val themes = mutableMapOf<String, Map<String, Long>>()
        val result = LuaEngine().executeApp(
            "loadFeature('reading-list')".encodeToByteArray(),
            { route, script -> routes += route to script }, {},
            readFeature = { File("../lua/${it.removeSuffix(".luac")}.lua").readBytes() },
            registerTheme = { feature, colors -> themes[feature] = colors },
        )
        assertTrue(result.succeeded)
        assertEquals(listOf(
            "reading-list/index" to "reading-list/reading-list-controller.luac",
            "reading-list/add" to "reading-list/reading-list-add-controller.luac",
            "reading-list/edit/{id}" to "reading-list/reading-list-add-controller.luac",
        ), routes)
        assertEquals(0xFF176047L, themes.getValue("reading-list")["primary"])
        assertEquals(0xFFEEECE6L, themes.getValue("reading-list")["toolbar"])
    }

    @Test fun `feature hooks reject unsafe names wrong types and invalid themes`() {
        listOf(
            "registerSubRoute('../outside')", "loadFeature('../outside')",
            "registerSubRoute(2)", "registerSubRoute('items').registerRoute('index')",
            "registerSubRoute('items').registerTheme { primary = '#123' }",
            "registerSubRoute('items').registerTheme { unknown = '#123456' }",
            "registerSubRoute('items').registerTheme { primary = 123456 }",
            "registerSubRoute('items').registerTheme { toolbar = '#123' }",
            "registerSubRoute('items').registerTheme { toolbar = 123456 }",
        ).forEach { source ->
            assertFalse(source, LuaEngine().executeApp(source.encodeToByteArray(), { _, _ -> }, {}).succeeded)
        }
    }

    @Test fun `list navigates to add and forms parse ids without changing saved data`() = runTest {
        var saved: ByteArray? = null
        val store = JsonStore({ saved }, { saved = it }, {})
        val routes = mutableListOf<String>()
        var backs = 0
        val modules = listOf("base-controller", "reading-interactor", "common-ui", "reading-list", "reading-list-add", "reading-list-add-controller").associateWith {
            File("../lua/reading-list/$it.lua").readBytes()
        }
        fun session(file: String, arguments: Map<String, String> = emptyMap()) = LuaSession(
            File("../lua/reading-list/$file.lua").readText(), { store },
            navigation = LuaNavigation(arguments, { routes += it }, { backs++ }),
            scope = this, completed = { _, _ -> }, modules = modules,
            reading = ReadingCapabilities(fetchTitle = { LuaHttpClient.Response(error = "transport") },
                openOriginal = {}, identifier = { "article-1" }, now = { 1000L }),
        )
        val list = session("reading-list-controller")
        assertTrue(list.start() is LuaUiResult.Success)
        list.dispatch(LuaEvent.Action("reading.add"))
        assertEquals(listOf("reading-list/add"), routes)
        val add = session("reading-list-add-controller")
        assertTrue(add.start() is LuaUiResult.Success)
        add.dispatch(LuaEvent.TextChanged("reading.field.url", "https://example.com/article"))
        add.dispatch(LuaEvent.TextChanged("reading.field.title", "Saved title"))
        assertTrue(add.dispatch(LuaEvent.Action("reading.save")) is LuaUiResult.Success)
        assertNotNull(saved)
        val before = saved!!.copyOf()
        val edit = session("reading-list-add-controller", mapOf("id" to "article-1"))
        val root = (edit.start() as LuaUiResult.Success).root.let { (it as UiNode.Scaffold).content as UiNode.Column }
        val fields = root.children.filterIsInstance<UiNode.Card>().flatMap { it.children }
            .filterIsInstance<UiNode.Column>().flatMap { it.children }.filterIsInstance<UiNode.TextField>()
        assertEquals("Saved title", fields.single { it.label == "Title" }.value)
        assertFalse(fields.single { it.label == "HTTPS URL" }.enabled)
        edit.dispatch(LuaEvent.TextChanged("reading.field.note", "Discard this"))
        edit.dispatch(LuaEvent.Action("reading.back"))
        assertEquals(1, backs)
        val refreshed = (list.resume() as LuaUiResult.Success).root.let { (it as UiNode.Scaffold).content as UiNode.Column }
        assertTrue(refreshed.children.filterIsInstance<UiNode.Text>().any { it.text == "1 saved · 1 unread" })
        list.dispatch(LuaEvent.Action("reading.view.article-1"))
        assertEquals("reading-list/edit/article-1", routes.last())
        list.dispatch(LuaEvent.Action("reading.status.Read"))
        val filtered = (list.resume() as LuaUiResult.Success).root.let { (it as UiNode.Scaffold).content as UiNode.Column }
        assertTrue(filtered.children.filterIsInstance<UiNode.ListItem>().isEmpty())
        val filters = filtered.children.filterIsInstance<UiNode.Row>().flatMap { it.children }
            .filterIsInstance<UiNode.Button>()
        assertEquals("Read (selected)", filters.single { it.action == "reading.status.Read" }.text)
        assertArrayEquals(before, saved)
        val missing = session("reading-list-add-controller", mapOf("id" to "missing"))
        val missingRoot = (missing.start() as LuaUiResult.Success).root
        assertEquals(missingRoot, (missing.dispatch(LuaEvent.Action("reading.reload")) as LuaUiResult.Success).root)
        missing.close()
        list.close(); add.close(); edit.close()
    }

    @Test fun `common ui preserves styles actions and recovery content`() {
        val source = """
            local common = featureModule("common-ui")
            local label = common.text("Title", "title", "secondary")
            assert(label.text == "Title" and label.style == "title" and label.tone == "secondary")
            local selected = common.filter("Unread", "status.Unread", true)
            assert(selected.text == "Unread (selected)" and selected.style == "selected")
            assert(selected.action == "reading.status.Unread")
            local filter = common.filter("All", "status.All", false)
            assert(filter.text == "All" and filter.style == "filter")
            local card = common.card({ label }, "outlined", "view.article")
            assert(card.action == "reading.view.article" and card.style == "outlined")
            assert(card.children[1].gap == 12 and card.children[1].children[1] == label)
            assert(common.card({ label }).action == nil)
            assert(#common.children({ loaded = true }) == 0)
            assert(#common.children({ loaded = true, message = "Saved" }) == 0)
            local children = common.children({ loaded = false, message = "Load failed" })
            assert(children[1].children[1].children[3].action == "reading.reload")
            return { render = function() return ui.column { children = children } end, onEvent = function() end }
        """.trimIndent()
        val session = LuaSession(source, { error("Storage is unused") }, modules = mapOf(
            "common-ui" to File("../lua/reading-list/common-ui.lua").readBytes(),
        ))
        try {
            assertTrue(session.start() is LuaUiResult.Success)
        } finally {
            session.close()
        }
    }

    @Test fun `controller instances keep drafts and callbacks independent`() {
        val source = """
            local first = featureModule("reading-list-add-controller")
            local second = featureModule("reading-list-add-controller")
            local function title(controller)
              return controller.render().content.children[1].children[1].children[2].value
            end
            assert(first ~= second)
            first.onEvent { type = "text", action = "reading.field.title", value = "First draft" }
            assert(title(first) == "First draft")
            assert(title(second) == "")
            second.onEvent { type = "text", action = "reading.field.title", value = "Second draft" }
            assert(title(first) == "First draft")
            assert(title(second) == "Second draft")
            return first
        """.trimIndent()
        val store = JsonStore({ null }, {}, {})
        val session = LuaSession(source, { store }, navigation = LuaNavigation(emptyMap(), {}, {}), modules = listOf("base-controller", "reading-interactor", "reading-list-add-controller", "reading-list-add", "common-ui").associateWith {
            File("../lua/reading-list/$it.lua").readBytes()
        })
        try {
            assertTrue(session.start() is LuaUiResult.Success)
        } finally {
            session.close()
        }
    }

    @Test fun `missing and oversized feature indexes fail startup`() {
        val engine = LuaEngine()
        listOf<(String) -> ByteArray>({ error("missing") }, { ByteArray(LuaUiEngine.MAX_SCRIPT_BYTES + 1) }).forEach { read ->
            assertFalse(engine.executeApp("loadFeature('items')".encodeToByteArray(), { _, _ -> }, {}, readFeature = read).succeeded)
        }
        assertFalse(engine.executeApp(ByteArray(LuaUiEngine.MAX_SCRIPT_BYTES + 1), { _, _ -> }, {}).succeeded)
    }

    @Test fun `modules are explicit and enforce the script limit`() {
        listOf(emptyMap(), mapOf("reading-list-controller" to ByteArray(LuaUiEngine.MAX_SCRIPT_BYTES + 1))).forEach { modules ->
            val session = LuaSession("return featureModule('reading-list-controller')", { error("unused") }, modules = modules)
            assertTrue(session.start() is LuaUiResult.Failure)
        }
    }
}
