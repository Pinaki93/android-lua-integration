package com.example.luacompose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun LuaUi(
    result: LuaUiResult,
    onAction: (String) -> Unit,
    modifier: Modifier = Modifier,
    onInput: (UiInput) -> Unit = {},
) {
    when (result) {
        is LuaUiResult.Success -> LuaNode(result.root, onAction, onInput, modifier)
        is LuaUiResult.Failure -> Text(result.displayText(), modifier)
    }
}

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
            node.children.forEach { LuaNode(it, onAction, onInput) }
        }
        is UiNode.Row -> Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(node.gap.toDp()),
        ) {
            node.children.forEach { LuaNode(it, onAction, onInput) }
        }
        is UiNode.Text -> Text(
            text = node.text,
            modifier = modifier,
            style = node.style.textStyle(MaterialTheme.typography),
        )
        is UiNode.Card -> Card(modifier) {
            Column(Modifier.padding(16.dp)) {
                node.children.forEach { LuaNode(it, onAction, onInput) }
            }
        }
        is UiNode.Button -> Button(
            onClick = action(node.action, onAction, node.enabled),
            modifier = modifier,
            enabled = node.enabled,
        ) {
            Text(node.text)
        }
        is UiNode.TextField -> OutlinedTextField(
            value = node.value,
            onValueChange = textInput(node.action, node.enabled, onInput),
            modifier = modifier,
            enabled = node.enabled,
            label = { Text(node.label) },
            singleLine = true,
            isError = node.error != null,
            supportingText = node.error?.let { error -> ({ Text(error) }) },
        )
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
    }
}

internal fun Int.toDp(): Dp = dp

internal fun UiTextStyle.textStyle(typography: Typography): TextStyle = when (this) {
    UiTextStyle.Body -> typography.bodyLarge
    UiTextStyle.Title -> typography.titleLarge
    UiTextStyle.Metric -> typography.headlineMedium
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
