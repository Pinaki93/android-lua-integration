package com.example.luaplayground

import android.app.Application
import com.example.luacompose.persistentJsonStore
import java.io.File

class LuaApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(
            assetManager = AssetManager(assets),
            storageFactory = { name -> persistentJsonStore(File(filesDir, name)) },
        )
        container.start()
    }

    companion object {
        fun getInstance(application: Application) = application as LuaApplication
    }
}
