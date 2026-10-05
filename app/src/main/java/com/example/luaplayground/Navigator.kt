package com.example.luaplayground

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

sealed interface NavigationEvent {
    data class Navigate(val route: String) : NavigationEvent
    data object PopBackStack : NavigationEvent
}

interface Navigator {
    val events: SharedFlow<NavigationEvent>

    suspend fun navigate(route: String)

    suspend fun popBackStack()
}

object AppNavigator : Navigator {
    private val mutableEvents = MutableSharedFlow<NavigationEvent>(extraBufferCapacity = 2)
    override val events = mutableEvents.asSharedFlow()

    override suspend fun navigate(route: String) {
        mutableEvents.emit(NavigationEvent.Navigate(route))
    }

    override suspend fun popBackStack() {
        mutableEvents.emit(NavigationEvent.PopBackStack)
    }
}
