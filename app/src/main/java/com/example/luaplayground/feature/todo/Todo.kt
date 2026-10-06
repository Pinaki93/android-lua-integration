package com.example.luaplayground.feature.todo

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.luacompose.JsonStore
import com.example.luacompose.LuaEvent
import com.example.luacompose.LuaSession
import com.example.luacompose.LuaUi
import com.example.luacompose.LuaUiResult
import com.example.luacompose.UiInput
import com.example.luacompose.UiNode
import com.example.luaplayground.AssetManager
import com.example.luaplayground.Navigator
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Immutable
class TodoContainer(
    private val assets: AssetManager,
    val navigator: Navigator,
    private val store: JsonStore,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    fun createVm() = LuaSessionVm(assets.readTodo(), store, io)
}

class LuaSessionVm(
    script: String,
    store: JsonStore,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val session = LuaSession(script, store)
    private val events = Channel<LuaEvent>(Channel.UNLIMITED)

    var result by mutableStateOf<LuaUiResult>(LuaUiResult.Success(UiNode.Text("Loading…")))
        private set

    init {
        viewModelScope.launch {
            result = withContext(io) { session.start() }
            for (event in events) result = withContext(io) { session.dispatch(event) }
        }
    }

    fun action(action: String) {
        events.trySend(LuaEvent.Action(action))
    }

    fun input(input: UiInput) {
        events.trySend(
            when (input) {
                is UiInput.TextChanged -> LuaEvent.TextChanged(input.action, input.value)
                is UiInput.CheckedChanged -> LuaEvent.CheckedChanged(input.action, input.checked)
            },
        )
    }

    override fun onCleared() {
        events.close()
        session.close()
    }
}

@Composable
fun TodoFeature(container: TodoContainer) {
    val vm = viewModel { container.createVm() }
    val scope = rememberCoroutineScope()
    BackHandler { scope.launch { container.navigator.popBackStack() } }
    Column(
        Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        OutlinedButton(onClick = { scope.launch { container.navigator.popBackStack() } }) { Text("Back to playground") }
        LuaUi(vm.result, vm::action, Modifier.fillMaxWidth(), vm::input)
    }
}
