package com.example.luaplayground

import android.content.res.AssetManager as AndroidAssetManager

class AssetManager(
    private val listFiles: () -> Array<String>?,
    private val readFile: (String) -> String,
    private val readDashboardFile: () -> String = { readFile(DASHBOARD) },
    private val readTodoFile: () -> String = { readFile(TODO) },
) {
    constructor(assets: AndroidAssetManager) : this(
        listFiles = { assets.list(DIRECTORY) },
        readFile = { name -> assets.open("$DIRECTORY/$name").bufferedReader().use { it.readText() } },
        readDashboardFile = { assets.open(DASHBOARD).bufferedReader().use { it.readText() } },
        readTodoFile = { assets.open(TODO).bufferedReader().use { it.readText() } },
    )

    fun names(): List<String> = listFiles().orEmpty().filter { it.endsWith(EXTENSION) }.sorted()

    fun read(name: String): String = readFile(name)

    fun readDashboard(): String = readDashboardFile()

    fun readTodo(): String = readTodoFile()

    private companion object {
        private const val DIRECTORY = "lua"
        private const val DASHBOARD = "dashboard.lua"
        private const val TODO = "todo.lua"
        private const val EXTENSION = ".lua"
    }
}
