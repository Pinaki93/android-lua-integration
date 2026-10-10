package com.example.luaplayground

import com.example.luacompose.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import java.io.File
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ContributorsTest {
    private fun TestScope.client(handler: MockRequestHandler) = HttpClient(MockEngine(MockEngineConfig().apply {
        dispatcher = StandardTestDispatcher(testScheduler)
        addHandler(handler)
    }))
    @Test fun `permissions belong to the asset and reject URL bypasses`() {
        val client = HttpClient(MockEngine { error("must not send") })
        assertNull(httpForScript("todo.luac", client))
        assertNull(httpForScript("other/okhttp-contributors.luac", client))
        assertNotNull(httpForScript("okhttp-contributors.luac", client))
        val base = "https://api.github.com/repos/lysine-dev/okhttp/contributors?page=1&per_page=100&anon=1"
        assertTrue(contributorsRequest(HttpMethod.Get, Url(base)))
        assertFalse(contributorsRequest(HttpMethod.Post, Url(base)))
        listOf(
            base.replace("https:", "http:"), base.replace("api.github.com", "evil.com"),
            base.replace("api.github.com", "api.github.com:444"), base.replace("/contributors", "/contributors/"),
            base.replace("/contributors", "/%63ontributors"), base.replace("page=1", "page=0"),
            base.replace("page=1", "page=999999999999"), base + "&page=2", base + "&extra=1",
            base.replace("anon=1", "anon=0"), base.replace("per_page=100", "per_page=101"),
        ).forEach { assertFalse(it, contributorsRequest(HttpMethod.Get, Url(it))) }
        client.close()
    }

    private fun contributor(index: Int) = """{"login":"user$index","contributions":$index,"avatar_url":"https://avatars.githubusercontent.com/u/$index"}"""
    private fun page(from: Int, count: Int) = (from until from + count).joinToString(",", "[", "]", transform=::contributor)

    @Test fun `actual script fetches sequential pages preserves order and paginates UI`() = runTest {
        val requested = mutableListOf<String>()
        val client = client { request ->
            requested += request.url.parameters["page"]!!
            val body = if (requested.size == 1) page(1,100) else """[{"type":"Anonymous","name":"Someone","contributions":1}]"""
            respond(body, headers=headersOf("Content-Type","application/json"))
        }
        val queue = ArrayDeque<Pair<Long,LuaHttpClient.Response>>()
        val session = LuaSession(File("../lua/okhttp-contributors.lua").readText(), storage={JsonStore({null},{},{})},
            http=httpForScript("okhttp-contributors.luac",client), scope=this, completed={id,r->queue.add(id to r)})
        var result = session.start()
        suspend fun drain() {
            advanceUntilIdle()
            while (queue.isNotEmpty()) {
                val (id,r) = queue.removeFirst()
                result=session.complete(id,r)
                advanceUntilIdle()
            }
        }
        drain()
        assertEquals(listOf("1","2"),requested)
        assertTrue(result.texts().contains("101 contributors"))
        assertEquals(50,result.nodes().filterIsInstance<UiNode.ListItem>().size)
        assertTrue(result.texts().contains("user1"))
        assertEquals(UiNode.Image("https://avatars.githubusercontent.com/u/1", "user1 avatar", circleCrop = true),
            result.nodes().filterIsInstance<UiNode.Image>().first())
        assertEquals(50, result.nodes().filterIsInstance<UiNode.Image>().size)
        result=session.dispatch(LuaEvent.Action("contributors.next"))
        assertTrue(result.texts().contains("user51"))
        result=session.dispatch(LuaEvent.Action("contributors.next"))
        assertTrue(result.texts().contains("Someone (anonymous)"))
        assertEquals(listOf(UiNode.Image(null, "Someone avatar", circleCrop = true)),
            result.nodes().filterIsInstance<UiNode.Image>())
        assertEquals(1,result.nodes().filterIsInstance<UiNode.ListItem>().size)
        assertTrue(result.texts().contains("Page 3 of 3"))
        assertFalse(result.nodes().filterIsInstance<UiNode.Button>().single { it.action=="contributors.next" }.enabled)
        result=session.dispatch(LuaEvent.Action("contributors.previous"))
        assertTrue(result.texts().contains("Page 2 of 3"))
        session.close(); client.close()
    }

    @Test fun `failed page retains incomplete results retry resumes and refresh clears`() = runTest {
        var calls=0
        val pages=mutableListOf<String>()
        val client=client { request ->
            calls++; pages+=request.url.parameters["page"]!!
            when(calls) {
                1 -> respond(page(1,100),headers=headersOf("Content-Type","application/json"))
                2 -> respond("{}",HttpStatusCode.Forbidden,headersOf("x-ratelimit-remaining","0"))
                else -> respond("",HttpStatusCode.NoContent)
            }
        }
        val queue=ArrayDeque<Pair<Long,LuaHttpClient.Response>>()
        val session=LuaSession(File("../lua/okhttp-contributors.lua").readText(),storage={JsonStore({null},{},{})},
            http=httpForScript("okhttp-contributors.luac",client),scope=this,completed={id,r->queue.add(id to r)})
        var result=session.start()
        suspend fun drain() {
            advanceUntilIdle()
            while(queue.isNotEmpty()) {
                val(id,r)=queue.removeFirst();result=session.complete(id,r);advanceUntilIdle()
            }
        }
        assertFalse(result.nodes().filterIsInstance<UiNode.Button>().single {it.action=="contributors.refresh"}.enabled)
        drain()
        assertTrue(result.texts().contains("100 contributors fetched — incomplete"))
        assertTrue(result.texts().any {it.contains("GitHub rate limit. Remaining: 0")})
        session.dispatch(LuaEvent.Action("contributors.retry"));drain()
        assertEquals(listOf("1","2","2"),pages)
        assertTrue(result.texts().contains("100 contributors"))
        session.dispatch(LuaEvent.Action("contributors.refresh"));drain()
        assertTrue(result.texts().contains("No contributors found."))
        assertEquals("1",pages.last())
        session.close();client.close()
    }

    @Test fun `empty and malformed pages never become complete contributor lists`() = runTest {
        for(body in listOf("[]","{}","[{\"login\":\"x\"}]","[{\"login\":\"x\",\"contributions\":-1}]","[null]")) {
            val client=client {respond(body,headers=headersOf("Content-Type","application/json"))}
            val queue=ArrayDeque<Pair<Long,LuaHttpClient.Response>>()
            val session=LuaSession(File("../lua/okhttp-contributors.lua").readText(),storage={JsonStore({null},{},{})},
                http=httpForScript("okhttp-contributors.luac",client),scope=this,completed={id,r->queue.add(id to r)})
            session.start();advanceUntilIdle()
            val(id,r)=queue.removeFirst();val result=session.complete(id,r)
            assertTrue(result is LuaUiResult.Success)
            if(body=="[]") assertTrue(result.texts().contains("No contributors found."))
            else assertTrue(result.texts().contains("Invalid contributor data."))
            session.close();client.close()
        }
    }

    @Test fun `missing and unsafe avatars preserve contributor rows`() = runTest {
        for (avatar in listOf("null", "42", "\"file:///private/avatar\"",
            "\"https://evil.com/avatar\"", "\"https://avatars.githubusercontent.com.evil.com/a\"",
            "\"https://avatars.githubusercontent.com/a#fragment\"", "\"https://avatars.githubusercontent.com/a b\"")) {
            val client = client {
                respond("[{\"login\":\"Ada\",\"contributions\":2,\"avatar_url\":$avatar}]",
                    headers = headersOf("Content-Type", "application/json"))
            }
            val queue = ArrayDeque<Pair<Long, LuaHttpClient.Response>>()
            val session = LuaSession(File("../lua/okhttp-contributors.lua").readText(),
                storage = { JsonStore({ null }, {}, {}) },
                http = httpForScript("okhttp-contributors.luac", client), scope = this,
                completed = { id, response -> queue.add(id to response) })
            session.start()
            advanceUntilIdle()
            val (id, response) = queue.removeFirst()
            val result = session.complete(id, response)
            assertTrue(avatar, result.texts().contains("Ada"))
            assertEquals(avatar, listOf(UiNode.Image(null, "Ada avatar", circleCrop = true)),
                result.nodes().filterIsInstance<UiNode.Image>())
            session.close()
            client.close()
        }
    }

    private fun LuaUiResult.nodes(): List<UiNode> = (this as? LuaUiResult.Success)?.root?.flatten() ?: emptyList()
    private fun LuaUiResult.texts() = nodes().filterIsInstance<UiNode.Text>().map {it.text}
    private fun UiNode.flatten(): List<UiNode> = listOf(this) + when(this) {
        is UiNode.Column -> children.flatMap {it.flatten()}
        is UiNode.Row -> children.flatMap {it.flatten()}
        is UiNode.ListItem -> children.flatMap {it.flatten()}
        else -> emptyList()
    }
}
