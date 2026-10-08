package com.example.luaplayground

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class AppContainerTest {
    private val assets = AssetManager(
        listFiles = { arrayOf("hello.luac") },
        readFile = { "print('hello')".encodeToByteArray() },
        readAppFile = { compiled("app.luac") },
        readPageFile = { compiled(it) },
    )
    private val engine = LuaEngine()
    private val container = AppContainer(assets, engine, AppNavigator)

    @Test fun `native features receive shared services`() {
        val console = container.console()

        assertSame(assets, console.assetManager)
        assertSame(engine, console.luaEngine)
        assertSame(AppNavigator, console.navigator)
        assertSame(AppNavigator, container.dashboard().navigator)
        assertSame(container.luaUiEngine, container.dashboard().engine)
        assertEquals("hello.luac", console.createVm().state.selectedScript)
    }

    @Test fun `native feature containers keep stable identities`() {
        assertSame(container.console(), container.console())
        assertSame(container.dashboard(), container.dashboard())
    }

    @Test fun `start atomically publishes Lua routes and stable page containers`() {
        var scheduled: (() -> Unit)? = null

        container.start { scheduled = it }

        assertSame(LuaRoutesState.Loading, container.routeRegistry.state.value)
        scheduled!!()
        val ready = container.routeRegistry.state.value as LuaRoutesState.Ready
        assertEquals("playground", ready.startRoute)
        assertEquals(
            listOf(LuaRoute("playground", "playground.luac"), LuaRoute("todo", "todo.luac")),
            ready.routes,
        )
        assertSame(container.page("todo"), container.page("todo"))
    }

    @Test fun `missing page asset fails without publishing partial routes`() {
        val app = AppContainer(
            AssetManager(
                listFiles = { emptyArray() },
                readFile = { error("unexpected console read: $it") },
                readAppFile = { compiled("app.luac") },
                readPageFile = { if (it == "todo.luac") error("missing") else compiled(it) },
            ),
        )

        app.start { it() }

        assertSame(LuaRoutesState.Failed, app.routeRegistry.state.value)
    }

    @Test fun `startup requires exactly one start route`() {
        val app = AppContainer(
            AssetManager(
                listFiles = { emptyArray() },
                readFile = { byteArrayOf() },
                readAppFile = {
                    "setStartRoute('console'); setStartRoute('dashboard')".encodeToByteArray()
                },
            ),
        )

        app.start { it() }

        assertSame(LuaRoutesState.Failed, app.routeRegistry.state.value)
    }

    @Test fun `oversized page fails before routes are published`() {
        val app = AppContainer(
            AssetManager(
                listFiles = { emptyArray() },
                readFile = { byteArrayOf() },
                readAppFile = {
                    "registerRoute('page', 'page.luac'); setStartRoute('page')".encodeToByteArray()
                },
                readPageFile = { ByteArray(com.example.luacompose.LuaUiEngine.MAX_SCRIPT_BYTES + 1) },
            ),
        )

        app.start { it() }

        assertSame(LuaRoutesState.Failed, app.routeRegistry.state.value)
    }

    private fun compiled(name: String) = File("build/generated/luaAssets/$name").readBytes()
}
