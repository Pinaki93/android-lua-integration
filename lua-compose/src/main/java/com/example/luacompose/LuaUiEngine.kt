package com.example.luacompose

import org.luaj.vm2.Globals
import org.luaj.vm2.LuaError
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.LoadState
import org.luaj.vm2.compiler.LuaC
import org.luaj.vm2.lib.BaseLib
import org.luaj.vm2.lib.OneArgFunction
import java.net.URI
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.IdentityHashMap

class LuaUiEngine {
    fun evaluate(script: String, state: Map<String, Any?> = emptyMap()) = evaluate(
        script.toByteArray(StandardCharsets.UTF_8).size,
        state,
    ) { globals -> globals.load(script, "ui.lua") }

    fun evaluate(script: ByteArray, state: Map<String, Any?> = emptyMap()) = evaluate(script.size, state) { globals ->
        globals.load(ByteArrayInputStream(script), "ui.luac", "b", globals)
    }

    private fun evaluate(size: Int, state: Map<String, Any?>, load: (Globals) -> LuaValue): LuaUiResult {
        if (size > MAX_SCRIPT_BYTES) {
            return failure(LuaUiError.Kind.Limit, "Script exceeds $MAX_SCRIPT_BYTES UTF-8 bytes.")
        }

        val globals = try {
            globals(state)
        } catch (error: EngineFailure) {
            return failure(error.kind, error.message)
        }
        val chunk = try {
            load(globals)
        } catch (_: LuaError) {
            return failure(LuaUiError.Kind.Syntax, "Invalid Lua syntax.")
        }
        val result = try {
            chunk.invoke()
        } catch (_: LuaError) {
            return failure(LuaUiError.Kind.Runtime, "Lua execution failed.")
        }

        if (result.narg() != 1 || result.arg1().isnil()) {
            return failure(LuaUiError.Kind.Validation, "Script must return exactly one root node.")
        }
        return try {
            LuaUiResult.Success(Parser().parse(result.arg1()))
        } catch (error: EngineFailure) {
            failure(error.kind, error.message)
        }
    }

    internal fun globals(state: Map<String, Any?>): Globals = Globals().apply {
        load(BaseLib())
        val safeFunctions = setOf("assert", "error", "ipairs", "next", "pairs", "pcall", "rawequal", "rawlen", "select", "tonumber", "tostring", "type", "xpcall")
        keys().map(LuaValue::tojstring).filterNot(safeFunctions::contains).forEach { set(it, LuaValue.NIL) }
        set("state", stateTable(state))
        set("ui", uiTable())
        LoadState.install(this)
        LuaC.install(this)
    }

    private fun stateTable(state: Map<String, Any?>): LuaTable =
        stateValue(state, "state", Collections.newSetFromMap(IdentityHashMap())).checktable()

    private fun stateValue(value: Any?, path: String, active: MutableSet<Any>): LuaValue = when (value) {
        null -> LuaValue.NIL
        is String -> LuaValue.valueOf(value)
        is Boolean -> LuaValue.valueOf(value)
        is Byte -> LuaValue.valueOf(value.toInt())
        is Short -> LuaValue.valueOf(value.toInt())
        is Int -> LuaValue.valueOf(value)
        is Long -> LuaValue.valueOf(value.toDouble())
        is Float -> number(value.toDouble(), path)
        is Double -> number(value, path)
        is Map<*, *> -> frozenTable(value, path, active)
        is List<*> -> frozenList(value, path, active)
        else -> fail(LuaUiError.Kind.Validation, "Unsupported state value at $path.")
    }

    private fun number(value: Double, path: String): LuaValue {
        if (!value.isFinite()) fail(LuaUiError.Kind.Validation, "Non-finite state number at $path.")
        return LuaValue.valueOf(value)
    }

    private fun frozenTable(value: Map<*, *>, path: String, active: MutableSet<Any>): LuaTable {
        enterState(value, path, active)
        val table = FrozenTable()
        value.entries.sortedBy { it.key.toString() }.forEach { (key, item) ->
            if (key !is String) fail(LuaUiError.Kind.Validation, "State key at $path must be a string.")
            table.add(LuaValue.valueOf(key), stateValue(item, "$path.$key", active))
        }
        active.remove(value)
        table.freeze()
        return table
    }

    private fun frozenList(value: List<*>, path: String, active: MutableSet<Any>): LuaTable {
        enterState(value, path, active)
        val table = FrozenTable()
        value.forEachIndexed { index, item -> table.add(index + 1, stateValue(item, "$path[${index + 1}]", active)) }
        active.remove(value)
        table.freeze()
        return table
    }

    private fun enterState(value: Any, path: String, active: MutableSet<Any>) {
        if (!active.add(value)) fail(LuaUiError.Kind.Validation, "Cyclic state value at $path.")
    }

    internal fun uiTable() = FrozenTable().apply {
        listOf("dialog", "column", "row", "text", "card", "button", "textField", "checkbox", "listItem", "iconButton", "image").forEach { type ->
            add(LuaValue.valueOf(type), NodeConstructor(type))
        }
        freeze()
    }

    private class NodeConstructor(private val type: String) : OneArgFunction() {
        override fun call(argument: LuaValue): LuaValue {
            if (!argument.istable()) throw LuaError("ui.$type expects a table")
            argument.checktable().set("type", type)
            return argument
        }
    }

    internal class FrozenTable : LuaTable() {
        private var frozen = false

        fun add(key: LuaValue, value: LuaValue) = super.rawset(key, value)
        fun add(key: Int, value: LuaValue) = super.rawset(key, value)
        fun freeze() {
            frozen = true
        }

        override fun set(key: Int, value: LuaValue) = write { super.set(key, value) }
        override fun set(key: LuaValue, value: LuaValue) = write { super.set(key, value) }
        override fun rawset(key: Int, value: LuaValue) = write { super.rawset(key, value) }
        override fun rawset(key: LuaValue, value: LuaValue) = write { super.rawset(key, value) }

        private inline fun write(block: () -> Unit) {
            if (frozen) throw LuaError("table is read-only")
            block()
        }
    }

    internal class Parser {
        private var nodes = 0
        private var totalText = 0
        private val active = Collections.newSetFromMap(IdentityHashMap<LuaTable, Boolean>())

        fun parse(value: LuaValue, path: String = "$", depth: Int = 1): UiNode {
            if (!value.istable()) fail(LuaUiError.Kind.Validation, "Node at $path must be a table.")
            if (depth > MAX_DEPTH) fail(LuaUiError.Kind.Limit, "Tree depth exceeds $MAX_DEPTH at $path.")
            if (++nodes > MAX_NODES) fail(LuaUiError.Kind.Limit, "Node count exceeds $MAX_NODES at $path.")

            val table = value.checktable()
            if (!active.add(table)) fail(LuaUiError.Kind.Validation, "Node cycle at $path.")
            try {
                val type = requiredString(table, "type", path)
                return when (type) {
                    "dialog" -> {
                        fields(table, path, "type", "title", "children", "dismissAction")
                        val dismiss = table.get("dismissAction")
                        if (dismiss.type() != LuaValue.TSTRING || !ACTION.matches(dismiss.tojstring())) fail(LuaUiError.Kind.Validation, "Invalid dismiss action.")
                        UiNode.Dialog(text(table, "title", path), children(table, path, depth), dismiss.tojstring())
                    }
                    "column" -> {
                        fields(table, path, "type", "gap", "children")
                        UiNode.Column(children(table, path, depth), gap(table, path))
                    }
                    "row" -> {
                        fields(table, path, "type", "gap", "children", "wrap")
                        UiNode.Row(children(table, path, depth), gap(table, path), optionalBoolean(table, "wrap", path, false))
                    }
                    "text" -> {
                        fields(table, path, "type", "text", "style", "weight", "strikeThrough", "tone")
                        UiNode.Text(
                            text = text(table, "text", path),
                            style = style(table, path),
                            weight = optionalBoolean(table, "weight", path, false),
                            strikeThrough = optionalBoolean(table, "strikeThrough", path, false),
                            tone = tone(table, path),
                        )
                    }
                    "image" -> {
                        fields(table, path, "type", "url", "label", "width", "height", "circleCrop")
                        UiNode.Image(
                            url = if (table.get("url").isnil()) null else imageUrl(table, path),
                            label = label(table, path),
                            width = imageDimension(table, "width", path),
                            height = imageDimension(table, "height", path),
                            circleCrop = optionalBoolean(table, "circleCrop", path, false),
                        )
                    }
                    "card" -> {
                        fields(table, path, "type", "children", "style", "action")
                        UiNode.Card(children(table, path, depth), cardStyle(table, path), if (table.get("action").isnil()) null else action(table, path))
                    }
                    "button" -> {
                        fields(table, path, "type", "text", "action", "enabled", "style")
                        UiNode.Button(text(table, "text", path), action(table, path), enabled(table, path), buttonStyle(table, path))
                    }
                    "textField" -> {
                        fields(table, path, "type", "value", "label", "action", "enabled", "error", "style", "multiline")
                        UiNode.TextField(
                            value = text(table, "value", path),
                            label = label(table, path),
                            action = action(table, path),
                            enabled = enabled(table, path),
                            error = optionalText(table, "error", path),
                            style = textFieldStyle(table, path),
                            multiline = optionalBoolean(table, "multiline", path, false),
                        )
                    }
                    "checkbox" -> {
                        fields(table, path, "type", "checked", "label", "action", "enabled", "showLabel")
                        UiNode.Checkbox(
                            checked = boolean(table, "checked", path),
                            label = label(table, path),
                            action = action(table, path),
                            enabled = enabled(table, path),
                            showLabel = optionalBoolean(table, "showLabel", path, true),
                        )
                    }
                    "listItem" -> {
                        fields(table, path, "type", "key", "children")
                        UiNode.ListItem(namedAction(table, "key", path), children(table, path, depth))
                    }
                    "iconButton" -> {
                        fields(table, path, "type", "icon", "label", "action", "enabled")
                        val name = requiredString(table, "icon", path)
                        val icon = UiIcon.entries.firstOrNull { it.name.lowercase() == name }
                            ?: fail(LuaUiError.Kind.Validation, "Unknown icon '$name' at $path.")
                        UiNode.IconButton(icon, label(table, path), action(table, path), enabled(table, path))
                    }
                    else -> fail(LuaUiError.Kind.Validation, "Unknown node type '$type' at $path.")
                }
            } finally {
                active.remove(table)
            }
        }

        private fun fields(table: LuaTable, path: String, vararg allowed: String) {
            val allowedFields = allowed.toSet()
            val unknown = table.keys().map { key ->
                if (key.type() != LuaValue.TSTRING) "[${key.tojstring()}]" else key.tojstring()
            }.filterNot(allowedFields::contains).sorted().firstOrNull()
            if (unknown != null) fail(LuaUiError.Kind.Validation, "Unknown field '$unknown' at $path.")
        }

        private fun children(table: LuaTable, path: String, depth: Int): List<UiNode> {
            val value = table.get("children")
            if (value.isnil()) return emptyList()
            if (!value.istable()) fail(LuaUiError.Kind.Validation, "Field 'children' at $path must be a table.")
            val children = value.checktable()
            val indexes = children.keys().map { key ->
                if (!key.isinttype() || key.toint() <= 0) {
                    fail(LuaUiError.Kind.Validation, "Children at $path must be a list.")
                }
                key.toint()
            }.sorted()
            if (indexes != (1..indexes.size).toList()) {
                fail(LuaUiError.Kind.Validation, "Children at $path must be a contiguous list.")
            }
            return indexes.map { index -> parse(children.get(index), "$path.children[$index]", depth + 1) }
        }

        private fun imageUrl(table: LuaTable, path: String): String {
            val url = text(table, "url", path)
            val uri = try { URI(url) } catch (_: java.net.URISyntaxException) { null }
            if (uri == null || uri.scheme != "https" || uri.host.isNullOrEmpty() ||
                uri.rawUserInfo != null || uri.rawFragment != null || uri.port !in listOf(-1, 443)) {
                fail(LuaUiError.Kind.Validation, "Image URL at $path must be HTTPS without credentials or fragments on port 443.")
            }
            return url
        }

        private fun imageDimension(table: LuaTable, field: String, path: String): Int {
            val value = table.get(field)
            if (value.isnil()) return 48
            if (!value.isinttype() || value.toint() !in 1..1024) {
                fail(LuaUiError.Kind.Validation, "Field '$field' at $path must be an integer from 1 to 1024.")
            }
            return value.toint()
        }

        private fun gap(table: LuaTable, path: String): Int {
            val value = table.get("gap")
            if (value.isnil()) return 0
            if (!value.isinttype() || value.toint() !in 0..MAX_GAP) {
                fail(LuaUiError.Kind.Validation, "Field 'gap' at $path must be an integer from 0 to $MAX_GAP.")
            }
            return value.toint()
        }

        private fun style(table: LuaTable, path: String): UiTextStyle {
            val value = table.get("style")
            if (value.isnil()) return UiTextStyle.Body
            if (value.type() != LuaValue.TSTRING) fail(LuaUiError.Kind.Validation, "Field 'style' at $path must be a string.")
            return when (val style = value.tojstring()) {
                "body" -> UiTextStyle.Body
                "title" -> UiTextStyle.Title
                "metric" -> UiTextStyle.Metric
                "label" -> UiTextStyle.Label
                "heading" -> UiTextStyle.Heading
                else -> fail(LuaUiError.Kind.Validation, "Unknown style '$style' at $path.")
            }
        }

        private fun tone(table: LuaTable, path: String): UiTextTone {
            val value = table.get("tone")
            if (value.isnil()) return UiTextTone.Default
            if (value.type() != LuaValue.TSTRING) fail(LuaUiError.Kind.Validation, "Field 'tone' at $path must be a string.")
            return UiTextTone.entries.firstOrNull { it.name.lowercase() == value.tojstring() }
                ?: fail(LuaUiError.Kind.Validation, "Unknown text tone at $path.")
        }

        private fun cardStyle(table: LuaTable, path: String): UiCardStyle {
            val value = table.get("style")
            if (value.isnil()) return UiCardStyle.Default
            if (value.type() != LuaValue.TSTRING) fail(LuaUiError.Kind.Validation, "Field 'style' at $path must be a string.")
            return when (val style = value.tojstring()) {
                "default" -> UiCardStyle.Default
                "accent" -> UiCardStyle.Accent
                "subtle" -> UiCardStyle.Subtle
                "outlined" -> UiCardStyle.Outlined
                "orange" -> UiCardStyle.Orange
                "lightOrange" -> UiCardStyle.LightOrange
                else -> fail(LuaUiError.Kind.Validation, "Unknown card style '$style' at $path.")
            }
        }

        private fun buttonStyle(table: LuaTable, path: String): UiButtonStyle {
            val value = table.get("style")
            if (value.isnil()) return UiButtonStyle.Primary
            if (value.type() != LuaValue.TSTRING) fail(LuaUiError.Kind.Validation, "Field 'style' at $path must be a string.")
            return when (val style = value.tojstring()) {
                "primary" -> UiButtonStyle.Primary
                "orange" -> UiButtonStyle.Orange
                "quiet" -> UiButtonStyle.Quiet
                "filter" -> UiButtonStyle.Filter
                "selected" -> UiButtonStyle.Selected
                "destructive" -> UiButtonStyle.Destructive
                else -> fail(LuaUiError.Kind.Validation, "Unknown button style '$style' at $path.")
            }
        }

        private fun textFieldStyle(table: LuaTable, path: String): UiTextFieldStyle {
            val value = table.get("style")
            if (value.isnil()) return UiTextFieldStyle.Outlined
            if (value.type() != LuaValue.TSTRING) fail(LuaUiError.Kind.Validation, "Field 'style' at $path must be a string.")
            return when (val style = value.tojstring()) {
                "outlined" -> UiTextFieldStyle.Outlined
                "plain" -> UiTextFieldStyle.Plain
                else -> fail(LuaUiError.Kind.Validation, "Unknown text field style '$style' at $path.")
            }
        }

        private fun action(table: LuaTable, path: String): String {
            val action = requiredString(table, "action", path)
            if (!ACTION.matches(action)) fail(LuaUiError.Kind.Validation, "Unknown action '$action' at $path.")
            return action
        }

        private fun namedAction(table: LuaTable, field: String, path: String): String {
            val action = requiredString(table, field, path)
            if (!ACTION.matches(action)) fail(LuaUiError.Kind.Validation, "Unknown action '$action' at $path.")
            return action
        }

        private fun enabled(table: LuaTable, path: String) = optionalBoolean(table, "enabled", path, true)

        private fun optionalBoolean(table: LuaTable, field: String, path: String, default: Boolean): Boolean =
            if (table.get(field).isnil()) default else boolean(table, field, path)

        private fun boolean(table: LuaTable, field: String, path: String): Boolean {
            val value = table.get(field)
            if (value.isnil()) fail(LuaUiError.Kind.Validation, "Missing field '$field' at $path.")
            if (value.type() != LuaValue.TBOOLEAN) {
                fail(LuaUiError.Kind.Validation, "Field '$field' at $path must be a boolean.")
            }
            return value.toboolean()
        }

        private fun label(table: LuaTable, path: String): String =
            text(table, "label", path).also {
                if (it.isEmpty()) fail(LuaUiError.Kind.Validation, "Field 'label' at $path must not be empty.")
            }

        private fun optionalText(table: LuaTable, field: String, path: String): String? {
            val value = table.get(field)
            if (value.isnil()) return null
            if (value.type() != LuaValue.TSTRING) {
                fail(LuaUiError.Kind.Validation, "Field '$field' at $path must be a string or nil.")
            }
            return text(value.tojstring(), path)
        }

        private fun text(table: LuaTable, field: String, path: String): String {
            val text = requiredString(table, field, path)
            return text(text, path)
        }

        private fun text(text: String, path: String): String {
            val length = text.codePointCount(0, text.length)
            if (length > MAX_TEXT_LENGTH) {
                fail(LuaUiError.Kind.Limit, "Text at $path exceeds $MAX_TEXT_LENGTH characters.")
            }
            totalText += length
            if (totalText > MAX_TOTAL_TEXT_LENGTH) {
                fail(LuaUiError.Kind.Limit, "Total text exceeds $MAX_TOTAL_TEXT_LENGTH characters at $path.")
            }
            return text
        }

        private fun requiredString(table: LuaTable, field: String, path: String): String {
            val value = table.get(field)
            if (value.isnil()) fail(LuaUiError.Kind.Validation, "Missing field '$field' at $path.")
            if (value.type() != LuaValue.TSTRING) fail(LuaUiError.Kind.Validation, "Field '$field' at $path must be a string.")
            return value.tojstring()
        }
    }

    internal class EngineFailure(val kind: LuaUiError.Kind, override val message: String) : RuntimeException(message)

    companion object {
        const val MAX_SCRIPT_BYTES = 500 * 1024
        const val MAX_DEPTH = 32
        const val MAX_NODES = 1_000
        const val MAX_TEXT_LENGTH = 10_000
        const val MAX_TOTAL_TEXT_LENGTH = 100_000
        const val MAX_GAP = 1_000
        internal val ACTION = Regex("[a-z][A-Za-z0-9_.-]{0,63}")

        private fun failure(kind: LuaUiError.Kind, message: String) =
            LuaUiResult.Failure(listOf(LuaUiError(kind, message)))

        private fun fail(kind: LuaUiError.Kind, message: String): Nothing = throw EngineFailure(kind, message)
    }
}
