package com.example.luaplayground

import java.io.File

private fun readingFixture(): String {
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
        local sourceBase
        local createBase = modules["base-controller"].new
        modules["base-controller"].new = function(source)
          if not source then return base end
          sourceBase = sourceBase or createBase(true)
          return sourceBase
        end
        local listing, form, sourceForm, active
        local stack = {}
        local navigation = { arguments = {} }
        navigation.back = function()
          active = table.remove(stack) or listing
          base.view.message = nil
          if active.onResume then active.onResume() end
        end
        navigation.navigate = function(route)
          if not base.view.loaded then return end
          stack[#stack + 1] = active
          if route == "reading-list/add/source" and not sourceForm then
            navigation.arguments.kind = "source"
            sourceForm = ${module("reading-list-add-controller")}
            navigation.arguments.kind = nil
          end
          active = route == "reading-list/add/source" and sourceForm or form
          local action = (route == "reading-list/add" or route == "reading-list/add/source") and "add" or "view." .. string.sub(route, 19)
          active.onEvent { type = "action", action = "reading." .. action }
        end
        listing = ${module("reading-list-controller")}
        form = ${module("reading-list-add-controller")}

        active = listing
        return {
          render = function() return active.render() end,
          onEvent = function(event) active.onEvent(event) end,
          onResume = function() if active.onResume then active.onResume() end end,
        }
        """.trimIndent()
}

internal fun compiledReadingFixture(): ByteArray {
    val prototype = org.luaj.vm2.compiler.LuaC.instance.compile(readingFixture().byteInputStream(), "reading-test.lua")
    return java.io.ByteArrayOutputStream().also { org.luaj.vm2.compiler.DumpState.dump(prototype, it, true) }.toByteArray()
}
