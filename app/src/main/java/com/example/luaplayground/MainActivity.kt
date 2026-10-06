package com.example.luaplayground

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.luacompose.persistentJsonStore
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = AppContainer(
            assetManager = AssetManager(assets),
            todoStore = persistentJsonStore(File(applicationContext.filesDir, "todos.json")),
        )
        setContent { LuaApp(container) }
    }
}
