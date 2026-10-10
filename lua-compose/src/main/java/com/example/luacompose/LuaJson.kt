package com.example.luacompose

import java.util.Collections
import java.util.IdentityHashMap
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import org.luaj.vm2.*

internal class JsonFailure : RuntimeException()

internal class LuaJson(private val nullValue: LuaValue = LuaValue.NIL) {
    fun encode(value: LuaValue): String {
        if (!value.istable()) throw JsonFailure()
        return luaToJson(value, 1, Collections.newSetFromMap(IdentityHashMap())).toString()
    }

    private fun luaToJson(value: LuaValue, depth: Int, active: MutableSet<LuaTable>): JsonElement {
        if (depth > 32) throw JsonFailure()
        return when {
            value === nullValue && !nullValue.isnil() -> JsonNull
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

    fun decode(json: String): LuaValue {
        // Bound nesting before the JSON parser allocates a recursive tree.
        var depth = 0
        var quoted = false
        var escaped = false
        for (character in json) {
            if (quoted) {
                if (escaped) escaped = false
                else if (character == '\\') escaped = true
                else if (character == '"') quoted = false
            } else when (character) {
                '"' -> quoted = true
                '[', '{' -> { depth++; if (depth > 32) throw JsonFailure() }
                ']', '}' -> depth--
            }
        }
        val value = kotlinx.serialization.json.Json.parseToJsonElement(json)
        if (nullValue.isnil() && value !is JsonArray && value !is JsonObject) throw LuaError("store: invalid data")
        return jsonToLua(value, 1)
    }

    private fun jsonToLua(value: JsonElement, depth: Int): LuaValue {
        if (depth > 32) throw LuaError("store: invalid data")
        return when (value) {
            JsonNull -> nullValue
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

}
