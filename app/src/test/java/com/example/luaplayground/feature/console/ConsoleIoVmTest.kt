package com.example.luaplayground.feature.console

import com.example.luaplayground.AssetManager
import com.example.luaplayground.LuaEngine
import com.example.luaplayground.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConsoleIoVmTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    @Test fun `initial state selects and reads the first sorted Lua script`() {
        val viewModel = viewModel(
            linkedMapOf(
                "second.luac" to "print('second')",
                "first.luac" to "print('first')",
            ),
        )

        assertEquals(listOf("first.luac", "second.luac"), viewModel.state.scripts)
        assertEquals("first.luac", viewModel.state.selectedScript)
        assertEquals("Precompiled Lua bytecode", viewModel.state.source)
    }

    @Test fun `empty script directory produces an idle empty state`() {
        val viewModel = viewModel(emptyMap())

        assertEquals(LuaUiState(), viewModel.state)
    }

    @Test fun `input is owned by the ViewModel`() {
        val viewModel = viewModel()

        viewModel.updateInput("Ada\nLovelace")

        assertEquals("Ada\nLovelace", viewModel.state.input)
    }

    @Test fun `selecting a script loads its source and clears old output`() {
        val viewModel = viewModel(
            mapOf(
                "first.luac" to "print('first')",
                "second.luac" to "print('second')",
            ),
        )
        viewModel.runScript()
        mainDispatcher.dispatcher.scheduler.advanceUntilIdle()

        viewModel.selectScript("second.luac")

        assertEquals("second.luac", viewModel.state.selectedScript)
        assertEquals("Precompiled Lua bytecode", viewModel.state.source)
        assertEquals("", viewModel.state.output)
    }

    @Test fun `selecting the current script does not read it again`() {
        var reads = 0
        val scripts = AssetManager(
            listFiles = { arrayOf("only.luac") },
            readFile = { reads++; "print('ok')".encodeToByteArray() },
        )
        val viewModel = ConsoleIoVm(scripts, LuaEngine(), mainDispatcher.dispatcher)

        viewModel.selectScript("only.luac")

        assertEquals(1, reads)
    }

    @Test fun `unknown script selection is ignored`() {
        val viewModel = viewModel()
        val initial = viewModel.state

        viewModel.selectScript("missing.lua")

        assertEquals(initial, viewModel.state)
    }

    @Test fun `successful run passes source and input to the engine`() {
        val viewModel = viewModel(mapOf("echo.luac" to "print(io.read())"))
        viewModel.updateInput("hello")

        viewModel.runScript()
        assertTrue(viewModel.state.running)
        assertEquals("Running…", viewModel.state.output)
        mainDispatcher.dispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.state.running)
        assertEquals("hello\n", viewModel.state.output)
    }

    @Test fun `runtime error is shown without a leading blank line`() {
        val viewModel = viewModel(mapOf("broken.luac" to "error('boom')"))

        viewModel.runScript()
        mainDispatcher.dispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.state.output.startsWith("Error: "))
        assertTrue(viewModel.state.output.contains("boom"))
    }

    @Test fun `runtime error keeps output produced before failure`() {
        val viewModel = viewModel(mapOf("broken.luac" to "print('before'); error('boom')"))

        viewModel.runScript()
        mainDispatcher.dispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.state.output.startsWith("before\nError: "))
        assertTrue(viewModel.state.output.contains("boom"))
    }

    @Test fun `empty script list cannot run`() {
        val viewModel = viewModel(emptyMap())

        viewModel.runScript()
        mainDispatcher.dispatcher.scheduler.advanceUntilIdle()

        assertEquals(LuaUiState(), viewModel.state)
    }

    @Test fun `injected engine can run repeatedly`() {
        val viewModel = viewModel()

        repeat(2) {
            viewModel.runScript()
            mainDispatcher.dispatcher.scheduler.advanceUntilIdle()
        }

        assertEquals("main\n", viewModel.state.output)
    }

    @Test fun `duplicate run and selection are ignored while execution is queued`() {
        val viewModel = viewModel()

        viewModel.runScript()
        viewModel.runScript()
        viewModel.selectScript("other.luac")

        assertTrue(viewModel.state.running)
        assertEquals("main.luac", viewModel.state.selectedScript)
        mainDispatcher.dispatcher.scheduler.advanceUntilIdle()
        assertEquals("main\n", viewModel.state.output)
    }

    @Test fun `script selection keeps entered input`() {
        val viewModel = viewModel()
        viewModel.updateInput("keep me")

        viewModel.selectScript("other.luac")

        assertEquals("keep me", viewModel.state.input)
    }

    private fun viewModel(
        files: Map<String, String> = mapOf(
            "main.luac" to "print('main')",
            "other.luac" to "print('other')",
        ),
    ) = ConsoleIoVm(
        scripts = AssetManager(
            listFiles = { files.keys.toTypedArray() },
            readFile = { files.getValue(it).encodeToByteArray() },
        ),
        engine = LuaEngine(),
        worker = mainDispatcher.dispatcher,
    )
}
