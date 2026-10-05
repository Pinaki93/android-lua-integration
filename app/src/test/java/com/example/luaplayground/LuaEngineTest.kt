package com.example.luaplayground

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LuaEngineTest {
    private val engine = LuaEngine()

    @Test fun `print writes a newline`() {
        assertOutput("hello\n", "print('hello')")
    }

    @Test fun `print separates values with tabs`() {
        assertOutput("one\t2\ttrue\n", "print('one', 2, true)")
    }

    @Test fun `print with no values writes an empty line`() {
        assertOutput("\n", "print()")
    }

    @Test fun `io write does not add spaces or a newline`() {
        assertOutput("answer=42", "io.write('answer=', 42)")
    }

    @Test fun `io write and print share output in order`() {
        assertOutput("Name: Ada\n", "io.write('Name: '); print('Ada')")
    }

    @Test fun `io read consumes one line at a time`() {
        assertOutput("Ada\nLovelace\n", "print(io.read()); print(io.read())", "Ada\nLovelace")
    }

    @Test fun `io read accepts explicit line format`() {
        assertOutput("Ada\n", "print(io.read('*l'))", "Ada")
    }

    @Test fun `io read returns nil at end of input`() {
        assertOutput("true\n", "print(io.read() == nil)")
    }

    @Test fun `empty input line remains an empty string`() {
        assertOutput("true\n", "print(io.read() == '')", "\n")
    }

    @Test fun `unsupported read format is a readable error`() {
        val result = engine.execute("io.read('*n')")

        assertFalse(result.succeeded)
        assertTrue(result.error.orEmpty().contains("supports only line input"))
    }

    @Test fun `syntax error reports the script name`() {
        val result = engine.execute("this is not lua", name = "broken.lua")

        assertFalse(result.succeeded)
        assertTrue(result.error.orEmpty().contains("broken.lua"))
    }

    @Test fun `runtime error keeps output produced before failure`() {
        val result = engine.execute("print('before'); error('boom')")

        assertEquals("before\n", result.output)
        assertTrue(result.error.orEmpty().contains("boom"))
    }

    @Test fun `successful execution has no error`() {
        val result = engine.execute("local value = 6 * 7; print(value)")

        assertTrue(result.succeeded)
        assertNull(result.error)
        assertEquals("42\n", result.output)
    }

    @Test fun `standard string table and math libraries are available`() {
        val script = """
            local values = {3, 1, 2}
            table.sort(values)
            print(string.upper(table.concat(values, ',')))
            print(math.floor(4.9))
        """.trimIndent()

        assertOutput("1,2,3\n4\n", script)
    }

    @Test fun `coroutines are available`() {
        val script = """
            local task = coroutine.create(function() coroutine.yield('paused') end)
            local ok, value = coroutine.resume(task)
            print(ok, value)
        """.trimIndent()

        assertOutput("true\tpaused\n", script)
    }

    @Test fun `executions do not leak globals into each other`() {
        engine.execute("secret = 'first'")

        assertOutput("nil\n", "print(secret)")
    }

    @Test fun `loops and functions execute normally`() {
        val script = """
            local function sum(limit)
              local total = 0
              for number = 1, limit do total = total + number end
              return total
            end
            print(sum(100))
        """.trimIndent()

        assertOutput("5050\n", script)
    }

    @Test fun `flush is supported as a harmless console operation`() {
        assertOutput("ready", "io.write('ready'); assert(io.flush())")
    }

    private fun assertOutput(expected: String, script: String, input: String = "") {
        val result = engine.execute(script, input)
        assertNull(result.error)
        assertEquals(expected, result.output)
    }
}
