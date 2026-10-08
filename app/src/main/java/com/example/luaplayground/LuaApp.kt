package com.example.luaplayground

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.luaplayground.feature.dashboard.DashboardFeature
import com.example.luaplayground.feature.dynamic.LuaFeature
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun LuaApp(container: AppContainer) {
    val navController = rememberNavController()
    NavigatorListener(container.navigator, navController)

    val routes by container.routeRegistry.state.collectAsState()
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors) {
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

private val LightColors = lightColorScheme(
    primary = Color(0xFF1E3A5F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCEAF7),
    onPrimaryContainer = Color(0xFF102A43),
    secondary = Color(0xFF397B75),
    tertiary = Color(0xFF9A3412),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE4CC),
    onTertiaryContainer = Color(0xFF431407),
    background = Color(0xFFF5F7FA),
    onBackground = Color(0xFF17202A),
    surface = Color.White,
    onSurface = Color(0xFF17202A),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF8FAFC),
    surfaceContainer = Color(0xFFF0F3F7),
    surfaceContainerHigh = Color(0xFFE8EDF3),
    surfaceContainerHighest = Color(0xFFE1E7EF),
    outline = Color(0xFF7B8794),
    outlineVariant = Color(0xFFD5DCE5),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA9C7E8),
    onPrimary = Color(0xFF0F2942),
    primaryContainer = Color(0xFF294A6D),
    onPrimaryContainer = Color(0xFFDCEAF7),
    secondary = Color(0xFF9CCFC9),
    tertiary = Color(0xFFFFB68A),
    onTertiary = Color(0xFF552007),
    tertiaryContainer = Color(0xFF74300F),
    onTertiaryContainer = Color(0xFFFFDBC7),
    background = Color(0xFF101419),
    onBackground = Color(0xFFE6EAF0),
    surface = Color(0xFF151A20),
    onSurface = Color(0xFFE6EAF0),
    surfaceContainerLowest = Color(0xFF0B0F13),
    surfaceContainerLow = Color(0xFF151A20),
    surfaceContainer = Color(0xFF1A2027),
    surfaceContainerHigh = Color(0xFF222933),
    surfaceContainerHighest = Color(0xFF2A323D),
    outline = Color(0xFF8E99A7),
    outlineVariant = Color(0xFF3B4654),
)

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
