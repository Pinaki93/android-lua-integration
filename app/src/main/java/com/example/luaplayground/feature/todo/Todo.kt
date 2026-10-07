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

class LuaSessionVm private constructor(
    private val session: LuaSession,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    constructor(script: ByteArray, store: JsonStore, io: CoroutineDispatcher = Dispatchers.IO) :
        this(LuaSession(script, store), io)
    constructor(script: String, store: JsonStore, io: CoroutineDispatcher = Dispatchers.IO) :
        this(LuaSession(script, store), io)
    private val events = Channel<QueuedEvent>(Channel.UNLIMITED)
    private var revision = 0L

    var result by mutableStateOf<LuaUiResult>(LuaUiResult.Success(UiNode.Text("Loading…")))
        private set

    init {
        viewModelScope.launch {
            result = withContext(io) { session.start() }
            for (queued in events) {
                val next = withContext(io) { session.dispatch(queued.event) }
                if (queued.revision == revision) result = next
            }
        }
    }

    fun action(action: String) {
        enqueue(LuaEvent.Action(action))
    }

    fun input(input: UiInput) {
        val event = when (input) {
            is UiInput.TextChanged -> LuaEvent.TextChanged(input.action, input.value)
            is UiInput.CheckedChanged -> LuaEvent.CheckedChanged(input.action, input.checked)
        }
        if (enqueue(event) && input is UiInput.TextChanged) {
            val current = result
            if (current is LuaUiResult.Success) {
                result = LuaUiResult.Success(current.root.withText(input.action, input.value))
            }
        }
    }

    private fun enqueue(event: LuaEvent): Boolean {
        val next = revision + 1
        if (events.trySend(QueuedEvent(next, event)).isFailure) return false
        revision = next
        return true
    }

    override fun onCleared() {
        events.close()
        session.close()
    }

    private data class QueuedEvent(val revision: Long, val event: LuaEvent)
}

private fun UiNode.withText(action: String, value: String): UiNode = when (this) {
    is UiNode.Card -> UiNode.Card(children.map { it.withText(action, value) })
    is UiNode.Column -> UiNode.Column(children.map { it.withText(action, value) }, gap)
    is UiNode.Row -> UiNode.Row(children.map { it.withText(action, value) }, gap)
    is UiNode.TextField -> if (enabled && this.action == action) copy(value = value) else this
    else -> this
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
