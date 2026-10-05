package com.example.luaplayground

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavController

@Composable
fun NavigatorListener(navigator: Navigator, navController: NavController) {
    LaunchedEffect(navigator, navController) {
        navigator.events.collect { event ->
            when (event) {
                is NavigationEvent.Navigate -> navController.navigate(event.route)
                NavigationEvent.PopBackStack -> navController.popBackStack()
            }
        }
    }
}
