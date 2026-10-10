package com.example.luaplayground.feature.dashboard

import com.example.luacompose.LuaUiEngine
import com.example.luacompose.LuaUiResult
import com.example.luacompose.UiNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DashboardTest {
    @Test fun `initial state evaluates the bundled dashboard`() {
        val viewModel = viewModel()

        assertEquals(DashboardState(), viewModel.state)
        assertTrue(viewModel.result is LuaUiResult.Success)
        assertTrue(viewModel.result.texts().containsAll(listOf("1,284", "$42,680", "Updated at 09:45")))
    }

    @Test fun `refresh action reduces immutable app state`() {
        val initial = DashboardState()

        assertEquals(
            initial.copy(
                activeUsers = 1_291,
                activityNote = "Live data refreshed",
                refreshedAt = "Updated just now",
            ),
            reduceDashboard(initial, "refresh"),
        )
        assertSame(initial, reduceDashboard(initial, "unknown"))
    }

    @Test fun `refresh re-evaluates the same asset with new state`() {
        val viewModel = viewModel()
        val initialResult = viewModel.result

        viewModel.dispatch("refresh")

        assertNotEquals(initialResult, viewModel.result)
        assertTrue(viewModel.result.texts().containsAll(listOf("1,291", "Live data refreshed", "Updated just now")))
    }

    private fun viewModel() = DashboardVm(
        File("build/generated/luaAssets/dashboard.luac").readBytes(),
        LuaUiEngine(),
    )

    private fun LuaUiResult.texts(): List<String> = when (this) {
        is LuaUiResult.Failure -> emptyList()
        is LuaUiResult.Success -> root.texts()
    }

    private fun UiNode.texts(): List<String> = when (this) {
        is UiNode.Button -> listOf(text)
        is UiNode.Card -> children.flatMap { it.texts() }
        is UiNode.Column -> children.flatMap { it.texts() }
        is UiNode.Row -> children.flatMap { it.texts() }
        is UiNode.Text -> listOf(text)
        is UiNode.TextField -> listOf(label, value) + listOfNotNull(error)
        is UiNode.Checkbox -> listOf(label)
        is UiNode.IconButton -> listOf(label)
        is UiNode.ListItem -> children.flatMap { it.texts() }
    }
}
