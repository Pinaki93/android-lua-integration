package com.example.luaplayground

import org.luaj.vm2.Globals
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
    ): LuaResult {
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
