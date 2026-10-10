package com.example.luaplayground.feature.dynamic

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.luacompose.LuaUi
import kotlinx.coroutines.launch

@Composable
fun LuaFeature(
    container: LuaContainer,
    arguments: Map<String, String>,
    showBack: Boolean,
) {
    val viewModel = viewModel { container.createVm(arguments) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    androidx.compose.runtime.DisposableEffect(lifecycle, viewModel) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) viewModel.resume()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val scope = rememberCoroutineScope()
    val readingBackAction = viewModel.readingBackAction
    BackHandler(enabled = showBack || readingBackAction != null) {
        if (readingBackAction != null) viewModel.action(readingBackAction)
        else scope.launch { container.navigator.popBackStack() }
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier
            .fillMaxWidth()
            .weight(1f), contentAlignment = Alignment.Center) {
            if (viewModel.isLoading) CircularProgressIndicator()
            else LuaUi(
                viewModel.result,
                viewModel::action,
                Modifier.fillMaxSize(),
                viewModel::input,
                lazy = true
            )
        }
    }
}
