package com.example.luaplayground

import org.luaj.vm2.Globals
import org.luaj.vm2.LuaError
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.LoadState
import org.luaj.vm2.Varargs
import org.luaj.vm2.compiler.LuaC
import org.luaj.vm2.lib.BaseLib
import org.luaj.vm2.lib.Bit32Lib
import org.luaj.vm2.lib.CoroutineLib
import org.luaj.vm2.lib.OneArgFunction
import org.luaj.vm2.lib.PackageLib
import org.luaj.vm2.lib.StringLib
import org.luaj.vm2.lib.TableLib
import org.luaj.vm2.lib.VarArgFunction
import org.luaj.vm2.lib.jse.JseMathLib
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.StringReader

data class LuaResult(val output: String, val error: String? = null) {
    val succeeded: Boolean get() = error == null
}

class LuaEngine {
    fun execute(script: String, input: String = "", name: String = "script.lua") = execute(input) { globals ->
        globals.load(script, name)
    }

    fun execute(script: ByteArray, input: String = "", name: String = "script.luac") = execute(input) { globals ->
        globals.load(ByteArrayInputStream(script), name, "bt", globals)
    }

    private fun execute(input: String, load: (Globals) -> LuaValue): LuaResult {
        val output = StringBuilder()
        val globals = globals(BufferedReader(StringReader(input)), output)

        return try {
            load(globals).call()
            LuaResult(output.toString())
        } catch (error: LuaError) {
            LuaResult(output.toString(), error.message ?: "Lua execution failed")
        }
    }

    private fun globals(input: BufferedReader, output: StringBuilder) = Globals().apply {
        load(BaseLib())
        load(PackageLib())
        load(Bit32Lib())
        load(TableLib())
        load(StringLib())
        load(CoroutineLib())
        load(JseMathLib())
        LoadState.install(this)
        LuaC.install(this)

        set("print", Print(output))
        set("io", LuaTable().apply {
            set("read", Read(input))
            set("write", Write(output))
            set("flush", object : OneArgFunction() {
                override fun call(argument: LuaValue) = LuaValue.TRUE
            })
        })
    }

    private class Read(private val input: BufferedReader) : VarArgFunction() {
        override fun invoke(arguments: Varargs): Varargs {
            if (arguments.narg() > 0 && arguments.checkjstring(1) != "*l") {
                throw LuaError("io.read supports only line input (*l)")
            }
            return input.readLine()?.let(LuaValue::valueOf) ?: LuaValue.NIL
        }
    }

    private class Write(private val output: StringBuilder) : VarArgFunction() {
        override fun invoke(arguments: Varargs): Varargs {
            for (index in 1..arguments.narg()) output.append(arguments.checkvalue(index).tojstring())
            return LuaValue.TRUE
        }
    }

    private class Print(private val output: StringBuilder) : VarArgFunction() {
        override fun invoke(arguments: Varargs): Varargs {
            for (index in 1..arguments.narg()) {
                if (index > 1) output.append('\t')
                output.append(arguments.checkvalue(index).tojstring())
            }
            output.append('\n')
            return LuaValue.NONE
        }
    }
}
