package com.example.luacompose

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.utils.io.writeFully
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.luaj.vm2.*

@OptIn(ExperimentalCoroutinesApi::class)
class LuaHttpClientTest {
    private fun options(url: String = "https://example.com/path") = LuaTable().apply { set("url", url) }
    private fun TestScope.adapter(handler: MockRequestHandler): LuaHttpClient {
        val engine = MockEngine(MockEngineConfig().apply {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler(handler)
        })
        return LuaHttpClient(HttpClient(engine), { _, _ -> true })
    }

    @Test fun `all methods query headers and bodies reach the engine`() = runTest {
        val seen = mutableListOf<String>()
        val http = adapter { request ->
            seen += request.method.value
            assertEquals("a b", request.url.parameters["search"])
            assertEquals("value", request.headers["X-Test"])
            respond("ok")
        }
        for (method in listOf("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS")) {
            val input = options().apply {
                set("method", method)
                set("query", LuaTable().apply { set("search", "a b") })
                set("headers", LuaTable().apply { set("X-Test", "value") })
                if (method !in listOf("GET", "HEAD")) set("body", "hello")
            }
            assertEquals(if (method in listOf("GET", "HEAD")) null else "hello", http.parse(input).body)
            assertNull(http.execute(http.parse(input)).error)
        }
        assertEquals(7, seen.size)
        val input = options().apply { set("method", "POST"); set("json", LuaTable().apply { set("key", "value"); set("null", http.nullValue) }) }
        assertTrue(http.parse(input).body!!.contains("\"null\":null"))
        assertEquals("application/json", http.parse(input).headers["Content-Type"])
    }

    @Test fun `JSON null preserves indexes and HTTP errors remain responses`() = runTest {
        val http = adapter { respond("[null,2,null]", HttpStatusCode.Forbidden, headersOf("Content-Type", "application/json")) }
        val response = http.execute(http.parse(options()))
        assertEquals(403, response.status)
        assertNull(response.error)
        val array = http.table(response).get("json")
        assertEquals(3, array.length())
        assertSame(http.nullValue, array.get(1))
        assertSame(http.nullValue, array.get(3))
    }

    @Test fun `capability is frozen and closing cancels active requests`() = runTest {
        var cancelled = false
        val http = adapter {
            try { delay(30_000); respond("late") } finally { cancelled = true }
        }
        val completions = mutableListOf<LuaHttpClient.Response>()
        val session = LuaSession("""
            assert(not pcall(function() http.request = nil end))
            http.request({url='https://example.com'}, function() error('late') end)
            return {render=function() return ui.text{text='waiting'} end,onEvent=function() end}
        """, storage={JsonStore({null},{},{})}, http=http, scope=this, completed={_,r->completions += r})
        assertTrue(session.start() is LuaUiResult.Success)
        runCurrent()
        session.close()
        advanceUntilIdle()
        assertTrue(cancelled)
        assertTrue(completions.isEmpty())
    }

    @Test fun `oversized chunked response stops reading and closes the stream`() = runTest {
        val channel = io.ktor.utils.io.ByteChannel()
        val producer = backgroundScope.launch {
            repeat(257) {
                channel.writeFully(ByteArray(4096))
                channel.flush()
            }
            channel.flushAndClose()
        }
        val http = adapter { respond(channel, HttpStatusCode.OK) }
        assertEquals("response_size", http.execute(http.parse(options())).error)
        producer.cancel()
    }

    @Test fun `validation and policy failures never send`() = runTest {
        var sent = 0
        val http = adapter { sent++; respond("ok") }
        val invalid = listOf(
            options().apply { set("method", "TRACE") },
            options().apply { set("unknown", LuaValue.TRUE) },
            options().apply { set("body", 1) },
            options().apply { set("body", "GET cannot change into POST") },
            options().apply { set("method", "HEAD"); set("body", "x") },
            options().apply { set("query", LuaTable().apply { set("x", 1) }) },
            options().apply { set("headers", LuaTable().apply { set("Bad Header", "x") }) },
            options().apply { set("headers", LuaTable().apply { set("X", "\r\n") }) },
            options().apply { set("headers", LuaTable().apply { set("Host", "evil.com") }) },
            options().apply { set("body", "x"); set("json", LuaTable()) },
            options("https://user:pass@example.com/path"), options("https://example.com/path#fragment"),
            options("https://example.com/%70ath"), options("https://example.com/../path"),
            options().apply { set("method", "POST"); set("json", LuaTable().apply { set("cycle", this) }) },
        )
        invalid.forEach { try { http.parse(it); fail("Accepted $it") } catch (_: LuaHttpClient.Failure) {} }
        val denied = LuaHttpClient(HttpClient(MockEngine { sent++; respond("ok") }), { _, _ -> false })
        try { denied.parse(options()); fail() } catch (failure: LuaHttpClient.Failure) { assertEquals("policy", failure.code) }
        assertEquals(0, sent)
    }

    @Test fun `request and streamed response boundaries are exact`() = runTest {
        val http = adapter { respond(ByteReadChannel(ByteArray(LuaHttpClient.MAX_RESPONSE_BYTES)), HttpStatusCode.OK) }
        assertEquals(LuaHttpClient.MAX_REQUEST_BYTES, http.parse(options().apply { set("method", "POST"); set("body", "a".repeat(LuaHttpClient.MAX_REQUEST_BYTES)) }).body!!.length)
        try { http.parse(options().apply { set("method", "POST"); set("body", "a".repeat(LuaHttpClient.MAX_REQUEST_BYTES + 1)) }); fail() }
        catch (failure: LuaHttpClient.Failure) { assertEquals("request_size", failure.code) }
        assertEquals(LuaHttpClient.MAX_RESPONSE_BYTES, http.execute(http.parse(options())).body.length)
        val oversized = adapter { respond(ByteReadChannel(ByteArray(LuaHttpClient.MAX_RESPONSE_BYTES + 1)), HttpStatusCode.OK) }
        assertEquals("response_size", oversized.execute(oversized.parse(options())).error)
    }

    @Test fun `invalid JSON transport timeout and redirects are distinct`() = runTest {
        for (body in listOf("broken", "[".repeat(33) + "]".repeat(33))) {
            val http = adapter { respond(body, headers = headersOf("Content-Type", "application/json")) }
            assertEquals("invalid_json", http.execute(http.parse(options())).error)
        }
        val failed = adapter { throw java.io.IOException() }
        assertEquals("transport", failed.execute(failed.parse(options())).error)
        val timed = adapter { delay(30_001); respond("late") }
        assertEquals("timeout", timed.execute(timed.parse(options())).error)
        var sent = 0
        val redirect = adapter { sent++; respond("", HttpStatusCode.Found, headersOf("Location", "https://evil.com/")) }
        assertEquals(302, redirect.execute(redirect.parse(options())).status)
        assertEquals(1, sent)
    }

    @Test fun `session retains callbacks ignores duplicates and forbids render requests`() = runTest {
        val http = adapter { respond("ok") }
        val completions = mutableListOf<Pair<Long, LuaHttpClient.Response>>()
        val session = LuaSession("""
            local text = 'waiting'
            http.request({url='https://example.com'}, function(r) text=r.body end)
            return {render=function() return ui.text{text=text} end, onEvent=function() end}
        """.trimIndent(), storage = { JsonStore({null}, {}, {}) }, http = http, scope = this,
            completed = { id, response -> completions += id to response })
        assertTrue(session.start() is LuaUiResult.Success)
        advanceUntilIdle()
        assertEquals(1, completions.size)
        val (id, response) = completions.single()
        val result = session.complete(id, response)
        assertEquals("ok", ((result as LuaUiResult.Success).root as UiNode.Text).text)
        assertEquals(result, session.complete(id, LuaHttpClient.Response(body="duplicate")))
        session.close()
        assertTrue(session.complete(id, response) is LuaUiResult.Failure)
        val rendering = LuaSession("return {render=function() http.request({},function() end) end,onEvent=function() end}",
            storage={JsonStore({null}, {}, {})}, http=http, scope=this, completed={_,_->})
        assertTrue(rendering.start() is LuaUiResult.Failure)
        assertTrue(LuaSession("assert(http == nil); return {render=function() return ui.text{text='ok'} end,onEvent=function() end}").start() is LuaUiResult.Success)
    }

    @Test fun `pending limit callback failure and close cancel work`() = runTest {
        var cancelled = 0
        val http = adapter { try { delay(30_000); respond("ok") } finally { cancelled++ } }
        val completions = mutableListOf<Pair<Long, LuaHttpClient.Response>>()
        val session = LuaSession("""
            for i=1,5 do http.request({url='https://example.com'},function(r) error('failed') end) end
            return {render=function() return ui.text{text='waiting'} end,onEvent=function() end}
        """, storage={JsonStore({null}, {}, {})}, http=http, scope=this, completed={id,r->completions += id to r})
        session.start()
        runCurrent()
        assertEquals("pending_limit", completions.single().second.error)
        assertTrue(session.complete(completions.single().first, completions.single().second) is LuaUiResult.Failure)
        advanceUntilIdle()
        assertEquals(4, cancelled)
        assertEquals(1, completions.size)
    }
}
