package com.example.luacompose

import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReadingListTest {
    private class Memory(initial: String? = null) {
        var bytes = initial?.toByteArray()
        var writes = 0
        var failWrite = false
        var failRead = false
        val store = JsonStore(
            read = { if (failRead) error("unavailable"); bytes?.copyOf() },
            write = { if (failWrite) error("unavailable"); bytes = it.copyOf(); writes++ },
            delete = { error("The document must never be reset") },
        )
        fun document() = Json.parseToJsonElement(bytes!!.toString(Charsets.UTF_8)).jsonObject
        fun articles() = document()["articles"]!!.jsonArray
        fun article() = articles().single().jsonObject
        fun snapshot() = bytes?.toString(Charsets.UTF_8)
    }

    private class Harness(val scope: TestScope, val memory: Memory = Memory(), compiled: Boolean = false,
        identifier: (() -> String)? = null) {
        val requests = mutableListOf<String>()
        val completions = mutableListOf<Pair<Long, LuaHttpClient.Response>>()
        val browsers = mutableListOf<String>()
        var browserFailure = false
        var nextId = 0
        var instant = 1000L
        var cancelled = 0
        var reply: suspend (String) -> LuaHttpClient.Response = { LuaHttpClient.Response(error = "transport") }
        val capability = ReadingCapabilities(
            fetchTitle = { url ->
                requests += url
                try { reply(url) } catch (cancelled: CancellationException) { this.cancelled++; throw cancelled }
            },
            openOriginal = { if (browserFailure) error("no browser"); browsers += it },
            identifier = identifier ?: { "id-${++nextId}" },
            now = { instant },
        )
        val session = if (compiled) LuaSession(
            compile(File("../lua/reading-list.lua").readText()), { memory.store },
            scope = scope, completed = { id, result -> completions += id to result }, reading = capability,
        ) else LuaSession(
            File("../lua/reading-list.lua").readText(), { memory.store },
            scope = scope, completed = { id, result -> completions += id to result }, reading = capability,
        )
        var result = session.start()
        fun action(action: String) { result = session.dispatch(LuaEvent.Action("reading.$action")); nodes() }
        fun field(field: String, text: String) { result = session.dispatch(LuaEvent.TextChanged("reading.field.$field", text)); nodes() }
        fun add(url: String = "https://example.com/article", title: String = "", tags: String = "", note: String = "") {
            action("back"); action("add"); field("url", url); field("title", title); field("tags", tags); field("note", note); action("save")
        }
        fun deliver(index: Int = 0) {
            val (id, response) = completions.removeAt(index)
            result = session.complete(id, response)
            nodes()
        }
        fun nodes(): List<UiNode> {
            assertTrue("Expected rendered UI, got $result", result is LuaUiResult.Success)
            return (result as LuaUiResult.Success).root.flatten()
        }
        fun texts() = nodes().mapNotNull {
            when (it) {
                is UiNode.Text -> it.text
                is UiNode.Button -> it.text
                is UiNode.TextField -> it.error
                else -> null
            }
        }
        fun value(field: String) = nodes().filterIsInstance<UiNode.TextField>().single { it.action == "reading.field.$field" }.value
    }

    @Test fun `editorial states expose selected filters clickable cards and multiline notes`() = runTest {
        val h = Harness(this)
        assertTrue(h.nodes().filterIsInstance<UiNode.Text>().any { it.style == UiTextStyle.Heading })
        assertTrue(h.nodes().filterIsInstance<UiNode.Card>().any { it.style == UiCardStyle.Subtle })
        assertEquals(2, h.nodes().filterIsInstance<UiNode.Button>().count { it.style == UiButtonStyle.Selected })
        assertTrue(h.nodes().filterIsInstance<UiNode.Row>().all { it.wrap })
        h.add(title = "Article", tags = "Tech", note = "Line one\nLine two")
        assertTrue(h.nodes().filterIsInstance<UiNode.TextField>().single { it.label == "Note" }.multiline)
        assertFalse(h.nodes().filterIsInstance<UiNode.Button>().single { it.text == "Save" }.enabled)
        h.action("back")
        val article = h.nodes().filterIsInstance<UiNode.Card>().single { it.action != null }
        assertEquals("reading.view.id-1", article.action)
        h.action("view.id-1")
        assertEquals("Line one\nLine two", h.value("note"))
        h.action("delete")
        assertEquals(UiButtonStyle.Destructive, h.nodes().filterIsInstance<UiNode.Button>().single { it.text == "Delete" }.style)
        h.session.close()
    }

    @Test fun `numeric UUID entries render after saving and restarting`() = runTest {
        val id = "12345678-1234-1234-1234-123456789abc"
        val memory = Memory()
        val first = Harness(this, memory, compiled = true, identifier = { id })
        first.add(title = "Saved article", note = "Saved note")
        first.action("back")
        assertEquals("reading.$id", first.nodes().filterIsInstance<UiNode.ListItem>().single().key)
        first.action("view.$id")
        assertEquals("Saved note", first.value("note"))
        first.session.close()

        val restored = Harness(this, memory, compiled = true)
        assertTrue(restored.texts().contains("Saved article"))
        restored.action("view.$id")
        assertEquals("Saved note", restored.value("note"))
        restored.session.close()
    }

    @Test fun `offline save restores all fields without network or automatic refresh`() = runTest {
        val memory = Memory()
        val first = Harness(this, memory)
        first.add(" HTTPS://EXAMPLE.COM:443/a?x=1#fragment ", " My title ", " Tech, tech, , Kotlin ", "My offline note")
        assertEquals(1, memory.writes)
        assertEquals("https://example.com/a?x=1", memory.article()["url"]!!.jsonPrimitive.content)
        assertEquals(listOf("Tech", "Kotlin"), memory.article()["tags"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertTrue(first.requests.isEmpty())
        first.session.close()
        val restored = Harness(this, memory)
        restored.action("view.id-1")
        assertEquals("My title", restored.value("title"))
        assertEquals("My offline note", restored.value("note"))
        assertEquals("Tech, Kotlin", restored.value("tags"))
        assertTrue(restored.texts().contains("Title: failed (interrupted; Retry)"))
        runCurrent()
        assertTrue(restored.requests.isEmpty())
        restored.session.close()
    }

    @Test fun `invalid URL and oversized fields preserve form and never write or fetch`() = runTest {
        val h = Harness(this)
        for (url in listOf("", "http://example.com", "https://u:p@example.com", "https://example.com:444", "//example.com", "https://example.com/" + "a".repeat(2049))) {
            h.add(url)
            assertEquals(url, h.value("url"))
            assertTrue(h.nodes().filterIsInstance<UiNode.TextField>().any { it.error != null })
        }
        for ((field, value) in listOf("title" to "😀".repeat(301), "note" to "a".repeat(4001), "tags" to "a,b,c,d,e,f", "tags" to "x".repeat(31))) {
            h.action("back"); h.action("add"); h.field("url", "https://example.com"); h.field(field, value); h.action("save")
            assertEquals(value, h.value(field))
            assertTrue(h.nodes().filterIsInstance<UiNode.TextField>().any { it.error != null })
        }
        assertEquals(0, h.memory.writes)
        runCurrent()
        assertTrue(h.requests.isEmpty())
    }

    @Test fun `field limits accept Unicode at the boundary`() = runTest {
        val h = Harness(this)
        h.add("https://example.com/" + "a".repeat(2028), "😀".repeat(300), "😀".repeat(30), "😀".repeat(4000))
        assertEquals(1, h.memory.writes)
        h.session.close()
    }

    @Test fun `duplicates open existing details and preserve user fields and read status`() = runTest {
        val h = Harness(this)
        h.add("https://example.com:443/a#first", "Original", "one", "Keep me")
        h.action("toggle")
        val before = h.memory.snapshot()
        h.add("HTTPS://EXAMPLE.COM/a#second", "Overwrite", "two", "bad")
        assertEquals(before, h.memory.snapshot())
        assertEquals("Original", h.value("title"))
        assertEquals("Keep me", h.value("note"))
        assertTrue(h.texts().contains("Already saved"))
        runCurrent()
        assertEquals(1, h.requests.size)
        h.deliver()
    }

    @Test fun `metadata success replaces only automatic fallback and persists it`() = runTest {
        val h = Harness(this)
        h.reply = { LuaHttpClient.Response(body = "Fetched title") }
        h.add()
        runCurrent(); h.deliver()
        assertEquals("Fetched title", h.value("title"))
        assertEquals("metadata", h.memory.article()["titleSource"]!!.jsonPrimitive.content)
        assertEquals("available", h.memory.article()["metadataState"]!!.jsonPrimitive.content)
        h.session.close()
        val restored = Harness(this, h.memory)
        restored.action("view.id-1")
        assertEquals("Fetched title", restored.value("title"))
    }

    @Test fun `metadata preserves supplied titles and unsaved or saved edits during fetch`() = runTest {
        for (saveEdit in listOf(false, true)) {
            val h = Harness(this)
            h.reply = { LuaHttpClient.Response(body = "Fetched") }
            h.add()
            h.field("title", "My edit")
            if (saveEdit) h.action("save")
            runCurrent(); h.deliver()
            assertEquals("My edit", h.value("title"))
            if (!saveEdit) h.action("save")
            assertEquals("My edit", h.memory.article()["title"]!!.jsonPrimitive.content)
        }
        val h = Harness(this)
        h.reply = { LuaHttpClient.Response(body = "Fetched") }
        h.add(title = "Supplied"); runCurrent(); h.deliver()
        assertEquals("Supplied", h.value("title"))
    }

    @Test fun `every title failure keeps local entry and permits retry`() = runTest {
        for (failure in listOf("timeout", "http", "redirect", "missing_title", "malformed", "unsupported", "response_size", "transport", "policy", "pending_limit")) {
            val h = Harness(this)
            h.reply = { LuaHttpClient.Response(error = failure) }
            h.add(note = "Still here"); runCurrent(); h.deliver()
            assertEquals("Still here", h.value("note"))
            assertEquals("failed", h.memory.article()["metadataState"]!!.jsonPrimitive.content)
            assertEquals("https://example.com/article", h.value("title"))
            assertTrue(h.texts().contains("Retry title"))
            if (failure == "redirect") assertTrue(h.texts().any { it.contains("final HTTPS URL") })
            h.reply = { LuaHttpClient.Response(body = "Recovered") }
            h.action("retry"); runCurrent(); h.deliver()
            assertEquals("Recovered", h.value("title"))
        }
    }

    @Test fun `obsolete title callbacks cannot overwrite newer requests`() = runTest {
        val h = Harness(this)
        var count = 0
        h.reply = { LuaHttpClient.Response(body = "Title ${++count}") }
        h.add(); runCurrent()
        h.action("retry"); runCurrent()
        h.deliver(1)
        val saved = h.memory.snapshot()
        h.deliver(0)
        assertEquals(saved, h.memory.snapshot())
        assertEquals("Title 2", h.value("title"))
    }

    @Test fun `delete confirmation and cancellation identify selected article and ignore late fetch`() = runTest {
        val h = Harness(this)
        h.reply = { LuaHttpClient.Response(body = "Too late") }
        h.add(title = "Delete this"); runCurrent()
        h.action("delete")
        assertEquals("Delete article?", h.nodes().filterIsInstance<UiNode.Dialog>().single().title)
        assertTrue(h.texts().contains("Delete this"))
        h.action("cancelDelete")
        assertEquals(1, h.memory.articles().size)
        h.action("delete"); h.action("confirmDelete")
        val deleted = h.memory.snapshot()
        h.deliver()
        assertEquals(deleted, h.memory.snapshot())
        assertEquals(0, h.memory.articles().size)
        assertTrue(Harness(this, h.memory).texts().contains("No articles yet. Add one to your queue."))
    }

    @Test fun `session closure cancels title work and ignores queued completions`() = runTest {
        val h = Harness(this)
        h.reply = { awaitCancellation() }
        h.add(); runCurrent()
        val saved = h.memory.snapshot()
        h.session.close(); runCurrent()
        assertEquals(1, h.cancelled)
        assertTrue(h.session.complete(1, LuaHttpClient.Response(body = "late")) is LuaUiResult.Failure)
        assertEquals(saved, h.memory.snapshot())
        assertTrue(h.completions.isEmpty())
    }

    @Test fun `failed mutations keep committed data and allow retry`() = runTest {
        val h = Harness(this)
        h.memory.failWrite = true
        h.add(title = "Draft")
        assertNull(h.memory.bytes)
        assertEquals("Draft", h.value("title"))
        assertTrue(h.requests.isEmpty())
        h.memory.failWrite = false; h.action("save")
        for (action in listOf("save", "toggle", "retry", "confirmDelete")) {
            h.field("note", "Edited")
            if (action == "confirmDelete") h.action("delete")
            val before = h.memory.snapshot()
            h.memory.failWrite = true; h.action(action)
            assertEquals(before, h.memory.snapshot())
            assertTrue(h.texts().contains("Could not save articles. Please try again."))
            h.memory.failWrite = false
        }
        h.action("cancelDelete"); h.action("save")
        assertEquals("Edited", h.memory.article()["note"]!!.jsonPrimitive.content)
        runCurrent()
        val before = h.memory.snapshot()
        h.memory.failWrite = true; h.deliver()
        assertEquals(before, h.memory.snapshot())
        assertTrue(h.texts().contains("Retry title"))
        h.memory.failWrite = false; h.action("retry"); runCurrent(); h.deliver()
        assertEquals("failed", h.memory.article()["metadataState"]!!.jsonPrimitive.content)
    }

    @Test fun `all combined filters use case insensitive tags and stable newest first ordering`() = runTest {
        val h = Harness(this)
        h.add("https://example.com/one", "One", "Tech, TECH, , Lua")
        h.action("toggle")
        h.add("https://example.com/two", "Two", "tech")
        h.instant = 2000
        h.add("https://example.com/three", "Three", "Other")
        h.action("back")
        fun titles() = h.nodes().filterIsInstance<UiNode.ListItem>().map { it.articleTitle() }
        assertEquals(listOf("Three", "Two", "One"), titles())
        h.action("tag.1")
        assertEquals(listOf("Two", "One"), titles())
        h.action("status.Read"); assertEquals(listOf("One"), titles())
        h.action("status.Unread"); assertEquals(listOf("Two"), titles())
        h.action("status.Read"); h.action("tag.4"); assertTrue(titles().isEmpty())
        assertTrue(h.texts().contains("No articles match these filters."))
        h.action("status.Unread"); h.action("tag.all"); assertEquals(listOf("Three", "Two"), titles())
        h.action("status.All"); assertEquals(listOf("Three", "Two", "One"), titles())
        h.session.close()
        assertTrue(Harness(this, h.memory).texts().contains("One"))
    }

    @Test fun `Unicode tag spelling is preserved and filtering folds case`() = runTest {
        val h = Harness(this)
        h.add("https://example.com/1", "One", "École, école")
        h.add("https://example.com/2", "Two", "école")
        assertEquals(listOf("école"), h.memory.articles()[1].jsonObject["tags"]!!.jsonArray.map { it.jsonPrimitive.content })
        h.action("back"); h.action("tag.1")
        assertEquals(2, h.nodes().filterIsInstance<UiNode.ListItem>().size)
        h.session.close()
    }

    @Test fun `browser launches only on explicit action and never marks read`() = runTest {
        val h = Harness(this)
        h.add()
        assertTrue(h.browsers.isEmpty())
        val before = h.memory.snapshot()
        h.action("open")
        assertEquals(listOf("https://example.com/article"), h.browsers)
        h.browserFailure = true; h.action("open")
        assertEquals(before, h.memory.snapshot())
        assertTrue(h.texts().contains("Could not open original. A browser and connection are needed."))
        h.session.close()
    }

    @Test fun `corrupt unknown and invalid records block all mutations and preserve document`() = runTest {
        val invalid = listOf(
            "broken", "{}", "{\"schemaVersion\":2,\"articles\":[]}", "{\"schemaVersion\":1,\"articles\":{}}",
            "{\"schemaVersion\":1,\"articles\":[{}]}", "{\"schemaVersion\":1,\"articles\":[null]}",
        )
        for (document in invalid) {
            val memory = Memory(document)
            val h = Harness(this, memory)
            assertTrue(h.texts().any { it.contains("Reload to recover") })
            h.action("add"); h.action("save")
            assertEquals(document, memory.snapshot())
            assertEquals(0, memory.writes)
            memory.bytes = "{\"schemaVersion\":1,\"articles\":[]}".toByteArray()
            h.action("reload")
            assertTrue(h.texts().contains("Add article"))
        }
    }

    @Test fun `unavailable storage is recoverable including factory failure`() = runTest {
        val memory = Memory().apply { failRead = true }
        val h = Harness(this, memory)
        assertTrue(h.texts().any { it.contains("Reload to recover") })
        memory.failRead = false; h.action("reload"); h.add(); h.session.close()
        val session = LuaSession(File("../lua/reading-list.lua").readText(), { error("unavailable") }, scope = this,
            completed = { _, _ -> }, reading = h.capability)
        val tree = session.start()
        assertTrue(tree is LuaUiResult.Success)
        assertTrue((tree as LuaUiResult.Success).root.flatten().filterIsInstance<UiNode.Button>().any { it.text == "Reload saved articles" })
    }

    @Test fun `quota sized lists paginate safely and oversized writes retain previous data`() = runTest {
        val h = Harness(this)
        h.add(title = "Article", note = "😀".repeat(4000))
        h.session.close()
        val template = h.memory.article()
        val entries = mutableListOf<JsonElement>()
        for (id in 1..15) entries += JsonObject(template + mapOf("id" to JsonPrimitive("id-$id"), "url" to JsonPrimitive("https://example.com/$id")))
        val document = JsonObject(mapOf("schemaVersion" to JsonPrimitive(1), "articles" to JsonArray(entries))).toString()
        assertTrue(document.toByteArray().size < JsonStore.MAX_DOCUMENT_BYTES)
        val memory = Memory(document)
        val restored = Harness(this, memory)
        val before = memory.snapshot()
        restored.nextId = 16
        restored.add("https://example.com/16", note = "😀".repeat(4000))
        assertTrue(memory.articles().size == 16)
        restored.nextId = 17
        restored.add("https://example.com/17", note = "😀".repeat(4000))
        assertTrue(restored.texts().contains("Storage is full. Shorten content or delete entries."))
        assertEquals(16, memory.articles().size)
        assertTrue(before != memory.snapshot())
        restored.session.close()
    }

    @Test fun `every invalid saved field blocks recovery without skipping records`() = runTest {
        val seed = Harness(this)
        seed.add(title = "User title", tags = "One", note = "Note")
        seed.session.close()
        val article = seed.memory.article()
        val invalid = listOf(
            "id" to JsonPrimitive(""), "id" to JsonPrimitive("../bad"),
            "url" to JsonPrimitive("http://example.com"), "url" to JsonPrimitive("https://EXAMPLE.COM:443/a#fragment"),
            "title" to JsonPrimitive(""), "title" to JsonPrimitive("x".repeat(301)),
            "titleSource" to JsonPrimitive("unknown"), "titleSource" to JsonPrimitive("fallback"),
            "tags" to JsonObject(emptyMap()), "tags" to JsonArray(listOf(JsonPrimitive(""))),
            "tags" to JsonArray(listOf(JsonPrimitive("One"), JsonPrimitive("one"))),
            "tags" to JsonArray(listOf(JsonPrimitive(" untrimmed "))),
            "tags" to JsonArray(listOf(JsonPrimitive("x".repeat(31)))),
            "tags" to JsonArray((1..6).map { JsonPrimitive("Tag $it") }),
            "note" to JsonPrimitive("x".repeat(4001)), "note" to JsonNull,
            "isRead" to JsonPrimitive("false"), "createdAt" to JsonPrimitive(-1),
            "createdAt" to JsonPrimitive(1.5), "metadataState" to JsonPrimitive("unknown"),
        )
        for ((field, value) in invalid) {
            val document = JsonObject(mapOf("schemaVersion" to JsonPrimitive(1),
                "articles" to JsonArray(listOf(JsonObject(article + (field to value)))))).toString()
            val memory = Memory(document)
            val h = Harness(this, memory)
            assertTrue("$field = $value", h.texts().any { it.contains("Reload to recover") })
            h.action("add")
            assertEquals(document, memory.snapshot())
            assertEquals(0, memory.writes)
            assertTrue(h.requests.isEmpty())
        }
        for (duplicate in listOf(article, JsonObject(article + ("id" to JsonPrimitive("another"))),
                JsonObject(article + ("url" to JsonPrimitive("https://example.com/another"))))) {
            val document = JsonObject(mapOf("schemaVersion" to JsonPrimitive(1), "articles" to JsonArray(listOf(article, duplicate)))).toString()
            assertTrue(Harness(this, Memory(document)).texts().any { it.contains("Reload to recover") })
        }
    }

    @Test fun `exact document boundary and many distinct tags remain navigable without startup writes`() = runTest {
        val seed = Harness(this)
        seed.add(title = "Title"); seed.session.close()
        val template = seed.memory.article()
        val entries = (1..60).map { id -> JsonObject(template + mapOf(
            "id" to JsonPrimitive("id-$id"), "url" to JsonPrimitive("https://example.com/$id"),
            "title" to JsonPrimitive("Title $id"), "createdAt" to JsonPrimitive(id),
            "tags" to JsonArray((1..5).map { JsonPrimitive("Tag $id-$it") }),
        )) }
        val document = JsonObject(mapOf("schemaVersion" to JsonPrimitive(1), "articles" to JsonArray(entries))).toString()
        val boundary = document + " ".repeat(JsonStore.MAX_DOCUMENT_BYTES - document.toByteArray().size)
        val memory = Memory(boundary)
        val h = Harness(this, memory)
        assertEquals(20, h.nodes().filterIsInstance<UiNode.ListItem>().size)
        assertEquals("Title 60", h.nodes().filterIsInstance<UiNode.ListItem>().first().articleTitle())
        h.action("next")
        assertEquals("Title 40", h.nodes().filterIsInstance<UiNode.ListItem>().first().articleTitle())
        h.action("previous")
        h.action("nextTags")
        assertTrue(h.texts().contains("Tag: Tag 5-1"))
        h.action("previousTags")
        assertTrue(h.texts().contains("Tag: Tag 1-1"))
        assertEquals(0, memory.writes)
        assertEquals(boundary, memory.snapshot())
    }

    @Test fun `full article and tag pages accept maximum length content`() = runTest {
        val seed = Harness(this)
        seed.add(title = "Title"); seed.session.close()
        val entries = (1..21).map { id -> JsonObject(seed.memory.article() + mapOf(
            "id" to JsonPrimitive("id-$id"),
            "url" to JsonPrimitive("https://example.com/" + "x".repeat(2000) + id),
            "title" to JsonPrimitive(if (id % 2 == 0) "https://example.com/" + "x".repeat(2000) + id else "😀".repeat(300)),
            "titleSource" to JsonPrimitive(if (id % 2 == 0) "fallback" else "user"),
            "tags" to JsonArray((1..5).map { JsonPrimitive("$id-$it" + "x".repeat(25)) }),
            "note" to JsonPrimitive("x".repeat(4000)),
        )) }
        val memory = Memory(JsonObject(mapOf("schemaVersion" to JsonPrimitive(1), "articles" to JsonArray(entries))).toString())
        val h = Harness(this, memory)
        assertEquals(20, h.nodes().filterIsInstance<UiNode.ListItem>().size)
        assertTrue(h.nodes().size < LuaUiEngine.MAX_NODES)
        h.action("next")
        assertEquals(1, h.nodes().filterIsInstance<UiNode.ListItem>().size)
        h.action("view.id-1")
        assertEquals(4000, h.value("note").length)
        h.session.close()
    }

    @Test fun `four pending title calls bound concurrency and overflow remains retryable`() = runTest {
        val h = Harness(this)
        h.reply = { awaitCancellation() }
        for (index in 1..5) h.add("https://example.com/$index", "Article $index")
        runCurrent()
        assertEquals(4, h.requests.size)
        assertEquals("pending_limit", h.completions.single().second.error)
        h.deliver()
        assertEquals("failed", h.memory.articles().last().jsonObject["metadataState"]!!.jsonPrimitive.content)
        assertTrue(h.texts().contains("Retry title"))
        h.session.close(); runCurrent()
        assertEquals(4, h.cancelled)
    }

    @Test fun `confirmation removes only the selected article and cancel prevents hidden edits`() = runTest {
        val h = Harness(this)
        h.add("https://example.com/one", "One")
        h.add("https://example.com/two", "Two")
        h.action("delete")
        val before = h.memory.snapshot()
        h.action("toggle"); h.field("note", "hidden edit"); h.action("save")
        assertEquals(before, h.memory.snapshot())
        h.action("cancelDelete")
        h.action("delete"); h.action("confirmDelete")
        assertEquals("One", h.memory.article()["title"]!!.jsonPrimitive.content)
        h.session.close()
        val restored = Harness(this, h.memory)
        assertTrue(restored.texts().contains("One"))
        assertFalse(restored.texts().contains("Two"))
    }

    @Test fun `compiled script exposes required fields actions and offline wording`() = runTest {
        val h = Harness(this, compiled = true)
        assertTrue(h.texts().containsAll(listOf("Reading list", "Saved list and notes available offline", "Add article")))
        h.action("add")
        assertEquals(listOf("HTTPS URL", "Title", "Tags (comma-separated)", "Note"), h.nodes().filterIsInstance<UiNode.TextField>().map { it.label })
        h.field("url", "https://example.com"); h.action("save")
        assertTrue(h.texts().containsAll(listOf("Mark read", "Retry title", "Open original", "Delete article")))
        h.action("toggle")
        assertTrue(h.texts().contains("Mark unread"))
        assertTrue(h.memory.article()["isRead"]!!.jsonPrimitive.boolean)
        h.action("delete")
        assertTrue(h.texts().containsAll(listOf("Cancel", "Delete")))
        h.session.close()
    }
}

private fun UiNode.flatten(): List<UiNode> = listOf(this) + when (this) {
    is UiNode.Column -> children.flatMap { it.flatten() }
    is UiNode.Row -> children.flatMap { it.flatten() }
    is UiNode.Card -> children.flatMap { it.flatten() }
    is UiNode.ListItem -> children.flatMap { it.flatten() }
    is UiNode.Dialog -> children.flatMap { it.flatten() }
    else -> emptyList()
}

private fun compile(source: String): ByteArray {
    val prototype = org.luaj.vm2.compiler.LuaC.instance.compile(source.byteInputStream(), "reading-list.lua")
    return java.io.ByteArrayOutputStream().also { org.luaj.vm2.compiler.DumpState.dump(prototype, it, true) }.toByteArray()
}

private fun UiNode.ListItem.articleTitle(): String {
    val card = children.single() as UiNode.Card
    val content = card.children.single() as UiNode.Column
    return (content.children.first() as UiNode.Text).text
}
