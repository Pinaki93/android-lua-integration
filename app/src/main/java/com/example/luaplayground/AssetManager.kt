package com.example.luaplayground

import android.content.res.AssetManager as AndroidAssetManager

class AssetManager(
    private val listFiles: () -> Array<String>?,
    private val readFile: (String) -> ByteArray,
    private val readAppFile: () -> ByteArray = { readFile(APP) },
    private val readDashboardFile: () -> ByteArray = { readFile(DASHBOARD) },
    private val readPageFile: (String) -> ByteArray = readFile,
) {
    constructor(assets: AndroidAssetManager) : this(
        listFiles = { assets.list(DIRECTORY) },
        readFile = { name -> assets.open("$DIRECTORY/$name").use { it.readBytes() } },
        readAppFile = { assets.open(APP).use { it.readBytes() } },
        readDashboardFile = { assets.open(DASHBOARD).use { it.readBytes() } },
        readPageFile = { name -> assets.open(name).use { it.readBytes() } },
    )

    fun names(): List<String> = listFiles().orEmpty().filter { it.endsWith(EXTENSION) }.sorted()

    fun read(name: String): ByteArray = readFile(name)

    fun readApp(): ByteArray = readAppFile()

    fun readDashboard(): ByteArray = readDashboardFile()

    fun readPage(name: String): ByteArray = readPageFile(name)

    private companion object {
        private const val DIRECTORY = "lua"
        private const val APP = "app.luac"
        private const val DASHBOARD = "dashboard.luac"
        private const val EXTENSION = ".luac"
    }
}
