package com.example.luaplayground.feature.playground

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun PlaygroundFeature(container: PlaygroundContainer) {
    val viewModel = viewModel { container.createVm() }
    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Lua Playground", style = MaterialTheme.typography.headlineLarge)
        Text("Small, explicit Lua capabilities you can try on this device.")
        Button(onClick = viewModel::openDashboard) {
            Text("Lua Compose dashboard")
        }
        Button(onClick = viewModel::openConsole) {
            Text("Console input and output")
        }
        Button(onClick = viewModel::openTodo) {
            Text("Persistent todo form")
        }
    }
}
