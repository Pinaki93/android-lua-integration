package com.example.luaplayground

import android.content.res.AssetManager as AndroidAssetManager
import androidx.compose.runtime.Immutable
import com.example.luacompose.JsonStore
import com.example.luacompose.LuaUiEngine
import com.example.luaplayground.feature.dashboard.DashboardContainer
import com.example.luaplayground.feature.dynamic.LuaContainer

@Immutable
class AppContainer(
    val assetManager: AssetManager,
    val luaEngine: LuaEngine = LuaEngine(),
    val navigator: Navigator = AppNavigator,
    val luaUiEngine: LuaUiEngine = LuaUiEngine(),
    private val storageFactory: (String) -> JsonStore = { JsonStore(read = { null }, write = {}, delete = {}) },
    val routeRegistry: LuaRouteRegistry = LuaRouteRegistry(),
    private val httpClient: io.ktor.client.HttpClient? = null,
    private val reading: com.example.luacompose.ReadingCapabilities? = null,
) {
    private val dashboard = DashboardContainer(assetManager, luaUiEngine, navigator)
    private var pages = emptyMap<String, LuaContainer>()

    constructor(assets: AndroidAssetManager) : this(AssetManager(assets))

    fun start(runInBackground: (() -> Unit) -> Unit = { Thread(it, "lua-app").start() }) {
        runInBackground {
            val routes = mutableListOf<LuaRoute>()
            var startRoute: String? = null
            var startRouteCalls = 0
            val result = runCatching {
                luaEngine.executeApp(
                    assetManager.readApp(),
                    registerRoute = { route, script -> routes += LuaRoute(route, script) },
                    setStartRoute = { startRoute = it; startRouteCalls++ },
                )
            }.getOrElse {
                routeRegistry.fail()
                return@runInBackground
            }
            if (!result.succeeded || startRouteCalls != 1 || !routeRegistry.accepts(startRoute, routes)) {
                routeRegistry.fail()
                return@runInBackground
            }
            val loaded = runCatching {
                routes.associate { route ->
                    val script = assetManager.readPage(route.script)
                    require(script.size <= LuaUiEngine.MAX_SCRIPT_BYTES)
                    route.pattern to LuaContainer({ script }, navigator, ::storage, http = httpForScript(route.script, httpClient),
                        reading = reading.takeIf { route.script == "reading-list.luac" })
                }
            }.getOrElse {
                routeRegistry.fail()
                return@runInBackground
            }
            pages = loaded
            routeRegistry.complete(startRoute!!, routes)
        }
    }

    fun dashboard() = dashboard

    fun page(pattern: String) = pages.getValue(pattern)

    private fun storage(name: String): JsonStore {
        require(validStorageName(name))
        return storageFactory(name)
    }
}

internal fun validStorageName(name: String) = Regex("[A-Za-z0-9][A-Za-z0-9._-]*\\.json").matches(name)
