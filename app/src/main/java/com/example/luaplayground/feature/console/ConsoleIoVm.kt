package com.example.luaplayground.feature.console

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.luaplayground.AssetManager
import com.example.luaplayground.LuaEngine
import com.example.luaplayground.LuaResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Immutable
data class LuaUiState(
    val scripts: List<String> = emptyList(),
    val selectedScript: String = "",
    val source: String = "",
    val input: String = "",
    val output: String = "",
    val running: Boolean = false,
)

class ConsoleIoVm(
    private val scripts: AssetManager,
    private val engine: LuaEngine,
    private val worker: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private var bytecode = ByteArray(0)
    var state by mutableStateOf(initialState())
        private set

    fun updateInput(input: String) {
        state = state.copy(input = input)
    }

    fun selectScript(name: String) {
        if (state.running || name !in state.scripts || name == state.selectedScript) return
        bytecode = scripts.read(name)
        state = state.copy(selectedScript = name, source = BYTECODE_LABEL, output = "")
    }

    fun runScript() {
        if (state.running || state.selectedScript.isEmpty()) return
        val name = state.selectedScript
        val script = bytecode
        val input = state.input
        state = state.copy(output = "Running…", running = true)

        viewModelScope.launch {
            val result = withContext(worker) { engine.execute(script, input, name) }
            state = state.copy(output = result.displayText(), running = false)
        }
    }

    private fun initialState(): LuaUiState {
        val names = scripts.names()
        val selected = names.firstOrNull().orEmpty()
        bytecode = selected.takeIf(String::isNotEmpty)?.let(scripts::read) ?: ByteArray(0)
        return LuaUiState(
            scripts = names,
            selectedScript = selected,
            source = if (selected.isEmpty()) "" else BYTECODE_LABEL,
        )
    }

    private fun LuaResult.displayText() = error?.let {
        listOf(output.trimEnd(), "Error: $it").filter(String::isNotEmpty).joinToString("\n")
    } ?: output

    private companion object {
        const val BYTECODE_LABEL = "Precompiled Lua bytecode"
    }
}
