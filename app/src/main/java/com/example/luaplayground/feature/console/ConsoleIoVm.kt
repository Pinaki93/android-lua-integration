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
    var state by mutableStateOf(initialState())
        private set

    fun updateInput(input: String) {
        state = state.copy(input = input)
    }

    fun selectScript(name: String) {
        if (state.running || name !in state.scripts || name == state.selectedScript) return
        state = state.copy(selectedScript = name, source = scripts.read(name), output = "")
    }

    fun runScript() {
        if (state.running || state.selectedScript.isEmpty()) return
        val name = state.selectedScript
        val source = state.source
        val input = state.input
        state = state.copy(output = "Running…", running = true)

        viewModelScope.launch {
            val result = withContext(worker) { engine.execute(source, input, name) }
            state = state.copy(output = result.displayText(), running = false)
        }
    }

    private fun initialState(): LuaUiState {
        val names = scripts.names()
        val selected = names.firstOrNull().orEmpty()
        return LuaUiState(
            scripts = names,
            selectedScript = selected,
            source = selected.takeIf(String::isNotEmpty)?.let(scripts::read).orEmpty(),
        )
    }

    private fun LuaResult.displayText() = error?.let {
        listOf(output.trimEnd(), "Error: $it").filter(String::isNotEmpty).joinToString("\n")
    } ?: output
}
