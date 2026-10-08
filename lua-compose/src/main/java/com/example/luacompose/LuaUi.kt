package com.example.luacompose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.graphics.Color

@Composable
fun LuaUi(
    result: LuaUiResult,
    onAction: (String) -> Unit,
    modifier: Modifier = Modifier,
    onInput: (UiInput) -> Unit = {},
    lazy: Boolean = false,
) {
    when (result) {
        is LuaUiResult.Success -> {
            val root = lazyRoot(result.root, lazy)
            if (root != null) {
                LazyColumn(
                    modifier = modifier,
                    verticalArrangement = Arrangement.spacedBy(root.gap.toDp()),
                ) {
                    itemsIndexed(
                        items = root.children,
                        key = { index, child -> lazyItemKey(index, child) },
                        contentType = { _, child -> child::class },
                    ) { index, child ->
                        LuaNode(child, onAction, onInput, Modifier.fillMaxWidth())
                        if (hasListDivider(root.children, index)) HorizontalDivider()
                    }
                }
            } else {
                LuaNode(result.root, onAction, onInput, modifier)
            }
        }
        is LuaUiResult.Failure -> Text(result.displayText(), modifier)
    }
}

internal fun lazyRoot(node: UiNode, enabled: Boolean): UiNode.Column? =
    (node as? UiNode.Column).takeIf { enabled }

internal fun lazyItemKey(index: Int, node: UiNode): Any =
    if (node is UiNode.ListItem) "list:${node.toggleAction}" else index

@Composable
private fun LuaNode(
    node: UiNode,
    onAction: (String) -> Unit,
    onInput: (UiInput) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (node) {
        is UiNode.Column -> Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(node.gap.toDp()),
        ) {
            node.children.forEachIndexed { index, child ->
                LuaNode(child, onAction, onInput)
                if (hasListDivider(node.children, index)) HorizontalDivider()
            }
        }
        is UiNode.Row -> Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(node.gap.toDp()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            node.children.forEach { child ->
                LuaNode(child, onAction, onInput, if (child is UiNode.Checkbox) Modifier.weight(1f) else Modifier)
            }
        }
        is UiNode.Text -> Text(
            text = node.text,
            modifier = modifier,
            style = node.style.textStyle(MaterialTheme.typography),
        )
        is UiNode.Card -> Card(
            modifier = modifier,
            shape = RoundedCornerShape(if (node.style == UiCardStyle.Accent) 24.dp else 16.dp),
            colors = CardDefaults.cardColors(containerColor = node.style.containerColor(MaterialTheme.colorScheme)),
            elevation = CardDefaults.cardElevation(defaultElevation = if (node.style == UiCardStyle.Outlined) 1.dp else 0.dp),
            border = if (node.style == UiCardStyle.Outlined) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
        ) {
            Column(Modifier.padding(if (node.style == UiCardStyle.Accent) 24.dp else 12.dp)) {
                node.children.forEach { LuaNode(it, onAction, onInput) }
            }
        }
        is UiNode.Button -> {
            if (node.style == UiButtonStyle.Quiet) {
                TextButton(
                    onClick = action(node.action, onAction, node.enabled),
                    modifier = modifier,
                    enabled = node.enabled,
                    content = { Text(node.text) },
                )
            } else {
                Button(
                    onClick = action(node.action, onAction, node.enabled),
                    modifier = modifier,
                    enabled = node.enabled,
                    colors = ButtonDefaults.buttonColors(containerColor = node.style.containerColor(MaterialTheme.colorScheme)),
                    content = { Text(node.text) },
                )
            }
        }
        is UiNode.TextField -> {
            if (node.style == UiTextFieldStyle.Plain) {
                TextField(
                    value = node.value,
                    onValueChange = textInput(node.action, node.enabled, onInput),
                    modifier = modifier,
                    enabled = node.enabled,
                    label = { Text(node.label) },
                    singleLine = true,
                    isError = node.error != null,
                    supportingText = node.error?.let { error -> ({ Text(error) }) },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                    ),
                )
            } else {
                OutlinedTextField(
                    value = node.value,
                    onValueChange = textInput(node.action, node.enabled, onInput),
                    modifier = modifier,
                    enabled = node.enabled,
                    label = { Text(node.label) },
                    singleLine = true,
                    isError = node.error != null,
                    supportingText = node.error?.let { error -> ({ Text(error) }) },
                )
            }
        }
        is UiNode.Checkbox -> Row(
            modifier = modifier.minimumInteractiveComponentSize().fillMaxWidth().toggleable(
                value = node.checked,
                enabled = node.enabled,
                role = Role.Checkbox,
                onValueChange = checkedInput(node.action, node.enabled, onInput),
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(node.label, Modifier.weight(1f))
            Checkbox(checked = node.checked, onCheckedChange = null, enabled = node.enabled)
        }
        is UiNode.ListItem -> Row(
            modifier = modifier.padding(horizontal = LIST_ITEM_HORIZONTAL_PADDING.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = node.checked,
                onCheckedChange = checkedInput(node.toggleAction, node.enabled, onInput),
                enabled = node.enabled,
                colors = CheckboxDefaults.colors(checkedColor = checkboxColor(MaterialTheme.colorScheme)),
            )
            Text(
                node.text,
                Modifier.weight(1f),
                textDecoration = TextDecoration.LineThrough.takeIf { node.checked },
            )
            IconButton(onClick = action(node.deleteAction, onAction, node.enabled), enabled = node.enabled) {
                DeleteIcon()
            }
        }
    }
}

internal fun Int.toDp(): Dp = dp

internal fun UiTextStyle.textStyle(typography: Typography): TextStyle = when (this) {
    UiTextStyle.Body -> typography.bodyLarge
    UiTextStyle.Title -> typography.titleLarge
    UiTextStyle.Metric -> typography.headlineMedium
    UiTextStyle.Label -> typography.labelMedium
}

internal fun UiCardStyle.containerColor(colors: ColorScheme) = when (this) {
    UiCardStyle.Default -> colors.surfaceContainerHighest
    UiCardStyle.Accent -> colors.primary
    UiCardStyle.Subtle -> colors.surfaceContainerLow
    UiCardStyle.Outlined -> colors.surface
    UiCardStyle.Orange -> colors.tertiary
    UiCardStyle.LightOrange -> colors.tertiaryContainer
}

internal fun UiButtonStyle.containerColor(colors: ColorScheme) = when (this) {
    UiButtonStyle.Primary -> colors.primary
    UiButtonStyle.Orange -> colors.tertiary
    UiButtonStyle.Quiet -> Color.Transparent
}

internal fun checkboxColor(colors: ColorScheme) = colors.tertiary

internal const val LIST_ITEM_HORIZONTAL_PADDING = 4

internal fun hasListDivider(children: List<UiNode>, index: Int) =
    children.getOrNull(index) is UiNode.ListItem && children.getOrNull(index + 1) is UiNode.ListItem

@Composable
private fun DeleteIcon() {
    val color = LocalContentColor.current
    Canvas(Modifier.size(20.dp).semantics { contentDescription = "Delete task" }) {
        val stroke = 1.8.dp.toPx()
        drawLine(color, Offset(size.width * .2f, size.height * .25f), Offset(size.width * .8f, size.height * .25f), stroke, StrokeCap.Round)
        drawLine(color, Offset(size.width * .4f, size.height * .15f), Offset(size.width * .6f, size.height * .15f), stroke, StrokeCap.Round)
        drawRect(
            color = color,
            topLeft = Offset(size.width * .3f, size.height * .35f),
            size = Size(size.width * .4f, size.height * .5f),
            style = Stroke(stroke, cap = StrokeCap.Round),
        )
    }
}

internal fun action(name: String, onAction: (String) -> Unit, enabled: Boolean = true): () -> Unit = {
    if (enabled) onAction(name)
}

internal fun textInput(action: String, enabled: Boolean, onInput: (UiInput) -> Unit): (String) -> Unit = { value ->
    if (enabled) onInput(UiInput.TextChanged(action, value))
}

internal fun checkedInput(action: String, enabled: Boolean, onInput: (UiInput) -> Unit): (Boolean) -> Unit = { checked ->
    if (enabled) onInput(UiInput.CheckedChanged(action, checked))
}

internal fun LuaUiResult.Failure.displayText(): String = "Unable to render UI."
