package com.example.luaplayground

import androidx.lifecycle.ViewModelStore
import com.example.luacompose.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReadingListIntegrationTest {
    @get:Rule val main = MainDispatcherRule()

    @Test fun `bundled screen queues rapid saves title completion and edits without duplicate submissions`() = runTest(main.dispatcher) {
        var saved: ByteArray? = null
        var writes = 0
        val memory = JsonStore({ saved }, { saved = it.copyOf(); writes++ }, {})
        val title = CompletableDeferred<LuaHttpClient.Response>()
        var fetches = 0
        val container = com.example.luaplayground.feature.dynamic.LuaContainer(
            script = { compiledReadingFixture() },
            navigator = AppNavigator,
            storage = { name -> assertEquals("reading-list.json", name); memory },
            io = main.dispatcher,
            reading = ReadingCapabilities(
                fetchTitle = { fetches++; title.await() }, openOriginal = {},
                identifier = { "article-1" }, now = { 1000L },
            ),
        )
        val vm = container.createVm()
        val owner = ViewModelStore().apply { put("reading", vm) }
        runCurrent()
        vm.action("reading.add")
        runCurrent()
        vm.input(UiInput.TextChanged("reading.field.url", "https://example.com/article"))
        vm.input(UiInput.TextChanged("reading.field.note", "Offline note"))
        vm.action("reading.save")
        vm.action("reading.save")
        runCurrent()
        assertEquals(1, writes)
        assertEquals(1, fetches)
        title.complete(LuaHttpClient.Response(body = "Fetched title"))
        vm.input(UiInput.TextChanged("reading.field.title", "My title"))
        runCurrent()
        assertEquals("My title", vm.result.nodes().filterIsInstance<UiNode.TextField>().single { it.label == "Title" }.value)
        vm.action("reading.save")
        runCurrent()
        assertEquals(3, writes)
        assertTrue(saved!!.toString(Charsets.UTF_8).contains("My title"))
        owner.clear()
        runCurrent()
    }

    @Test fun `system back action returns add and edit forms to list without saving drafts`() = runTest(main.dispatcher) {
        var saved: ByteArray? = null
        val container = com.example.luaplayground.feature.dynamic.LuaContainer(
            script = { compiledReadingFixture() },
            navigator = AppNavigator,
            storage = { JsonStore({ saved }, { saved = it.copyOf() }, {}) },
            io = main.dispatcher,
            reading = ReadingCapabilities(
                fetchTitle = { LuaHttpClient.Response(body = "Article") }, openOriginal = {},
                identifier = { "article-1" }, now = { 1000L },
            ),
        )
        val vm = container.createVm()
        val owner = ViewModelStore().apply { put("reading", vm) }
        runCurrent()
        assertNull(vm.readingBackAction)
        vm.action("reading.add")
        runCurrent()
        assertEquals("reading.back", vm.readingBackAction)
        vm.input(UiInput.TextChanged("reading.field.note", "Unsaved draft"))
        vm.action(vm.readingBackAction!!)
        runCurrent()
        assertNull(vm.readingBackAction)
        assertTrue(vm.result.nodes().filterIsInstance<UiNode.TextField>().isEmpty())
        assertNull(saved)

        vm.action("reading.add")
        runCurrent()
        vm.input(UiInput.TextChanged("reading.field.url", "https://example.com/article"))
        vm.action("reading.save")
        runCurrent()
        val beforeEdit = saved!!.copyOf()
        assertEquals("reading.back", vm.readingBackAction)
        vm.input(UiInput.TextChanged("reading.field.note", "Unsaved edit"))
        vm.action(vm.readingBackAction!!)
        runCurrent()
        assertNull(vm.readingBackAction)
        assertTrue(vm.result.nodes().filterIsInstance<UiNode.TextField>().isEmpty())
        assertArrayEquals(beforeEdit, saved)
        val texts = vm.result.nodes().filterIsInstance<UiNode.Text>().map { it.text }
        assertTrue(texts.contains("Article"))
        assertFalse(texts.any { "Title:" in it })
        vm.action("reading.view.article-1")
        runCurrent()
        vm.action("reading.edit")
        runCurrent()
        assertEquals("reading.back", vm.readingBackAction)
        vm.action(vm.readingBackAction!!)
        runCurrent()
        assertNull(vm.readingBackAction)
        owner.clear()
        runCurrent()
    }

    private fun compiled(name: String) = File("build/generated/luaAssets/$name").readBytes()
}

private fun LuaUiResult.nodes(): List<UiNode> = if (this is LuaUiResult.Success) root.nodes() else emptyList()
private fun UiNode.nodes(): List<UiNode> = listOf(this) + when (this) {
    is UiNode.Scaffold -> listOfNotNull(content, toolbar, alert, snackbar, bottomSheet).flatMap { it.nodes() }
    is UiNode.Toolbar -> (children + overflow).flatMap { it.nodes() }
    is UiNode.Alert -> listOfNotNull(positive, negative).flatMap { it.nodes() }
    is UiNode.Column -> children.flatMap { it.nodes() }
    is UiNode.Row -> children.flatMap { it.nodes() }
    is UiNode.Card -> children.flatMap { it.nodes() }
    is UiNode.BottomSheet -> content.nodes()
    is UiNode.Dialog -> children.flatMap { it.nodes() }
    is UiNode.ListItem -> children.flatMap { it.nodes() }
    else -> emptyList()
}
