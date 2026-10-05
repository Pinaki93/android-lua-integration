package com.example.luaplayground.feature.console

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

@Composable
fun ConsoleFeature(container: ConsoleContainer) {
    val viewModel = viewModel { container.createVm() }
    val scope = rememberCoroutineScope()
    ConsoleScreen(
        state = viewModel.state,
        onBack = { scope.launch { container.navigator.popBackStack() } },
        onInputChange = viewModel::updateInput,
        onScriptSelect = viewModel::selectScript,
        onRun = viewModel::runScript,
    )
}

@Composable
fun ConsoleScreen(
    state: LuaUiState,
    onBack: () -> Unit,
    onInputChange: (String) -> Unit,
    onScriptSelect: (String) -> Unit,
    onRun: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        OutlinedButton(onClick = onBack) { Text("Back to playground") }
        Text("Console input and output", style = MaterialTheme.typography.headlineLarge)
        Text("Bundled Lua program", style = MaterialTheme.typography.titleMedium)
        Box {
            OutlinedButton(
                onClick = { menuOpen = true },
                enabled = state.scripts.isNotEmpty() && !state.running,
            ) {
                Text(state.selectedScript.ifEmpty { "No scripts found" })
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                state.scripts.forEach { name ->
                    DropdownMenuItem(
                        text = { Text(name) },
                        onClick = {
                            onScriptSelect(name)
                            menuOpen = false
                        },
                    )
                }
            }
        }
        Text("Script", style = MaterialTheme.typography.titleMedium)
        Surface(tonalElevation = 2.dp, shape = MaterialTheme.shapes.medium) {
            SelectionContainer {
                Text(
                    state.source,
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(16.dp),
                    fontFamily = FontFamily.Monospace,
                    maxLines = 12,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        OutlinedTextField(
            value = state.input,
            onValueChange = onInputChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Input — one value per line") },
            minLines = 4,
            maxLines = 8,
        )
        Button(
            enabled = state.selectedScript.isNotEmpty() && !state.running,
            onClick = onRun,
        ) {
            Text(if (state.running) "Running…" else "Run script")
        }
        Text("Output", style = MaterialTheme.typography.titleMedium)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 2.dp,
            shape = MaterialTheme.shapes.medium,
        ) {
            SelectionContainer {
                Text(
                    state.output.ifEmpty { "Output will appear here." },
                    Modifier.padding(16.dp),
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
