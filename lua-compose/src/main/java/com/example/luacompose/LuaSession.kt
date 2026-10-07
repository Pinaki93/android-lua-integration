package com.example.luacompose

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.IdentityHashMap
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
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

class LuaSession private constructor(
    private val bytecode: ByteArray?,
    private val source: String?,
    private val store: JsonStore? = null,
) : AutoCloseable {
    constructor(script: ByteArray, store: JsonStore? = null) : this(script, null, store)
    constructor(script: String, store: JsonStore? = null) : this(null, script, store)
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

    private fun storeTable(value: JsonStore) = LuaUiEngine.FrozenTable().apply {
        add(LuaValue.valueOf("create"), StoreFunction(1) { value.create(encode(it.arg1())); LuaValue.TRUE })
        add(LuaValue.valueOf("read"), StoreFunction(0) { value.read()?.let(::decode) ?: LuaValue.NIL })
        add(LuaValue.valueOf("update"), StoreFunction(1) { value.update(encode(it.arg1())); LuaValue.TRUE })
        add(LuaValue.valueOf("delete"), StoreFunction(0) { value.delete(); LuaValue.TRUE })
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

    private fun encode(value: LuaValue): String {
        if (!value.istable()) throw JsonFailure()
        return luaToJson(value, 1, Collections.newSetFromMap(IdentityHashMap())).toString()
    }

    private fun luaToJson(value: LuaValue, depth: Int, active: MutableSet<LuaTable>): JsonElement {
        if (depth > MAX_JSON_DEPTH) throw JsonFailure()
        return when {
            value.isboolean() -> JsonPrimitive(value.toboolean())
            value.isnumber() -> {
                val number = value.todouble()
                if (!number.isFinite()) throw JsonFailure()
                if (number == kotlin.math.floor(number) && number >= Long.MIN_VALUE && number <= Long.MAX_VALUE) {
                    JsonPrimitive(number.toLong())
                } else JsonPrimitive(number)
            }
            value.type() == LuaValue.TSTRING -> JsonPrimitive(value.tojstring())
            value.istable() -> tableToJson(value.checktable(), depth, active)
            else -> throw JsonFailure()
        }
    }

    private fun tableToJson(table: LuaTable, depth: Int, active: MutableSet<LuaTable>): JsonElement {
        if (!active.add(table)) throw JsonFailure()
        try {
            val keys = table.keys().toList()
            if (keys.isEmpty()) return JsonArray(emptyList())
            val indexes = keys.mapNotNull { if (it.isinttype() && it.toint() > 0) it.toint() else null }.sorted()
            if (indexes.size == keys.size) {
                if (indexes != (1..indexes.size).toList()) throw JsonFailure()
                return JsonArray(indexes.map { luaToJson(table.get(it), depth + 1, active) })
            }
            if (keys.any { it.type() != LuaValue.TSTRING }) throw JsonFailure()
            return JsonObject(keys.associate { it.tojstring() to luaToJson(table.get(it), depth + 1, active) })
        } finally {
            active.remove(table)
        }
    }

    private fun decode(json: String): LuaValue {
        val value = kotlinx.serialization.json.Json.parseToJsonElement(json)
        if (value !is JsonArray && value !is JsonObject) throw LuaError("store: invalid data")
        return jsonToLua(value, 1)
    }

    private fun jsonToLua(value: JsonElement, depth: Int): LuaValue {
        if (depth > MAX_JSON_DEPTH) throw LuaError("store: invalid data")
        return when (value) {
            JsonNull -> LuaValue.NIL
            is JsonArray -> LuaTable().apply { value.forEachIndexed { index, item -> set(index + 1, jsonToLua(item, depth + 1)) } }
            is JsonObject -> LuaTable().apply { value.forEach { (key, item) -> set(key, jsonToLua(item, depth + 1)) } }
            is JsonPrimitive -> when {
                value.isString -> LuaValue.valueOf(value.content)
                value.booleanOrNull != null -> LuaValue.valueOf(value.booleanOrNull!!)
                value.doubleOrNull != null -> LuaValue.valueOf(value.doubleOrNull!!)
                else -> throw LuaError("store: invalid data")
            }
        }
    }

    private fun stop(kind: LuaUiError.Kind, message: String): LuaUiResult =
        LuaUiResult.Failure(listOf(LuaUiError(kind, message))).also { result = it; terminal = true }

    private fun failure() = result ?: LuaUiResult.Failure(listOf(LuaUiError(LuaUiError.Kind.Runtime, "Lua session is closed.")))

    private class SessionFailure : RuntimeException()
    private class JsonFailure : RuntimeException()

    private companion object {
        const val MAX_JSON_DEPTH = 32
    }
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
