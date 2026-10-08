package com.example.luaplayground

import android.content.res.AssetManager as AndroidAssetManager

class AssetManager(
    private val readAppFile: () -> ByteArray,
    private val readDashboardFile: () -> ByteArray,
    private val readPageFile: (String) -> ByteArray,
) {
    constructor(assets: AndroidAssetManager) : this(
        readAppFile = { assets.open(APP).use { it.readBytes() } },
        readDashboardFile = { assets.open(DASHBOARD).use { it.readBytes() } },
        readPageFile = { name -> assets.open(name).use { it.readBytes() } },
    )

    fun readApp(): ByteArray = readAppFile()

    fun readDashboard(): ByteArray = readDashboardFile()

    fun readPage(name: String): ByteArray = readPageFile(name)

    private companion object {
        private const val APP = "app.luac"
        private const val DASHBOARD = "dashboard.luac"
    }
}
