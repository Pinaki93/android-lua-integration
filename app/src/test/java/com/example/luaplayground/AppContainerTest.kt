package com.example.luaplayground

import com.example.luacompose.JsonStore
import com.example.luaplayground.feature.playground.PlaygroundVm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AppContainerTest {
    private val assets = AssetManager(
        listFiles = { arrayOf("hello.luac") },
        readFile = { "print('hello')".encodeToByteArray() },
    )
    private val engine = LuaEngine()
    private val container = AppContainer(assets, engine, AppNavigator)

    @Test fun `console receives the shared app services and creates its ViewModel`() {
        val console = container.console()

        assertSame(assets, console.assetManager)
        assertSame(engine, console.luaEngine)
        assertSame(AppNavigator, console.navigator)
        assertEquals("hello.luac", console.createVm().state.selectedScript)
    }

    @Test fun `features receive the shared navigator`() {
        assertSame(AppNavigator, container.console().navigator)
        assertSame(AppNavigator, container.dashboard().navigator)
        assertSame(AppNavigator, container.playground().navigator)
        assertSame(container.luaUiEngine, container.dashboard().engine)
        assertEquals(PlaygroundVm::class.java, container.playground().createVm().javaClass)
    }

    @Test fun `feature containers keep stable identities`() {
        assertSame(container.console(), container.console())
        assertSame(container.dashboard(), container.dashboard())
        assertSame(container.playground(), container.playground())
        assertSame(container.todo(), container.todo())
    }

    @Test fun `todo store can be injected without Android context`() {
        val store = JsonStore({ null }, {}, {})
        val app = AppContainer(assets, engine, AppNavigator, todoStore = store)

        assertSame(store, app.todoStore)
    }

    @Test fun `start schedules the app Lua script`() {
        var scheduled: (() -> Unit)? = null
        var todoReads = 0
        val app = AppContainer(
            assetManager = AssetManager(
                listFiles = { emptyArray() },
                readFile = { error("unexpected console read: $it") },
                readAppFile = { "started = true".encodeToByteArray() },
                readTodoFile = {
                    todoReads++
                    java.io.File("build/generated/luaAssets/todo.luac").readBytes()
                },
            ),
        )

        app.start { scheduled = it }

        assertTrue(scheduled != null)
        scheduled!!()
        assertEquals(1, todoReads)
    }
}
