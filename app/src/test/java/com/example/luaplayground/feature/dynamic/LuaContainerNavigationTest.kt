package com.example.luaplayground.feature.dynamic

import com.example.luacompose.JsonStore
import com.example.luaplayground.MainDispatcherRule
import com.example.luaplayground.NavigationEvent
import com.example.luaplayground.Navigator
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LuaContainerNavigationTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test fun `navigation uses the view model scope`() = runTest(mainDispatcherRule.dispatcher) {
        val navigator = RecordingNavigator()
        val container = LuaContainer(
            { File("build/generated/luaAssets/playground.luac").readBytes() },
            navigator,
            { JsonStore({ null }, {}, {}) },
            mainDispatcherRule.dispatcher,
        )
        val viewModel = container.createVm()
        advanceUntilIdle()

        viewModel.action("playground.dashboard")
        advanceUntilIdle()

        assertEquals(listOf("dashboard"), navigator.routes)
    }

    private class RecordingNavigator : Navigator {
        override val events: SharedFlow<NavigationEvent> = MutableSharedFlow()
        val routes = mutableListOf<String>()

        override suspend fun navigate(route: String) {
            routes += route
        }

        override suspend fun popBackStack() = Unit
    }
}
