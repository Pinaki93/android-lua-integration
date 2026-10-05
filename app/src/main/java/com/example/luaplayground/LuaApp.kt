package com.example.luaplayground

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.luaplayground.feature.console.ConsoleFeature
import com.example.luaplayground.feature.playground.PlaygroundFeature

@Composable
fun LuaApp(container: AppContainer) {
    val navController = rememberNavController()
    NavigatorListener(container.navigator, navController)

    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            NavHost(
                navController = navController,
                startDestination = PLAYGROUND_ROUTE
            ) {
                composable(PLAYGROUND_ROUTE) {
                    PlaygroundFeature(container.playground())
                }
                composable(CONSOLE_ROUTE) {
                    ConsoleFeature(container.console())
                }
            }
        }
    }
}
