package com.example.luaplayground.feature.dynamic

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.luacompose.LuaHttpClient
import com.example.luacompose.JsonStore
import com.example.luacompose.LuaEvent
import com.example.luacompose.LuaNavigation
import com.example.luacompose.LuaSession
import com.example.luacompose.LuaUiResult
import com.example.luacompose.UiInput
import com.example.luacompose.UiNode
import com.example.luaplayground.Navigator
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Immutable
class LuaContainer(
    private val script: () -> ByteArray,
    val navigator: Navigator,
    private val storage: (String) -> JsonStore,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val http: LuaHttpClient? = null,
    private val reading: com.example.luacompose.ReadingCapabilities? = null,
) {
    fun createVm(arguments: Map<String, String> = emptyMap()) =
        LuaContainerVm(
            { scope, completed ->
                LuaSession(
                    script(),
                    storage,
                    LuaNavigation(
                        arguments,
                        navigate = { scope.launch { navigator.navigate(it) } },
                        back = { scope.launch { navigator.popBackStack() } },
                    ),
                    http, CoroutineScope(scope.coroutineContext + io), completed, reading,
                )
            },
            io,
        )
}

class LuaContainerVm internal constructor(
    private val sessionFactory: (CoroutineScope, (Long, LuaHttpClient.Response) -> Unit) -> LuaSession,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    initial: LuaUiResult? = null,
) : ViewModel() {
    internal constructor(session: LuaSession, io: CoroutineDispatcher = Dispatchers.IO, initial: LuaUiResult? = null) :
        this({ _, _ -> session }, io, initial)

    constructor(
        script: ByteArray,
        store: JsonStore? = null,
        io: CoroutineDispatcher = Dispatchers.IO
    ) :
            this(LuaSession(script, store), io)

    constructor(
        script: String,
        store: JsonStore? = null,
        io: CoroutineDispatcher = Dispatchers.IO
    ) :
            this(LuaSession(script, store), io)

    constructor(
        script: ByteArray,
        storage: (String) -> JsonStore,
        io: CoroutineDispatcher = Dispatchers.IO
    ) :
            this(LuaSession(script, storage), io)

    constructor(
        script: String,
        storage: (String) -> JsonStore,
        io: CoroutineDispatcher = Dispatchers.IO
    ) :
            this(LuaSession(script, storage), io)

    private val events = Channel<QueuedEvent>(Channel.UNLIMITED)
    private var revision = 0L
    private val session by lazy(LazyThreadSafetyMode.NONE) {
        sessionFactory(viewModelScope) { id, response ->
            events.trySend(QueuedEvent.Completion(id, response))
        }
    }

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
                val next = withContext(io) {
                    when (queued) {
                        is QueuedEvent.Input -> session.dispatch(queued.event)
                        is QueuedEvent.Completion -> session.complete(queued.id, queued.response)
                    }
                }
                if (queued is QueuedEvent.Completion || queued is QueuedEvent.Input && queued.revision == revision) result = next
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
        if (enqueue(event)) {
            val current = result
            if (current is LuaUiResult.Success) {
                result = LuaUiResult.Success(current.root.withInput(input))
            }
        }
    }

    private fun enqueue(event: LuaEvent): Boolean {
        val next = revision + 1
        if (events.trySend(QueuedEvent.Input(next, event)).isFailure) return false
        revision = next
        return true
    }

    override fun onCleared() {
        events.close()
        session.close()
    }

    private sealed interface QueuedEvent {
        data class Input(val revision: Long, val event: LuaEvent) : QueuedEvent
        data class Completion(val id: Long, val response: LuaHttpClient.Response) : QueuedEvent
    }
}

private fun UiNode.withInput(input: UiInput): UiNode = when (this) {
    is UiNode.Card -> UiNode.Card(children.map { it.withInput(input) }, style, action)
    is UiNode.Column -> UiNode.Column(children.map { it.withInput(input) }, gap)
    is UiNode.Row -> UiNode.Row(children.map { it.withInput(input) }, gap, wrap)
    is UiNode.TextField -> if (enabled && input is UiInput.TextChanged && action == input.action) copy(value = input.value) else this
    is UiNode.Checkbox -> if (enabled && input is UiInput.CheckedChanged && action == input.action) copy(checked = input.checked) else this
    is UiNode.ListItem -> UiNode.ListItem(key, children.map { it.withInput(input) })
    else -> this
}
