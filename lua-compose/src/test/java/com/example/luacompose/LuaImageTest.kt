package com.example.luacompose

import org.junit.Assert.*
import org.junit.Test

class LuaImageTest {
    private val engine = LuaUiEngine()

    @Test fun `images parse through constructors and raw tables`() {
        val fields = "url='https://example.com/avatar.png', label='Ada avatar'"
        assertEquals(UiNode.Image("https://example.com/avatar.png", "Ada avatar"),
            (engine.evaluate("return ui.image {$fields}") as LuaUiResult.Success).root)
        assertEquals(UiNode.Image("https://example.com/avatar.png", "Ada avatar", 1, 1024),
            (engine.evaluate("return {type='image', $fields, width=1, height=1024}") as LuaUiResult.Success).root)
    }

    @Test fun `default avatars and circle cropping parse through both boundaries`() {
        for (constructor in listOf("ui.image", "")) {
            for (circleCrop in listOf(false, true)) {
                val script = "return $constructor {type='image', label='Avatar', circleCrop=$circleCrop}"
                assertEquals(UiNode.Image(null, "Avatar", circleCrop = circleCrop),
                    (engine.evaluate(script) as LuaUiResult.Success).root)
                assertEquals(UiNode.Image("https://example.com/a", "Avatar", circleCrop = circleCrop),
                    (engine.evaluate(script.replace("label=", "url='https://example.com/a', label=")) as LuaUiResult.Success).root)
            }
        }
    }

    @Test fun `images reject unsafe URLs`() {
        for (url in listOf("", "http://example.com/a", "file:///private/a", "content://a/b",
            "https:///a", "https://user:pass@example.com/a", "https://example.com:444/a",
            "https://example.com/a#fragment", "https://example.com/a b")) {
            assertTrue(url, engine.evaluate("return ui.image {url=state.url, label='Avatar'}",
                mapOf("url" to url)) is LuaUiResult.Failure)
        }
    }

    @Test fun `image fields are strict and bounded`() {
        val valid = "url='https://example.com/a', label='Avatar'"
        for (field in listOf("width=0", "width=1025", "height=-1", "height=1.5", "width='48'",
            "height=false", "circleCrop=1", "circleCrop='true'", "extra=true")) {
            assertTrue(field, engine.evaluate("return ui.image {$valid, $field}") is LuaUiResult.Failure)
        }
        for (fields in listOf("url='https://example.com/a'", "url='https://example.com/a', label=''",
            "url=1, label='Avatar'")) {
            assertTrue(fields, engine.evaluate("return ui.image {$fields}") is LuaUiResult.Failure)
        }
        assertTrue(engine.evaluate("return ui.image {url=state.url, label='Avatar'}",
            mapOf("url" to "https://example.com/" + "a".repeat(10_000))) is LuaUiResult.Failure)
        assertTrue(engine.evaluate("return ui.image {url='https://example.com/a', label=state.label}",
            mapOf("label" to "a".repeat(10_001))) is LuaUiResult.Failure)
    }
}
