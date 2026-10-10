package com.example.luaplayground

import org.luaj.vm2.Globals
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaError
import org.luaj.vm2.LuaValue
import org.luaj.vm2.LoadState
import org.luaj.vm2.Varargs
import org.luaj.vm2.compiler.LuaC
import org.luaj.vm2.lib.BaseLib
import org.luaj.vm2.lib.VarArgFunction
import java.io.ByteArrayInputStream

data class LuaResult(val succeeded: Boolean)

class LuaEngine {
    fun executeApp(
        script: ByteArray,
        registerRoute: (String, String) -> Unit,
        setStartRoute: (String) -> Unit,
        readFeature: (String) -> ByteArray = { error("Feature assets unavailable") },
        registerTheme: (String, Map<String, Long>) -> Unit = { _, _ -> },
    ): LuaResult {
        if (script.size > com.example.luacompose.LuaUiEngine.MAX_SCRIPT_BYTES) return LuaResult(false)
        val globals = globals()
        globals.set("registerRoute", object : VarArgFunction() {
            override fun invoke(arguments: Varargs): Varargs {
                if (arguments.narg() != 2 || arguments.arg(1).type() != LuaValue.TSTRING ||
                    arguments.arg(2).type() != LuaValue.TSTRING) {
                    throw LuaError("registerRoute expects route and script strings")
                }
                registerRoute(arguments.arg(1).tojstring(), arguments.arg(2).tojstring())
                return LuaValue.NONE
            }
        })
        globals.set("setStartRoute", object : VarArgFunction() {
            override fun invoke(arguments: Varargs): Varargs {
                if (arguments.narg() != 1 || arguments.arg1().type() != LuaValue.TSTRING) {
                    throw LuaError("setStartRoute expects one route string")
                }
                setStartRoute(arguments.arg1().tojstring())
                return LuaValue.NONE
            }
        })
        globals.set("registerSubRoute", object : VarArgFunction() {
            override fun invoke(arguments: Varargs): Varargs {
                if (arguments.narg() != 1 || arguments.arg1().type() != LuaValue.TSTRING) throw LuaError("Expected feature name")
                val feature = arguments.arg1().tojstring()
                if (!Regex("[A-Za-z][A-Za-z0-9_-]*").matches(feature)) throw LuaError("Invalid feature name")
                val builder = LuaTable()
                builder.set("registerRoute", object : VarArgFunction() {
                    override fun invoke(args: Varargs): Varargs {
                        if (args.narg() != 2 || args.arg1().type() != LuaValue.TSTRING || args.arg(2).type() != LuaValue.TSTRING) {
                            throw LuaError("Expected route and script")
                        }
                        registerRoute("$feature/${args.arg1().tojstring()}", "$feature/${args.arg(2).tojstring()}")
                        return builder
                    }
                })
                builder.set("registerTheme", object : VarArgFunction() {
                    override fun invoke(args: Varargs): Varargs {
                        if (args.narg() != 1 || !args.arg1().istable()) throw LuaError("Expected theme table")
                        val theme = args.arg1().checktable()
                        val colors = theme.keys().associate { key ->
                            val name = key.checkjstring()
                            val color = theme.get(key)
                            if (name !in com.example.luacompose.FEATURE_COLORS || color.type() != LuaValue.TSTRING ||
                                !Regex("#[0-9A-Fa-f]{6}").matches(color.tojstring())) {
                                throw LuaError("Invalid theme color")
                            }
                            name to (0xFF000000L or color.tojstring().drop(1).toLong(16))
                        }
                        registerTheme(feature, colors)
                        return builder
                    }
                })
                return builder
            }
        })
        globals.set("loadFeature", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                if (args.narg() != 1 || args.arg1().type() != LuaValue.TSTRING ||
                    !Regex("[A-Za-z][A-Za-z0-9_-]*").matches(args.arg1().tojstring())) {
                    throw LuaError("Invalid feature name")
                }
                val name = "${args.arg1().tojstring()}/index.luac"
                val bytes = try { readFeature(name) } catch (_: Exception) { throw LuaError("Feature unavailable") }
                if (bytes.size > com.example.luacompose.LuaUiEngine.MAX_SCRIPT_BYTES) throw LuaError("Feature exceeds size limit")
                globals.load(ByteArrayInputStream(bytes), name, "bt", globals).call()
                return LuaValue.NONE
            }
        })
        return try {
            globals.load(ByteArrayInputStream(script), "app.luac", "bt", globals).call()
            LuaResult(true)
        } catch (_: LuaError) {
            LuaResult(false)
        }
    }

    private fun globals() = Globals().apply {
        load(BaseLib())
        LoadState.install(this)
        LuaC.install(this)
    }
}
