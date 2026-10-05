package com.example.luaplayground

import android.content.res.AssetManager as AndroidAssetManager

class AssetManager(
    private val listFiles: () -> Array<String>?,
    private val readFile: (String) -> String,
) {
    constructor(assets: AndroidAssetManager) : this(
        listFiles = { assets.list(DIRECTORY) },
        readFile = { name -> assets.open("$DIRECTORY/$name").bufferedReader().use { it.readText() } },
    )

    fun names(): List<String> = listFiles().orEmpty().filter { it.endsWith(EXTENSION) }.sorted()

    fun read(name: String): String = readFile(name)

    private companion object {
        private const val DIRECTORY = "lua"
        private const val EXTENSION = ".lua"
    }
}
