package com.example.luaplayground.feature.playground

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.luaplayground.CONSOLE_ROUTE
import com.example.luaplayground.DASHBOARD_ROUTE
import com.example.luaplayground.Navigator
import com.example.luaplayground.TODO_ROUTE
import kotlinx.coroutines.launch

class PlaygroundVm(private val navigator: Navigator) : ViewModel() {
    fun openDashboard() {
        viewModelScope.launch { navigator.navigate(DASHBOARD_ROUTE) }
    }

    fun openConsole() {
        viewModelScope.launch { navigator.navigate(CONSOLE_ROUTE) }
    }

    fun openTodo() {
        viewModelScope.launch { navigator.navigate(TODO_ROUTE) }
    }
}
