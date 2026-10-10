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
        val readingLog: (String) -> Unit = { android.util.Log.d("ReadingAutoFill", it); Unit }
        container = AppContainer(
            assetManager = AssetManager(assets),
            httpClient = httpClient,
            reading = com.example.luacompose.ReadingCapabilities(
                log = readingLog,
                clipboardText = {
                    val clipboard = getSystemService(android.content.ClipboardManager::class.java)
                    clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
                },
                fetchTitle = com.example.luacompose.ReadingTitleClient(log = readingLog)::fetch,
                openOriginal = { url ->
                    startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                },
            ),
            storageFactory = { name -> persistentJsonStore(File(filesDir, name)) },
        )
        container.start()
    }

    companion object {
        fun getInstance(application: Application) = application as LuaApplication
    }
}
