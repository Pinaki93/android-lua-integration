package com.example.luaplayground

import android.content.res.AssetManager as AndroidAssetManager
import androidx.compose.runtime.Immutable
import com.example.luacompose.JsonStore
import com.example.luacompose.LuaUiEngine
import com.example.luaplayground.feature.console.ConsoleContainer
import com.example.luaplayground.feature.dashboard.DashboardContainer
import com.example.luaplayground.feature.playground.PlaygroundContainer
import com.example.luaplayground.feature.todo.TodoContainer

@Immutable
class AppContainer(
    val assetManager: AssetManager,
    val luaEngine: LuaEngine = LuaEngine(),
    val navigator: Navigator = AppNavigator,
    val luaUiEngine: LuaUiEngine = LuaUiEngine(),
    val todoStore: JsonStore = JsonStore(read = { null }, write = {}, delete = {}),
) {
    private val console = ConsoleContainer(assetManager, luaEngine, navigator)
    private val dashboard = DashboardContainer(assetManager, luaUiEngine, navigator)
    private val playground = PlaygroundContainer(navigator)
    private val todo = TodoContainer(assetManager, navigator, todoStore)

    constructor(assets: AndroidAssetManager) : this(AssetManager(assets))

    fun console() = console

    fun dashboard() = dashboard

    fun playground() = playground

    fun todo() = todo
}
