package com.example.luaplayground

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class LuaRoute(val pattern: String, val script: String) {
    val arguments: List<String>
        get() = PLACEHOLDER.findAll(pattern).map { it.groupValues[1] }.toList()

    internal val shape: String
        get() = PLACEHOLDER.replace(pattern, "{}")

    private companion object {
        val PLACEHOLDER = Regex("\\{([A-Za-z][A-Za-z0-9_]*)\\}")
    }
}

sealed interface LuaRoutesState {
    data object Loading : LuaRoutesState
    data class Ready(
        val startRoute: String,
        val routes: List<LuaRoute>,
    ) : LuaRoutesState
    data object Failed : LuaRoutesState
}

class LuaRouteRegistry(
    private val staticRoutes: Set<String> = setOf(DASHBOARD_ROUTE),
) {
    private val mutableState = MutableStateFlow<LuaRoutesState>(LuaRoutesState.Loading)
    val state: StateFlow<LuaRoutesState> = mutableState.asStateFlow()

    internal fun accepts(startRoute: String?, routes: List<LuaRoute>): Boolean =
        startRoute != null && validRoutes(routes) &&
            '{' !in startRoute && (startRoute in staticRoutes || routes.any { it.pattern == startRoute })

    internal fun complete(startRoute: String, routes: List<LuaRoute>) {
        mutableState.value = LuaRoutesState.Ready(startRoute, routes.toList())
    }

    internal fun fail() {
        mutableState.value = LuaRoutesState.Failed
    }

    private fun validRoutes(routes: List<LuaRoute>): Boolean {
        val patterns = mutableSetOf<String>()
        val shapes = mutableSetOf<String>()
        return routes.all { route ->
            validPattern(route.pattern) && validScript(route.script) && route.pattern !in staticRoutes &&
                patterns.add(route.pattern) && shapes.add(route.shape)
        }
    }

    private fun validPattern(pattern: String): Boolean {
        if (pattern.isEmpty() || '?' in pattern || '#' in pattern) return false
        val arguments = mutableSetOf<String>()
        return pattern.split('/').all { segment ->
            LITERAL.matches(segment) || PLACEHOLDER.matches(segment) && arguments.add(segment.drop(1).dropLast(1))
        }
    }

    private fun validScript(script: String): Boolean =
        script.endsWith(".luac") && script.split('/').all(ASSET_SEGMENT::matches)

    private companion object {
        val LITERAL = Regex("[A-Za-z][A-Za-z0-9._-]*")
        val PLACEHOLDER = Regex("\\{[A-Za-z][A-Za-z0-9_]*\\}")
        val ASSET_SEGMENT = Regex("[A-Za-z0-9][A-Za-z0-9._-]*")
    }
}
