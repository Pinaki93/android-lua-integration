package com.example.luaplayground

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Test

class StatusBarThemeTest {
    @Test fun `application uses the status bar theme`() {
        val manifest = readXml("src/main/AndroidManifest.xml")
        val application = manifest.getElementsByTagName("application").item(0)
        assertEquals("@style/AppTheme", application.attributes.getNamedItem("android:theme").nodeValue)
    }

    @Test fun `light mode uses dark icons on a light status bar`() {
        val items = themeItems("values")
        assertEquals("true", items["android:windowLightStatusBar"])
        assertEquals("@android:color/white", items["android:statusBarColor"])
    }

    @Test fun `dark mode uses white icons on a dark status bar`() {
        val items = themeItems("values-night")
        assertEquals("false", items["android:windowLightStatusBar"])
        assertEquals("#151A20", items["android:statusBarColor"])
    }

    private fun themeItems(directory: String): Map<String, String> {
        val theme = readXml("src/main/res/$directory/themes.xml")
        val style = theme.getElementsByTagName("style").item(0)
        assertEquals("AppTheme", style.attributes.getNamedItem("name").nodeValue)
        val items = theme.getElementsByTagName("item")
        return (0 until items.length).associate { index ->
            val item = items.item(index)
            item.attributes.getNamedItem("name").nodeValue to item.textContent.trim()
        }
    }

    private fun readXml(path: String) = File(path).inputStream().use {
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(it)
    }
}
