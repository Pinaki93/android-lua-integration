package com.example.luaplayground

import android.app.Application
import com.example.luacompose.persistentJsonStore
import java.io.File

class LuaApplication : Application() {
    lateinit var container: AppContainer
        private set

    private val httpClient by lazy {
        io.ktor.client.HttpClient(io.ktor.client.engine.android.Android) {
            followRedirects = false
            expectSuccess = false
            engine { connectTimeout = 30_000; socketTimeout = 30_000 }
        }
    }

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(
            assetManager = AssetManager(assets),
            httpClient = httpClient,
            storageFactory = { name -> persistentJsonStore(File(filesDir, name)) },
        )
        container.start()
    }

    companion object {
        fun getInstance(application: Application) = application as LuaApplication
    }
}
