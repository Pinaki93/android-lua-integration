package com.example.luacompose

import java.io.File
import org.junit.Test
import org.luaj.vm2.lib.jse.JsePlatform

class ReadingInteractorTest {
    @Test fun `article operations need no presentation capabilities and retain data on failed writes`() {
        val globals = JsePlatform.standardGlobals()
        globals.load("""
            local saved = nil
            local fail = false
            reading = {
              trim = function(value) return value:match("^%s*(.-)%s*$") end,
              lower = string.lower,
              isArray = function(value) return type(value) == "table" end,
              length = string.len,
              normalize = function(value)
                assert(value:match("^https://"))
                return value
              end,
              id = function() return "article-1" end,
              now = function() return 1000 end,
            }
            local function write(document)
              if fail then error("write failed") end
              saved = document
            end
            storage = { open = function()
              return { read = function() return saved end, create = write, update = write }
            end }
            ui, navigation = nil, nil
            local Interactor = (function()
            ${File("../lua/reading-list/reading-interactor.lua").readText()}
            end)()
            local interactor = Interactor.new()
            assert(interactor.load())
            assert(interactor.render == nil and interactor.onEvent == nil)
            local invalid, errors = interactor.validate { url = "http://example.com", title = "", tags = "", note = "" }
            assert(invalid == nil and errors.url == "url")
            local form = interactor.validate { url = "https://example.com", title = "", tags = " Lua, lua ", note = "Note" }
            assert(#form.tags == 1 and form.tags[1] == "Lua")
            local article = interactor.add(form)
            assert(article.title == article.url and article.titleSource == "fallback")
            local duplicate, reason = interactor.add(form)
            assert(duplicate == article and reason == "duplicate" and #saved.articles == 1)
            local updated, failure, previous = interactor.updateMetadata(article.id, { title = "Fetched" })
            assert(failure == nil and previous == article and updated.title == "Fetched")
            assert(interactor.change(article.id, "toggle"))
            local committed = saved
            fail = true
            local ok, reason = interactor.change(article.id, "delete")
            assert(not ok and reason == "save")
            assert(saved == committed and #interactor.articles() == 1)
            fail = false
            assert(interactor.change(article.id, "delete"))
            assert(#saved.articles == 0)
            assert(interactor.updateMetadata(article.id, { title = "Late" }) == nil)
            saved = nil
            local sources = Interactor.new(true)
            assert(sources.load())
            local source = sources.add(form)
            assert(source.isRead == nil)
            assert(not sources.change(source.id, "toggle"))
            assert(sources.load())
            local committed = saved
            saved.articles[1].isRead = false
            assert(not sources.load())
            local ok, reason = sources.change(source.id, "delete")
            assert(not ok and reason == "load")
            assert(saved == committed)

        """.trimIndent(), "interactor-test").call()
    }
}
