package com.example.luaplayground.feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.luacompose.LuaUi
import com.example.luacompose.LuaUiEngine
import com.example.luacompose.LuaUiResult
import com.example.luaplayground.AssetManager
import com.example.luaplayground.Navigator
import java.util.Locale
import kotlinx.coroutines.launch

@Immutable
data class DashboardState(
    val activeUsers: Int = 1_284,
    val revenue: String = "$42,680",
    val reliability: String = "99.94%",
    val activityNote: String = "+8.2% from last week",
    val refreshedAt: String = "Updated at 09:45",
)

fun reduceDashboard(state: DashboardState, action: String): DashboardState = when (action) {
    "refresh" -> state.copy(
        activeUsers = state.activeUsers + 7,
        activityNote = "Live data refreshed",
        refreshedAt = "Updated just now",
    )
    else -> state
}

@Immutable
class DashboardContainer(
    private val assets: AssetManager,
    val engine: LuaUiEngine,
    val navigator: Navigator,
) {
    fun createVm() = DashboardVm(assets.readDashboard(), engine)
}

class DashboardVm(
    private val script: String,
    private val engine: LuaUiEngine,
) : ViewModel() {
    var state by mutableStateOf(DashboardState())
        private set

    var result by mutableStateOf(evaluate())
        private set

    fun dispatch(action: String) {
        val next = reduceDashboard(state, action)
        if (next == state) return
        state = next
        result = evaluate()
    }

    private fun evaluate(): LuaUiResult = engine.evaluate(
        script,
        mapOf(
            "activeUsers" to String.format(Locale.US, "%,d", state.activeUsers),
            "revenue" to state.revenue,
            "reliability" to state.reliability,
            "activityNote" to state.activityNote,
            "refreshedAt" to state.refreshedAt,
        ),
    )
}

@Composable
fun DashboardFeature(container: DashboardContainer) {
    val viewModel = viewModel { container.createVm() }
    val scope = rememberCoroutineScope()
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        OutlinedButton(onClick = { scope.launch { container.navigator.popBackStack() } }) {
            Text("Back to playground")
        }
        LuaUi(viewModel.result, viewModel::dispatch, Modifier.fillMaxWidth())
    }
}
