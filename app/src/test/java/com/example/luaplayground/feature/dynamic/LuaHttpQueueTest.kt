package com.example.luaplayground.feature.dynamic

import com.example.luacompose.*
import com.example.luaplayground.MainDispatcherRule
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import kotlinx.coroutines.test.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class LuaHttpQueueTest {
    @get:Rule val main = MainDispatcherRule()

    @Test fun `optimistic typing preserves card actions wrapping and multiline properties`() = runTest(main.dispatcher) {
        val field = UiNode.TextField("", "Note", "note", multiline = true)
        val root = UiNode.Card(listOf(UiNode.Row(listOf(field), wrap = true)), action = "open")
        val vm = LuaContainerVm(LuaSession("return {}"), main.dispatcher, LuaUiResult.Success(root))
        vm.input(UiInput.TextChanged("note", "Typed\nNote"))
        val card = (vm.result as LuaUiResult.Success).root as UiNode.Card
        val row = card.children.single() as UiNode.Row
        assertEquals("open", card.action)
        assertEquals(true, row.wrap)
        assertEquals(field.copy(value = "Typed\nNote"), row.children.single())
        advanceUntilIdle()
    }

    @Test fun `completion queued before newer input survives revision changes`() = runTest(main.dispatcher) {
        val http = LuaHttpClient(HttpClient(MockEngine(MockEngineConfig().apply {
            dispatcher = main.dispatcher
            addHandler { respond("network") }
        })), { _, _ -> true })
        lateinit var completion: (Long, LuaHttpClient.Response) -> Unit
        var requestId = 0L
        val vm = LuaContainerVm({ scope, completed ->
            completion = completed
            LuaSession("""
                local text, network = '', 'waiting'
                http.request({url='https://example.com'},function(r) network=r.body end)
                return {
                  render=function() return ui.column{children={
                    ui.text{text=network},ui.textField{label='Input',value=text,action='input'}
                  }} end,
                  onEvent=function(e) text=e.value end
                }
            """, storage={JsonStore({null},{},{})}, http=http, scope=scope,
                completed={id, _ -> requestId=id})
        }, main.dispatcher)
        advanceUntilIdle()
        completion(requestId,LuaHttpClient.Response(body="network"))
        vm.input(UiInput.TextChanged("input","typed"))
        advanceUntilIdle()
        val root=(vm.result as LuaUiResult.Success).root as UiNode.Column
        assertEquals("network",(root.children[0] as UiNode.Text).text)
        assertEquals("typed",(root.children[1] as UiNode.TextField).value)
    }
}
