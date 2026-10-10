package com.example.luacompose

import kotlinx.coroutines.*
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import org.luaj.vm2.Globals
import org.luaj.vm2.LuaError
import org.luaj.vm2.LuaFunction
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.Varargs
import org.luaj.vm2.lib.StringLib
import org.luaj.vm2.lib.TableLib
import org.luaj.vm2.lib.VarArgFunction

sealed interface LuaEvent {
    val action: String

    data class Action(override val action: String) : LuaEvent
    data class TextChanged(override val action: String, val value: String) : LuaEvent
    data class CheckedChanged(override val action: String, val checked: Boolean) : LuaEvent
}

data class LuaNavigation(
    val arguments: Map<String, String> = emptyMap(),
    val navigate: (String) -> Unit,
    val back: () -> Unit,
)

class LuaSession private constructor(
    private val bytecode: ByteArray?,
    private val source: String?,
    private val store: JsonStore? = null,
    private val storage: ((String) -> JsonStore)? = null,
    private val navigation: LuaNavigation? = null,
    private val http: LuaHttpClient? = null,
    private val scope: CoroutineScope? = null,
    private val completed: ((Long, LuaHttpClient.Response) -> Unit)? = null,
) : AutoCloseable {
    constructor(script: ByteArray, store: JsonStore? = null) : this(script, null, store, null, null)
    constructor(script: String, store: JsonStore? = null) : this(null, script, store, null, null)
    constructor(
        script: ByteArray,
        storage: (String) -> JsonStore,
        navigation: LuaNavigation? = null,
        http: LuaHttpClient? = null,
        scope: CoroutineScope? = null,
        completed: ((Long, LuaHttpClient.Response) -> Unit)? = null,
    ) :
        this(script, null, null, storage, navigation, http, scope, completed)
    constructor(
        script: String,
        storage: (String) -> JsonStore,
        navigation: LuaNavigation? = null,
        http: LuaHttpClient? = null,
        scope: CoroutineScope? = null,
        completed: ((Long, LuaHttpClient.Response) -> Unit)? = null,
    ) :
        this(null, script, null, storage, navigation, http, scope, completed)
    init {
        require(http == null || scope != null && completed != null) {
            "HTTP requires a request scope and completion queue."
        }
    }

    private val callbacks = mutableMapOf<Long, LuaFunction>()
    private val jobs = mutableMapOf<Long, Job>()
    private var nextRequest = 0L
    private var globals: Globals? = null
    private var render: LuaFunction? = null
    private var onEvent: LuaFunction? = null
    private var latest: UiNode? = null
    private var result: LuaUiResult? = null
    private var terminal = false
    private var rendering = false

    @Synchronized
    fun start(): LuaUiResult {
        result?.let { return it }
        if (terminal) return failure()
        val size = bytecode?.size ?: source!!.toByteArray(StandardCharsets.UTF_8).size
        if (size > LuaUiEngine.MAX_SCRIPT_BYTES) {
            return stop(LuaUiError.Kind.Limit, "Script exceeds ${LuaUiEngine.MAX_SCRIPT_BYTES} UTF-8 bytes.")
        }
        val environment = LuaUiEngine().globals(emptyMap()).apply {
            set("package", LuaTable().apply { set("loaded", LuaTable()) })
            load(StringLib())
            load(TableLib())
            set("package", LuaValue.NIL)
        }
        store?.let { environment.set("store", storeTable(it)) }
        storage?.let { environment.set("storage", storageTable(it)) }
        navigation?.let { environment.set("navigation", navigationTable(it)) }
        http?.let { environment.set("http", httpTable(it)) }
        val chunk = try {
            source?.let { environment.load(it, "ui.lua") }
                ?: environment.load(ByteArrayInputStream(bytecode!!), "ui.luac", "b", environment)
        } catch (_: LuaError) {
            return stop(LuaUiError.Kind.Syntax, "Invalid Lua syntax.")
        }
        val descriptor = try {
            val returned = chunk.invoke()
            if (returned.narg() != 1 || !returned.arg1().istable()) throw SessionFailure()
            returned.arg1().checktable()
        } catch (_: SessionFailure) {
            return stop(LuaUiError.Kind.Validation, "Script must return render and onEvent functions.")
        } catch (_: LuaError) {
            return stop(LuaUiError.Kind.Runtime, "Lua execution failed.")
        }
        val fields = descriptor.keys().map(LuaValue::tojstring).sorted()
        if (fields != listOf("onEvent", "render") || !descriptor.get("render").isfunction() || !descriptor.get("onEvent").isfunction()) {
            return stop(LuaUiError.Kind.Validation, "Script must return render and onEvent functions.")
        }
        globals = environment
        render = descriptor.get("render").checkfunction()
        onEvent = descriptor.get("onEvent").checkfunction()
        return render()
    }

    @Synchronized
    fun dispatch(event: LuaEvent): LuaUiResult {
        val current = result ?: start()
        if (terminal || !valid(event) || !admits(latest, event)) return current
        try {
            val returned = onEvent!!.invoke(eventTable(event))
            if (returned.narg() != 0) return stop(LuaUiError.Kind.Validation, "onEvent must return no values.")
        } catch (_: LuaError) {
            return stop(LuaUiError.Kind.Runtime, "Lua event handling failed.")
        }
        return render()
    }

    @Synchronized
    override fun close() {
        cancelRequests()
        terminal = true
        result = LuaUiResult.Failure(listOf(LuaUiError(LuaUiError.Kind.Runtime, "Lua session is closed.")))
        globals = null
        render = null
        onEvent = null
        latest = null
    }

    private fun render(): LuaUiResult {
        val value = try {
            rendering = true
            val returned = render!!.invoke()
            if (returned.narg() != 1 || returned.arg1().isnil()) {
                return stop(LuaUiError.Kind.Validation, "Render must return exactly one root node.")
            }
            returned.arg1()
        } catch (_: LuaError) {
            return stop(LuaUiError.Kind.Runtime, "Lua rendering failed.")
        } finally {
            rendering = false
        }
        return try {
            val root = LuaUiEngine.Parser().parse(value)
            latest = root
            LuaUiResult.Success(root).also { result = it }
        } catch (error: LuaUiEngine.EngineFailure) {
            stop(error.kind, error.message)
        }
    }

    private fun valid(event: LuaEvent): Boolean = LuaUiEngine.ACTION.matches(event.action) &&
        (event !is LuaEvent.TextChanged || event.value.codePointCount(0, event.value.length) <= LuaUiEngine.MAX_TEXT_LENGTH)

    private fun admits(node: UiNode?, event: LuaEvent): Boolean = when (node) {
        is UiNode.Button -> event is LuaEvent.Action && node.enabled && node.action == event.action
        is UiNode.TextField -> event is LuaEvent.TextChanged && node.enabled && node.action == event.action
        is UiNode.Checkbox -> event is LuaEvent.CheckedChanged && node.enabled && node.action == event.action
        is UiNode.IconButton -> event is LuaEvent.Action && node.enabled && node.action == event.action
        is UiNode.ListItem -> node.children.any { admits(it, event) }
        is UiNode.Card -> node.children.any { admits(it, event) }
        is UiNode.Column -> node.children.any { admits(it, event) }
        is UiNode.Row -> node.children.any { admits(it, event) }
        else -> false
    }

    private fun eventTable(event: LuaEvent) = LuaUiEngine.FrozenTable().apply {
        add(LuaValue.valueOf("action"), LuaValue.valueOf(event.action))
        when (event) {
            is LuaEvent.Action -> add(LuaValue.valueOf("type"), LuaValue.valueOf("action"))
            is LuaEvent.TextChanged -> {
                add(LuaValue.valueOf("type"), LuaValue.valueOf("text"))
                add(LuaValue.valueOf("value"), LuaValue.valueOf(event.value))
            }
            is LuaEvent.CheckedChanged -> {
                add(LuaValue.valueOf("type"), LuaValue.valueOf("checked"))
                add(LuaValue.valueOf("value"), LuaValue.valueOf(event.checked))
            }
        }
        freeze()
    }

    @Synchronized
    fun complete(id: Long, response: LuaHttpClient.Response): LuaUiResult {
        if (terminal) return failure()
        val callback = callbacks.remove(id) ?: return result ?: failure()
        jobs.remove(id)
        try {
            if (callback.invoke(http!!.table(response)).narg() != 0) {
                return stop(LuaUiError.Kind.Validation, "HTTP callback must return no values.")
            }
        } catch (_: Exception) {
            return stop(LuaUiError.Kind.Runtime, "HTTP callback failed.")
        }
        return render()
    }

    private fun cancelRequests() {
        jobs.values.forEach { it.cancel() }
        jobs.clear()
        callbacks.clear()
    }

    private fun httpTable(client: LuaHttpClient) = LuaUiEngine.FrozenTable().apply {
        add(LuaValue.valueOf("null"), client.nullValue)
        add(LuaValue.valueOf("request"), object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                if (rendering) throw LuaError("http: unavailable during render")
                if (args.narg() != 2 || !args.arg(2).isfunction()) throw LuaError("http: invalid arguments")
                val id = ++nextRequest
                callbacks[id] = args.arg(2).checkfunction()
                val request = try {
                    if (callbacks.size > 4) throw LuaHttpClient.Failure("pending_limit")
                    client.parse(args.arg1())
                } catch (failure: LuaHttpClient.Failure) {
                    completed!!(id, LuaHttpClient.Response(error = failure.code))
                    return LuaValue.NONE
                }
                jobs[id] = scope!!.launch {
                    completed!!(id, client.execute(request))
                }
                return LuaValue.NONE
            }
        })
        freeze()
    }

    private fun storeTable(value: JsonStore) = LuaUiEngine.FrozenTable().apply {
        add(LuaValue.valueOf("create"), StoreFunction(1) { value.create(json.encode(it.arg1())); LuaValue.TRUE })
        add(LuaValue.valueOf("read"), StoreFunction(0) { value.read()?.let(json::decode) ?: LuaValue.NIL })
        add(LuaValue.valueOf("update"), StoreFunction(1) { value.update(json.encode(it.arg1())); LuaValue.TRUE })
        add(LuaValue.valueOf("delete"), StoreFunction(0) { value.delete(); LuaValue.TRUE })
        freeze()
    }

    private fun storageTable(factory: (String) -> JsonStore) = LuaUiEngine.FrozenTable().apply {
        add(LuaValue.valueOf("open"), object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                if (args.narg() != 1 || args.arg1().type() != LuaValue.TSTRING) throw LuaError("storage: invalid argument")
                return try {
                    storeTable(factory(args.arg1().tojstring()))
                } catch (_: Exception) {
                    throw LuaError("storage: unavailable")
                }
            }
        })
        freeze()
    }

    private fun navigationTable(value: LuaNavigation) = LuaUiEngine.FrozenTable().apply {
        add(LuaValue.valueOf("arguments"), LuaUiEngine.FrozenTable().apply {
            value.arguments.forEach { (key, argument) -> add(LuaValue.valueOf(key), LuaValue.valueOf(argument)) }
            freeze()
        })
        add(LuaValue.valueOf("navigate"), object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                if (args.narg() != 1 || args.arg1().type() != LuaValue.TSTRING) throw LuaError("navigation: invalid argument")
                try {
                    value.navigate(args.arg1().tojstring())
                } catch (_: Exception) {
                    throw LuaError("navigation: unavailable")
                }
                return LuaValue.NONE
            }
        })
        add(LuaValue.valueOf("back"), object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                if (args.narg() != 0) throw LuaError("navigation: invalid argument")
                try {
                    value.back()
                } catch (_: Exception) {
                    throw LuaError("navigation: unavailable")
                }
                return LuaValue.NONE
            }
        })
        freeze()
    }

    private inner class StoreFunction(
        private val arguments: Int,
        private val call: (Varargs) -> LuaValue,
    ) : VarArgFunction() {
        override fun invoke(args: Varargs): Varargs {
            if (rendering) throw LuaError("store: unavailable during render")
            if (args.narg() != arguments) throw LuaError("store: invalid argument")
            return try {
                call(args)
            } catch (failure: JsonStoreException) {
                throw LuaError("store: ${failure.error.message}")
            } catch (_: JsonFailure) {
                throw LuaError("store: invalid argument")
            }
        }
    }

    private fun stop(kind: LuaUiError.Kind, message: String): LuaUiResult =
        LuaUiResult.Failure(listOf(LuaUiError(kind, message))).also { result = it; terminal = true; cancelRequests() }

    private fun failure() = result ?: LuaUiResult.Failure(listOf(LuaUiError(LuaUiError.Kind.Runtime, "Lua session is closed.")))

    private class SessionFailure : RuntimeException()
    private val json = LuaJson()

}

private val JsonStoreError.message: String
    get() = when (this) {
        JsonStoreError.ALREADY_EXISTS -> "document exists"
        JsonStoreError.NOT_FOUND -> "document missing"
        JsonStoreError.INVALID_JSON -> "invalid argument"
        JsonStoreError.INVALID_DATA -> "invalid data"
        JsonStoreError.QUOTA_EXCEEDED -> "quota exceeded"
        JsonStoreError.UNAVAILABLE -> "unavailable"
    }
