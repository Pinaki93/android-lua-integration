package com.example.luacompose

import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.semantics.selected
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import coil3.compose.AsyncImage
import androidx.compose.foundation.layout.size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

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
            if (result.root is UiNode.Scaffold) {
                LuaScaffold(result.root, onAction, onInput, modifier, lazy)
                return
            }
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
                        if (hasListDivider(root.children, index, LocalReadingStyle.current)) HorizontalDivider()
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
    if (node is UiNode.ListItem) "list:${node.key}" else index

internal val LocalReadingStyle = compositionLocalOf { false }

internal fun defaultImageResource(reading: Boolean): Int =
    if (reading) R.drawable.default_reading_icon else R.drawable.default_avatar

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun LuaNode(
    node: UiNode,
    onAction: (String) -> Unit,
    onInput: (UiInput) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (node) {
        is UiNode.Scaffold -> LuaScaffold(node, onAction, onInput, modifier, false)
        is UiNode.BottomSheet -> androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = { onAction(node.dismissAction) },
            sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = RoundedCornerShape(topStart = node.cornerRadius.dp, topEnd = node.cornerRadius.dp),
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            LuaNode(node.content, onAction, onInput,
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 24.dp))
        }
        is UiNode.Toolbar -> LuaToolbar(node, onAction)
        is UiNode.Alert -> AlertDialog(
            onDismissRequest = { onAction(node.dismissAction) },
            title = node.title?.let { { Text(it) } },
            text = node.subtitle?.let { { Text(it) } },
            confirmButton = { node.positive?.let { LuaNode(it, onAction, onInput) } },
            dismissButton = node.negative?.let { { LuaNode(it, onAction, onInput) } },
        )
        is UiNode.Snackbar -> Snackbar { Text(node.text) }
        is UiNode.Dialog -> androidx.compose.ui.window.Dialog(onDismissRequest = { onAction(node.dismissAction) }) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface)) {
                Column(Modifier.padding(24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(node.title, style = MaterialTheme.typography.titleLarge)
                    node.children.forEach { LuaNode(it, onAction, onInput) }
                }
            }
        }
        is UiNode.Column -> Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(node.gap.toDp()),
        ) {
            node.children.forEachIndexed { index, child ->
                LuaNode(child, onAction, onInput)
                if (hasListDivider(node.children, index, LocalReadingStyle.current)) HorizontalDivider()
            }
        }
        is UiNode.Row -> if (node.wrap) {
            FlowRow(
                modifier = modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(node.gap.dp),
                verticalArrangement = Arrangement.spacedBy(node.gap.dp),
            ) {
                node.children.forEach { LuaNode(it, onAction, onInput) }
            }
        } else Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(node.gap.toDp()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            node.children.forEach { child ->
                val weighted = (child is UiNode.Checkbox && child.showLabel) || (child is UiNode.Text && child.weight)
                LuaNode(child, onAction, onInput, if (weighted) Modifier.weight(1f) else Modifier)
            }
        }
        is UiNode.Text -> Text(
            text = node.text,
            modifier = if (node.style == UiTextStyle.Badge) modifier
                .background(MaterialTheme.colorScheme.primary, CircleShape)
                .padding(horizontal = 10.dp, vertical = 4.dp) else modifier,
            style = node.style.textStyle(MaterialTheme.typography),
            textDecoration = TextDecoration.LineThrough.takeIf { node.strikeThrough },
            color = if (node.style == UiTextStyle.Badge) MaterialTheme.colorScheme.onPrimary else when (node.tone) {
                UiTextTone.Default -> androidx.compose.material3.LocalContentColor.current
                UiTextTone.Secondary -> MaterialTheme.colorScheme.onSurfaceVariant
                UiTextTone.Accent -> MaterialTheme.colorScheme.primary
                UiTextTone.Gold -> MaterialTheme.colorScheme.tertiary
            },
        )
        is UiNode.Image -> AsyncImage(
            model = node.url?.let { if (it.startsWith("data:image/png;base64,")) SavedFavicon.decode(it.substringAfter(',')) else it },
            contentDescription = node.label,
            modifier = modifier.size(node.width.dp, node.height.dp)
                .then(if (node.circleCrop) Modifier.clip(CircleShape) else Modifier),
            fallback = painterResource(defaultImageResource(LocalReadingStyle.current)),
            error = if (LocalReadingStyle.current) painterResource(R.drawable.default_reading_icon) else null,
            contentScale = ContentScale.Crop,
        )
        is UiNode.Card -> Card(
            modifier = modifier
                .then(if (LocalReadingStyle.current) Modifier.fillMaxWidth() else Modifier)
                .then(node.action?.let {
                    Modifier.clickable(role = Role.Button, onClick = action(it, onAction))
                } ?: Modifier),
            shape = RoundedCornerShape(if (node.style == UiCardStyle.Accent) 24.dp else 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = node.style.containerColor(MaterialTheme.colorScheme),
                contentColor = node.style.contentColor(MaterialTheme.colorScheme),
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = if (node.style == UiCardStyle.Outlined) 1.dp else 0.dp),
            border = if (node.style == UiCardStyle.Outlined) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
        ) {
            Column(Modifier.padding(if (LocalReadingStyle.current) 20.dp else if (node.style == UiCardStyle.Accent) 24.dp else 12.dp)) {
                node.children.forEach { LuaNode(it, onAction, onInput) }
            }
        }
        is UiNode.Button -> {
            if (node.style == UiButtonStyle.Quiet) {
                TextButton(
                    onClick = action(node.action, onAction, node.enabled),
                    modifier = modifier.heightIn(min = 48.dp),
                    enabled = node.enabled,
                    content = { Text(node.text) },
                )
            } else {
                Button(
                    onClick = action(node.action, onAction, node.enabled),
                    modifier = modifier.heightIn(min = 48.dp).semantics {
                        if (node.style == UiButtonStyle.Filter || node.style == UiButtonStyle.Selected) {
                            selected = node.style == UiButtonStyle.Selected
                        }
                    },
                    enabled = node.enabled,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = node.style.containerColor(MaterialTheme.colorScheme),
                        contentColor = node.style.contentColor(MaterialTheme.colorScheme),
                    ),
                    content = { Text(node.text) },
                )
            }
        }
        is UiNode.TextField -> {
            if (node.style == UiTextFieldStyle.Plain) {
                TextField(
                    value = node.value,
                    onValueChange = textInput(node.action, node.enabled, onInput),
                    modifier = modifier.then(if (LocalReadingStyle.current) Modifier.fillMaxWidth() else Modifier),
                    enabled = node.enabled,
                    label = { Text(node.label) },
                    trailingIcon = node.trailingIcon?.let { icon -> ({ LuaNode(icon.copy(enabled = node.enabled && icon.enabled), onAction = onAction, onInput = onInput) }) },
                    singleLine = !node.multiline,
                    minLines = if (node.multiline) 4 else 1,
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
                    modifier = modifier.then(if (LocalReadingStyle.current) Modifier.fillMaxWidth() else Modifier),
                    enabled = node.enabled,
                    label = { Text(node.label) },
                    trailingIcon = node.trailingIcon?.let { icon -> ({ LuaNode(icon.copy(enabled = node.enabled && icon.enabled), onAction = onAction, onInput = onInput) }) },
                    singleLine = !node.multiline,
                    minLines = if (node.multiline) 4 else 1,
                    isError = node.error != null,
                    supportingText = node.error?.let { error -> ({ Text(error) }) },
                )
            }
        }
        is UiNode.Checkbox -> if (!node.showLabel) {
            Checkbox(
                checked = node.checked,
                onCheckedChange = checkedInput(node.action, node.enabled, onInput),
                modifier = modifier.semantics { contentDescription = node.label },
                enabled = node.enabled,
                colors = CheckboxDefaults.colors(checkedColor = checkboxColor(MaterialTheme.colorScheme)),
            )
        } else Row(
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
        is UiNode.ListItem -> Column(
            modifier = modifier.padding(horizontal = LIST_ITEM_HORIZONTAL_PADDING.dp),
        ) {
            node.children.forEach { LuaNode(it, onAction, onInput) }
        }
        is UiNode.IconButton -> IconButton(
            onClick = action(node.action, onAction, node.enabled),
            modifier = modifier,
            enabled = node.enabled,
        ) {
            Icon(node.icon.imageVector(), contentDescription = node.label)
        }
    }
}

@Composable
private fun LuaScaffold(node: UiNode.Scaffold, onAction: (String) -> Unit, onInput: (UiInput) -> Unit, modifier: Modifier, lazy: Boolean) {
    val host = remember { SnackbarHostState() }
    LaunchedEffect(node.snackbar, node.alert) {
        node.snackbar?.takeIf { node.alert == null }?.let {
            host.showSnackbar(it.text, withDismissAction = true)
            onAction(it.dismissAction)
        }
    }
    Scaffold(
        modifier = modifier,
        topBar = { node.toolbar?.let { LuaToolbar(it, onAction) } },
        snackbarHost = { SnackbarHost(host) },
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
    ) { padding ->
        LuaUi(LuaUiResult.Success(node.content), onAction, Modifier.fillMaxSize().padding(padding).padding(24.dp), onInput, lazy)
    }
    node.bottomSheet?.takeIf { node.alert == null }?.let { LuaNode(it, onAction, onInput) }
    node.alert?.let { LuaNode(it, onAction, onInput) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LuaToolbar(node: UiNode.Toolbar, onAction: (String) -> Unit) {
    var expanded by remember(node) { mutableStateOf(false) }
    val toolbarColor = LocalToolbarColor.current ?: MaterialTheme.colorScheme.surface
    val contentColor = toolbarContentColor(toolbarColor)
    TopAppBar(
        title = { Text(node.title) },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = toolbarColor,
            titleContentColor = contentColor,
            navigationIconContentColor = contentColor,
            actionIconContentColor = contentColor,
        ),
        navigationIcon = {
            node.backAction?.let { back ->
                IconButton(onClick = { onAction(back) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            }
        },
        actions = {
            node.children.forEach { LuaNode(it, onAction, {}) }
            if (node.overflow.isNotEmpty()) {
                IconButton(modifier = Modifier.padding(end = 12.dp), onClick = { expanded = true }) { Icon(Icons.Default.MoreVert, "More options") }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    node.overflow.filterIsInstance<UiNode.Button>().forEach { item ->
                        DropdownMenuItem(text = { Text(item.text) }, enabled = item.enabled, onClick = {
                            expanded = false
                            action(item.action, onAction, item.enabled)()
                        })
                    }
                }
            }
        },
    )
}

fun toolbarContentColor(background: Color): Color =
    if (background.luminance() > 0.179f) Color.Black else Color.White

internal fun Int.toDp(): Dp = dp

internal fun UiTextStyle.textStyle(typography: Typography): TextStyle = when (this) {
    UiTextStyle.Body -> typography.bodyLarge
    UiTextStyle.Title -> typography.titleLarge
    UiTextStyle.Metric -> typography.headlineMedium
    UiTextStyle.Label -> typography.labelMedium
    UiTextStyle.Badge -> typography.labelMedium
    UiTextStyle.Heading -> typography.headlineLarge
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
    UiButtonStyle.Filter -> colors.surfaceContainerLow
    UiButtonStyle.Selected -> colors.primary
    UiButtonStyle.Destructive -> colors.error
}

internal fun checkboxColor(colors: ColorScheme) = colors.tertiary

internal const val LIST_ITEM_HORIZONTAL_PADDING = 4

internal fun hasListDivider(children: List<UiNode>, index: Int, reading: Boolean = false) =
    !reading && children.getOrNull(index) is UiNode.ListItem && children.getOrNull(index + 1) is UiNode.ListItem

internal fun UiIcon.imageVector() = when (this) {
    UiIcon.Delete -> Icons.Default.Delete
    UiIcon.Add -> Icons.Default.Add
    UiIcon.Check -> Icons.Default.Check
    UiIcon.Close -> Icons.Default.Close
    UiIcon.Paste -> PasteIcon
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

internal fun UiCardStyle.contentColor(colors: ColorScheme) = when (this) {
    UiCardStyle.Accent -> colors.onPrimary
    UiCardStyle.Orange -> colors.onTertiary
    UiCardStyle.LightOrange -> colors.onTertiaryContainer
    else -> colors.onSurface
}

internal fun UiButtonStyle.contentColor(colors: ColorScheme) = when (this) {
    UiButtonStyle.Orange -> colors.onTertiary
    UiButtonStyle.Destructive -> colors.onError
    UiButtonStyle.Filter -> colors.onSurface
    UiButtonStyle.Quiet -> colors.primary
    else -> colors.onPrimary
}
