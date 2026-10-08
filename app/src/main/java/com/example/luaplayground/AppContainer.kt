package com.example.luaplayground

import android.content.res.AssetManager as AndroidAssetManager
import androidx.compose.runtime.Immutable
import com.example.luacompose.JsonStore
import com.example.luacompose.LuaUiEngine
import com.example.luaplayground.feature.console.ConsoleContainer
import com.example.luaplayground.feature.dashboard.DashboardContainer
import com.example.luaplayground.feature.lua.LuaContainer
import com.example.luaplayground.feature.playground.PlaygroundContainer

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
    private val todo = LuaContainer(assetManager::readTodo, navigator, todoStore)

    constructor(assets: AndroidAssetManager) : this(AssetManager(assets))

    fun start(runInBackground: (() -> Unit) -> Unit = { Thread(it, "lua-app").start() }) {
        runInBackground {
            luaEngine.execute(assetManager.readApp(), name = "app.luac")
            todo.warmUp()
        }
    }

    fun console() = console

    fun dashboard() = dashboard

    fun playground() = playground

    fun todo() = todo
}
