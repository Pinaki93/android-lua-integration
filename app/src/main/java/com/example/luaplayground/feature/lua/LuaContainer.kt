package com.example.luaplayground.feature.lua

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.luacompose.JsonStore
import com.example.luacompose.LuaEvent
import com.example.luacompose.LuaSession
import com.example.luacompose.LuaUiResult
import com.example.luacompose.UiInput
import com.example.luacompose.UiNode
import com.example.luaplayground.Navigator
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Immutable
class LuaContainer(
    private val script: () -> ByteArray,
    val navigator: Navigator,
    private val store: JsonStore? = null,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private var prepared: Pair<LuaSession, LuaUiResult>? = null
    private var preparingAllowed = true

    fun createVm(): LuaContainerVm {
        val ready = synchronized(this) {
            preparingAllowed = false
            prepared.also { prepared = null }
        }
        return ready?.let { LuaContainerVm(it.first, io, it.second) }
            ?: LuaContainerVm(script(), store, io)
    }

    fun warmUp() {
        val session = LuaSession(script(), store)
        val ready = session.start()
        val kept = synchronized(this) {
            if (preparingAllowed && prepared == null) {
                prepared = session to ready
                true
            } else false
        }
        if (!kept) session.close()
    }
}

class LuaContainerVm internal constructor(
    private val session: LuaSession,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    initial: LuaUiResult? = null,
) : ViewModel() {
    constructor(script: ByteArray, store: JsonStore? = null, io: CoroutineDispatcher = Dispatchers.IO) :
        this(LuaSession(script, store), io)
    constructor(script: String, store: JsonStore? = null, io: CoroutineDispatcher = Dispatchers.IO) :
        this(LuaSession(script, store), io)

    private val events = Channel<QueuedEvent>(Channel.UNLIMITED)
    private var revision = 0L

    var result by mutableStateOf(initial ?: LuaUiResult.Success(UiNode.Text("Loading…")))
        private set
    var isLoading by mutableStateOf(initial == null)
        private set

    init {
        viewModelScope.launch {
            if (initial == null) {
                result = withContext(io) { session.start() }
                isLoading = false
            }
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
