package com.example.luaplayground

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LuaScriptsTest {
    private val engine = LuaEngine()

    @Test fun `hello greets a named person`() {
        assertScript("hello.lua", "Ada\n", "What is your name? Hello, Ada!\n")
    }

    @Test fun `hello handles missing input`() {
        assertScript("hello.lua", "", "What is your name? Hello, mysterious Lua programmer!\n")
    }

    @Test fun `calculator adds`() {
        assertCalculator("10\n+\n7", "17")
    }

    @Test fun `calculator subtracts`() {
        assertCalculator("10\n-\n7", "3")
    }

    @Test fun `calculator multiplies`() {
        assertCalculator("6\n*\n7", "42")
    }

    @Test fun `calculator divides`() {
        assertCalculator("9\n/\n2", "4.5")
    }

    @Test fun `calculator rejects division by zero`() {
        assertCalculator("9\n/\n0", "Cannot divide by zero.")
    }

    @Test fun `calculator rejects non numbers`() {
        assertCalculator("nine\n+\n2", "Both values must be numbers.")
    }

    @Test fun `calculator rejects unknown operators`() {
        assertCalculator("9\n%\n2", "Unknown operator: %")
    }

    @Test fun `word count counts words separated by varied whitespace`() {
        assertScript("word_count.lua", "one   two\tthree\n", "Enter a sentence:\nWords: 3\n")
    }

    @Test fun `word count handles an empty sentence`() {
        assertScript("word_count.lua", "\n", "Enter a sentence:\nWords: 0\n")
    }

    @Test fun `every bundled Lua file executes without an error`() {
        val inputs = mapOf(
            "hello.luac" to "Ada",
            "calculator.luac" to "1\n+\n2",
            "word_count.luac" to "hello world",
        )

        val scripts = compiledDirectory().listFiles().orEmpty().filter { it.extension == "luac" }
        assertEquals(inputs.keys, scripts.map { it.name }.toSet())
        scripts.forEach { script ->
            val result = engine.execute(script.readBytes(), inputs.getValue(script.name), script.name)
            assertTrue("${script.name}: ${result.error}", result.succeeded)
        }
    }

    private fun assertCalculator(input: String, expectedLastLine: String) {
        val result = runScript("calculator.lua", input)
        assertEquals(expectedLastLine, result.output.lineSequence().last { it.isNotEmpty() })
    }

    private fun assertScript(name: String, input: String, expected: String) {
        val result = runScript(name, input)
        assertNull(result.error)
        assertEquals(expected, result.output)
    }

    private fun runScript(name: String, input: String) =
        engine.execute(File(sourceDirectory(), name).readText(), input, name)

    private fun sourceDirectory() = File("../lua/console")
    private fun compiledDirectory() = File("build/generated/luaAssets/console")
}
