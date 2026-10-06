package com.example.luaplayground.feature.playground

import com.example.luaplayground.CONSOLE_ROUTE
import com.example.luaplayground.DASHBOARD_ROUTE
import com.example.luaplayground.TODO_ROUTE
import com.example.luaplayground.MainDispatcherRule
import com.example.luaplayground.NavigationEvent
import com.example.luaplayground.Navigator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaygroundVmTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    @Test fun `open console navigates to the console`() {
        val navigator = RecordingNavigator()
        val viewModel = PlaygroundVm(navigator)

        viewModel.openConsole()
        mainDispatcher.dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(CONSOLE_ROUTE), navigator.routes)
    }

    @Test fun `open dashboard navigates to the dashboard`() {
        val navigator = RecordingNavigator()
        val viewModel = PlaygroundVm(navigator)

        viewModel.openDashboard()
        mainDispatcher.dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(DASHBOARD_ROUTE), navigator.routes)
    }

    @Test fun `open todo navigates to todo`() {
        val navigator = RecordingNavigator()
        val viewModel = PlaygroundVm(navigator)

        viewModel.openTodo()
        mainDispatcher.dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(TODO_ROUTE), navigator.routes)
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
