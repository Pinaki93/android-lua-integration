package com.example.luaplayground

import com.example.luaplayground.feature.playground.PlaygroundVm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class AppContainerTest {
    private val assets = AssetManager(
        listFiles = { arrayOf("hello.lua") },
        readFile = { "print('hello')" },
    )
    private val engine = LuaEngine()
    private val container = AppContainer(assets, engine, AppNavigator)

    @Test fun `console receives the shared app services and creates its ViewModel`() {
        val console = container.console()

        assertSame(assets, console.assetManager)
        assertSame(engine, console.luaEngine)
        assertSame(AppNavigator, console.navigator)
        assertEquals("hello.lua", console.createVm().state.selectedScript)
    }

    @Test fun `features receive the shared navigator`() {
        assertSame(AppNavigator, container.console().navigator)
        assertSame(AppNavigator, container.playground().navigator)
        assertEquals(PlaygroundVm::class.java, container.playground().createVm().javaClass)
    }

    @Test fun `feature containers keep stable identities`() {
        assertSame(container.console(), container.console())
        assertSame(container.playground(), container.playground())
    }
}
