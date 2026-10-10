package com.example.luaplayground.feature.todo

import com.example.luacompose.JsonStore
import com.example.luacompose.LuaEvent
import com.example.luacompose.LuaSession
import com.example.luacompose.LuaUiResult
import com.example.luacompose.UiNode
import com.example.luacompose.UiInput
import com.example.luaplayground.MainDispatcherRule
import com.example.luaplayground.feature.dynamic.LuaContainerVm
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Rule

@OptIn(ExperimentalCoroutinesApi::class)
class TodoTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test fun `rapid typing updates immediately and keeps the newest value`() = runTest(mainDispatcherRule.dispatcher) {
        val memory = MemoryStore()
        val vm = LuaContainerVm(java.io.File("build/generated/luaAssets/todo.luac").readBytes(), { memory.store }, mainDispatcherRule.dispatcher)
        assertTrue(vm.isLoading)
        advanceUntilIdle()
        assertFalse(vm.isLoading)

        val text = "abcdefghijklmnop"
        for (length in 1..text.length) {
            val value = text.take(length)
            vm.input(UiInput.TextChanged("todo.draft", value))
            assertEquals(value, vm.result.field().value)
        }

        advanceUntilIdle()
        assertEquals(text, vm.result.field().value)
    }

    @Test fun `toggling a task updates immediately`() = runTest(mainDispatcherRule.dispatcher) {
        val memory = MemoryStore(
            """{"version":1,"nextId":2,"items":[{"id":1,"title":"Task","completed":false}]}"""
        )
        val vm = LuaContainerVm(java.io.File("../lua/todo.lua").readText(), { memory.store }, mainDispatcherRule.dispatcher)
        advanceUntilIdle()

        vm.input(UiInput.CheckedChanged("todo.toggle.1", true))

        assertTrue(vm.result.checkboxes().single().checked)
        advanceUntilIdle()
        assertTrue(vm.result.checkboxes().single().checked)
    }

    @Test fun `typing then adding uses the complete text and clears the field`() = runTest(mainDispatcherRule.dispatcher) {
        val memory = MemoryStore()
        val vm = LuaContainerVm(java.io.File("../lua/todo.lua").readText(), { memory.store }, mainDispatcherRule.dispatcher)
        advanceUntilIdle()

        vm.input(UiInput.TextChanged("todo.draft", "Queued"))
        assertEquals("Queued", vm.result.field().value)
        vm.action("todo.add")
        advanceUntilIdle()

        assertTrue(vm.result.texts().contains("Queued"))
        assertEquals("", vm.result.field().value)
        assertEquals(1, memory.writes)
    }

    @Test fun `rejected over-limit typing is reverted by Lua`() = runTest(mainDispatcherRule.dispatcher) {
        val memory = MemoryStore()
        val vm = LuaContainerVm(java.io.File("../lua/todo.lua").readText(), { memory.store }, mainDispatcherRule.dispatcher)
        advanceUntilIdle()
        val accepted = "😀".repeat(200)
        vm.input(UiInput.TextChanged("todo.draft", accepted))
        advanceUntilIdle()

        val rejected = "😀".repeat(201)
        vm.input(UiInput.TextChanged("todo.draft", rejected))
        assertEquals(rejected, vm.result.field().value)
        advanceUntilIdle()

        assertEquals(accepted, vm.result.field().value)
        assertEquals("Task titles can be at most 200 characters.", vm.result.field().error)
    }

    @Test fun `empty start add restore toggle and delete use stable numeric IDs`() {
        val memory = MemoryStore()
        val first = session(memory)
        assertTrue(first.start().texts().contains("No tasks yet. Add one above."))
        first.dispatch(LuaEvent.TextChanged("todo.draft", "  Walk outside  "))
        val added = first.dispatch(LuaEvent.Action("todo.add"))
        assertTrue(added.texts().contains("Walk outside"))
        assertEquals(1, memory.writes)
        assertTrue(memory.text().contains("\"nextId\":2"))

        val restored = session(memory)
        assertTrue(restored.start().texts().contains("Walk outside"))
        val toggled = restored.dispatch(LuaEvent.CheckedChanged("todo.toggle.1", true))
        assertTrue(toggled.checkboxes().single().checked)
        val writes = memory.writes
        restored.dispatch(LuaEvent.CheckedChanged("todo.toggle.1", true))
        restored.dispatch(LuaEvent.CheckedChanged("todo.toggle.99", false))
        assertEquals(writes, memory.writes)
        assertTrue(restored.dispatch(LuaEvent.Action("todo.delete.1")).texts().contains("No tasks yet. Add one above."))
    }

    @Test fun `layout uses warm summary cards and list items with icon actions`() {
        val memory = MemoryStore(
            """{"version":1,"nextId":3,"items":[{"id":1,"title":"Done","completed":true},{"id":2,"title":"Next","completed":false}]}"""
        )

        val result = session(memory).start()
        val root = (result as LuaUiResult.Success).root as UiNode.Column
        val cards = root.children.filterIsInstance<UiNode.Card>()
        val listItems = root.children.filterIsInstance<UiNode.ListItem>()

        assertEquals(12, root.gap)
        assertTrue(result.texts().containsAll(listOf("1", "task left to finish", "1 completed", "98 spaces available")))
        assertEquals(com.example.luacompose.UiCardStyle.Orange, cards[0].style)
        assertEquals(com.example.luacompose.UiCardStyle.LightOrange, cards[1].style)
        assertEquals(
            listOf("1 completed", "98 spaces available"),
            cards[0].children.flatMap { it.flatten() }.filterIsInstance<UiNode.Row>().single().children
                .filterIsInstance<UiNode.Text>().map { it.text },
        )
        assertEquals(listOf("todo.1", "todo.2"), listItems.map { it.key })
        val rows = listItems.map { it.children.single() as UiNode.Row }
        val texts = rows.map { it.children[1] as UiNode.Text }
        assertEquals(listOf("Done", "Next"), texts.map { it.text })
        assertEquals(listOf(true, false), texts.map { it.strikeThrough })
        assertTrue(texts.all { it.weight })
        assertEquals(listOf("todo.delete.1", "todo.delete.2"), rows.map { (it.children[2] as UiNode.IconButton).action })
        assertTrue(result.nodes().filterIsInstance<UiNode.Button>().none { it.text == "Add Task" })

        val typing = session(memory).apply { start() }.dispatch(LuaEvent.TextChanged("todo.draft", "New task"))
        assertEquals(com.example.luacompose.UiTextFieldStyle.Plain, typing.field().style)
        assertTrue(typing.nodes().filterIsInstance<UiNode.Button>().any {
            it.text == "Add Task" && it.style == com.example.luacompose.UiButtonStyle.Orange
        })
    }

    @Test fun `draft validation trims blanks and counts unicode code points`() {
        val memory = MemoryStore()
        val session = session(memory)
        assertTrue(session.start().nodes().filterIsInstance<UiNode.Button>().none { it.text == "Add Task" })
        session.dispatch(LuaEvent.TextChanged("todo.draft", "   "))
        val blank = session.dispatch(LuaEvent.Action("todo.add"))
        assertTrue(blank.texts().contains("Enter a task title."))
        assertEquals(0, memory.writes)

        val accepted = session.dispatch(LuaEvent.TextChanged("todo.draft", "😀".repeat(200)))
        assertEquals("😀".repeat(200), accepted.nodes().filterIsInstance<UiNode.TextField>().single().value)
        val latest = session.dispatch(LuaEvent.TextChanged("todo.draft", "😀".repeat(201)))
        val field = latest.nodes().filterIsInstance<UiNode.TextField>().single()
        assertEquals("😀".repeat(200), field.value)
        assertEquals("Task titles can be at most 200 characters.", field.error)
        session.dispatch(LuaEvent.Action("todo.add"))
        assertTrue(memory.text().contains("😀".repeat(200)))
    }

    @Test fun `failed saves preserve committed UI and draft then retry`() {
        val memory = MemoryStore()
        val session = session(memory)
        session.start()
        session.dispatch(LuaEvent.TextChanged("todo.draft", "Retry me"))
        memory.writeFailure = true
        val failed = session.dispatch(LuaEvent.Action("todo.add"))
        assertTrue(failed.texts().contains("Could not save tasks. Please try again."))
        assertEquals("Retry me", failed.nodes().filterIsInstance<UiNode.TextField>().single().value)
        assertTrue(failed.checkboxes().isEmpty())

        memory.writeFailure = false
        val saved = session.dispatch(LuaEvent.Action("todo.add"))
        assertTrue(saved.texts().contains("Retry me"))
        assertEquals("", saved.nodes().filterIsInstance<UiNode.TextField>().single().value)
    }

    @Test fun `corrupt schema blocks mutation until successful reload`() {
        val memory = MemoryStore("""{"version":2,"nextId":1,"items":[]}""")
        val session = session(memory)
        val failed = session.start()
        assertTrue(failed.texts().contains("Could not load saved tasks. Reload before editing."))
        session.dispatch(LuaEvent.TextChanged("todo.draft", "blocked"))
        session.dispatch(LuaEvent.Action("todo.add"))
        assertEquals(0, memory.writes)

        memory.bytes = """{"version":1,"nextId":2,"items":[{"id":1,"title":"Recovered","completed":false}]}""".toByteArray()
        val loaded = session.dispatch(LuaEvent.Action("todo.reload"))
        assertTrue(loaded.texts().contains("Recovered"))
        assertFalse(loaded.texts().contains("Could not load saved tasks. Reload before editing."))
    }

    @Test fun `maximum list renders and refuses another write`() {
        val items = (1..100).joinToString(",") { """{"id":$it,"title":"Task $it","completed":false}""" }
        val memory = MemoryStore("""{"version":1,"nextId":101,"items":[$items]}""")
        val session = session(memory)
        val result = session.start()
        assertEquals(100, result.checkboxes().size)
        assertTrue(result.texts().containsAll(listOf("0 completed", "0 spaces available")))
        session.dispatch(LuaEvent.TextChanged("todo.draft", "Extra"))
        session.dispatch(LuaEvent.Action("todo.add"))
        assertEquals(0, memory.writes)
    }

    private fun session(memory: MemoryStore) = LuaSession(
        java.io.File("../lua/todo.lua").readText(),
        { memory.store },
    )

    private class MemoryStore(initial: String? = null) {
        var bytes = initial?.toByteArray()
        var writes = 0
        var writeFailure = false
        val store = JsonStore(
            read = { bytes?.copyOf() },
            write = { value ->
                if (writeFailure) error("private backend detail")
                writes++
                bytes = value.copyOf()
            },
            delete = { bytes = null },
        )
        fun text() = bytes!!.toString(Charsets.UTF_8)
    }

    private fun LuaUiResult.nodes(): List<UiNode> = when (this) {
        is LuaUiResult.Failure -> emptyList()
        is LuaUiResult.Success -> root.flatten()
    }

    private fun LuaUiResult.texts() = nodes().flatMap {
        when (it) {
            is UiNode.Button -> listOf(it.text)
            is UiNode.Checkbox -> listOf(it.label)
            is UiNode.Text -> listOf(it.text)
            is UiNode.TextField -> listOf(it.label, it.value) + listOfNotNull(it.error)
            else -> emptyList()
        }
    }

    private fun LuaUiResult.checkboxes() = nodes().filterIsInstance<UiNode.Checkbox>()

    private fun LuaUiResult.field() = nodes().filterIsInstance<UiNode.TextField>().single()

    private fun UiNode.flatten(): List<UiNode> = listOf(this) + when (this) {
        is UiNode.Card -> children.flatMap { it.flatten() }
        is UiNode.Column -> children.flatMap { it.flatten() }
        is UiNode.Row -> children.flatMap { it.flatten() }
        is UiNode.ListItem -> children.flatMap { it.flatten() }
        else -> emptyList()
    }
}
