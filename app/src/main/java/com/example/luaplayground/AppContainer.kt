package com.example.luaplayground

import android.content.res.AssetManager as AndroidAssetManager
import androidx.compose.runtime.Immutable
import com.example.luaplayground.feature.console.ConsoleContainer
import com.example.luaplayground.feature.playground.PlaygroundContainer

@Immutable
class AppContainer(
    val assetManager: AssetManager,
    val luaEngine: LuaEngine = LuaEngine(),
    val navigator: Navigator = AppNavigator,
) {
    private val console = ConsoleContainer(assetManager, luaEngine, navigator)
    private val playground = PlaygroundContainer(navigator)

    constructor(assets: AndroidAssetManager) : this(AssetManager(assets))

    fun console() = console

    fun playground() = playground
}
