package com.example.luaplayground

import org.junit.Assert.assertEquals
import org.junit.Test

class AssetManagerTest {
    private val assets = AssetManager(
        readAppFile = { "app".encodeToByteArray() },
        readDashboardFile = { "dashboard".encodeToByteArray() },
        readPageFile = { "page:$it".encodeToByteArray() },
    )

    @Test fun `reads app startup asset`() {
        assertEquals("app", assets.readApp().decodeToString())
    }

    @Test fun `reads dashboard asset`() {
        assertEquals("dashboard", assets.readDashboard().decodeToString())
    }

    @Test fun `reads nested page asset`() {
        assertEquals("page:features/todo.luac", assets.readPage("features/todo.luac").decodeToString())
    }
}
