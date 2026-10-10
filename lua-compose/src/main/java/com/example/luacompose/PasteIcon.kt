package com.example.luacompose

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

internal val PasteIcon = ImageVector.Builder(
    name = "Paste", defaultWidth = 24.dp, defaultHeight = 24.dp,
    viewportWidth = 24f, viewportHeight = 24f,
).apply {
    path(fill = SolidColor(Color.Black)) {
        moveTo(16f, 4f); lineTo(20f, 4f); lineTo(20f, 22f); lineTo(4f, 22f)
        lineTo(4f, 4f); lineTo(8f, 4f); lineTo(8f, 6f); lineTo(6f, 6f)
        lineTo(6f, 20f); lineTo(18f, 20f); lineTo(18f, 6f); lineTo(16f, 6f); close()
        moveTo(9f, 2f); lineTo(15f, 2f); lineTo(15f, 7f); lineTo(9f, 7f); close()
    }
}.build()
