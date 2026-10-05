package com.example.luaplayground.feature.console

import androidx.compose.runtime.Immutable
import com.example.luaplayground.AssetManager
import com.example.luaplayground.LuaEngine
import com.example.luaplayground.Navigator
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

@Immutable
class ConsoleContainer(
    val assetManager: AssetManager,
    val luaEngine: LuaEngine,
    val navigator: Navigator,
) {
    fun createVm(worker: CoroutineDispatcher = Dispatchers.Default) =
        ConsoleIoVm(assetManager, luaEngine, worker)
}
