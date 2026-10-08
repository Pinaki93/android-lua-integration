package com.example.luaplayground

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.luaplayground.feature.console.ConsoleFeature
import com.example.luaplayground.feature.dashboard.DashboardFeature
import com.example.luaplayground.feature.dynamic.LuaFeature
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun LuaApp(container: AppContainer) {
    val navController = rememberNavController()
    NavigatorListener(container.navigator, navController)

    val routes by container.routeRegistry.state.collectAsState()
    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            when (val state = routes) {
                LuaRoutesState.Loading -> {
                    LoadingUi(state)
                }

                LuaRoutesState.Failed -> Centered { Text("Could not start app.") }
                is LuaRoutesState.Ready -> DynamicNavHost(
                    navController = navController,
                    startRoute = state.startRoute,
                    routes = state.routes,
                    container = container
                )
            }
        }
    }
}

@Composable
private fun LoadingUi(state: LuaRoutesState) {
    // allow a grace duration of 500ms before showing loader
    var stillLoading by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(500.milliseconds)
        if (state is LuaRoutesState.Loading) {
            stillLoading = true
        }
    }
    if (stillLoading) {
        Centered { CircularProgressIndicator() }
    }
}

@Composable
private fun DynamicNavHost(
    navController: NavHostController,
    startRoute: String,
    routes: List<LuaRoute>,
    container: AppContainer
) {
    NavHost(
        navController = navController,
        startDestination = startRoute,
    ) {
        composable(CONSOLE_ROUTE) {
            ConsoleFeature(container.console())
        }
        composable(DASHBOARD_ROUTE) {
            DashboardFeature(container.dashboard())
        }
        routes.forEach { route ->
            composable(route.pattern) { entry ->
                val arguments = route.arguments.mapNotNull { name ->
                    entry.arguments?.getString(name)?.let { name to it }
                }.toMap()
                LuaFeature(
                    container.page(route.pattern),
                    arguments,
                    showBack = route.pattern != startRoute,
                )
            }
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}
