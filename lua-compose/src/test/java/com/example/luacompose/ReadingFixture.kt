package com.example.luacompose

import java.io.File

internal fun readingFixture(): String {
    val directory = File("../lua/reading-list")
    fun module(name: String) = "(function()\n" + directory.resolve("$name.lua").readText() + "\nend)()"
    return """
        local modules = {}
        local function featureModule(name) return assert(modules[name], name) end
        modules["common-ui"] = ${module("common-ui")}
        modules["reading-interactor"] = ${module("reading-interactor")}
        modules["base-controller"] = ${module("base-controller")}
        modules["reading-list"] = ${module("reading-list")}
        modules["reading-list-add"] = ${module("reading-list-add")}

        -- Keep both controllers alive to exercise late callbacks across fixture navigation.
        local base = modules["base-controller"].new()
        modules["base-controller"].new = function() return base end
        local listing, form, active
        local navigation = { arguments = {} }
        navigation.back = function()
          active = listing
          base.view.message = nil
        end
        navigation.navigate = function(route)
          if not base.view.loaded then return end
          active = form
          local action = route == "reading-list/add" and "add" or "view." .. string.sub(route, 19)
          form.onEvent { type = "action", action = "reading." .. action }
        end
        listing = ${module("reading-list-controller")}
        form = ${module("reading-list-add-controller")}
        active = listing
        return {
          render = function() return active.render() end,
          onEvent = function(event) active.onEvent(event) end,
          onResume = function() if active == listing then listing.onResume() end end,
        }
        """.trimIndent()
}
