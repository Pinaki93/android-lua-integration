package com.example.luacompose

import androidx.compose.runtime.Immutable
import java.util.Collections

@Immutable
sealed interface UiNode {
    @ConsistentCopyVisibility
    data class Column private constructor(
        val children: List<UiNode>,
        val gap: Int,
    ) : UiNode {
        constructor(children: Iterable<UiNode>, gap: Int = 0) : this(children.immutableList(), gap)
    }

    @ConsistentCopyVisibility
    data class Row private constructor(
        val children: List<UiNode>,
        val gap: Int,
        val wrap: Boolean,
    ) : UiNode {
        constructor(children: Iterable<UiNode>, gap: Int = 0, wrap: Boolean = false) : this(children.immutableList(), gap, wrap)
    }

    @ConsistentCopyVisibility
    data class Dialog private constructor(
        val title: String,
        val children: List<UiNode>,
        val dismissAction: String,
    ) : UiNode {
        constructor(title: String, children: Iterable<UiNode>, dismissAction: String) :
            this(title, children.immutableList(), dismissAction)
    }

    data class Text(
        val text: String,
        val style: UiTextStyle = UiTextStyle.Body,
        val weight: Boolean = false,
        val strikeThrough: Boolean = false,
        val tone: UiTextTone = UiTextTone.Default,
    ) : UiNode

    data class Image(
        val url: String?,
        val label: String,
        val width: Int = 48,
        val height: Int = 48,
        val circleCrop: Boolean = false,
    ) : UiNode

    @ConsistentCopyVisibility
    data class Card private constructor(
        val children: List<UiNode>,
        val style: UiCardStyle,
        val action: String?,
    ) : UiNode {
        constructor(children: Iterable<UiNode>, style: UiCardStyle = UiCardStyle.Default, action: String? = null) :
            this(children.immutableList(), style, action)
    }

    @Immutable
    data class Button(
        val text: String,
        val action: String,
        val enabled: Boolean = true,
        val style: UiButtonStyle = UiButtonStyle.Primary,
    ) : UiNode

    @Immutable
    data class TextField(
        val value: String,
        val label: String,
        val action: String,
        val enabled: Boolean = true,
        val error: String? = null,
        val style: UiTextFieldStyle = UiTextFieldStyle.Outlined,
        val multiline: Boolean = false,
        val trailingIcon: IconButton? = null,
    ) : UiNode

    @Immutable
    data class Checkbox(
        val checked: Boolean,
        val label: String,
        val action: String,
        val enabled: Boolean = true,
        val showLabel: Boolean = true,
    ) : UiNode

    data class IconButton(
        val icon: UiIcon,
        val label: String,
        val action: String,
        val enabled: Boolean = true,
    ) : UiNode

    @ConsistentCopyVisibility
    data class ListItem private constructor(val key: String, val children: List<UiNode>) : UiNode {
        constructor(key: String, children: Iterable<UiNode>) : this(key, children.immutableList())
    }
}

enum class UiIcon { Delete, Add, Check, Close, Paste }

sealed interface UiInput {
    val action: String

    data class TextChanged(override val action: String, val value: String) : UiInput
    data class CheckedChanged(override val action: String, val checked: Boolean) : UiInput
}

enum class UiTextStyle {
    Body,
    Title,
    Metric,
    Label,
    Badge,
    Heading,
}

enum class UiTextTone { Default, Secondary, Accent, Gold }

enum class UiCardStyle {
    Default,
    Accent,
    Subtle,
    Outlined,
    Orange,
    LightOrange,
}

enum class UiButtonStyle {
    Primary,
    Orange,
    Quiet,
    Filter,
    Selected,
    Destructive,
}

enum class UiTextFieldStyle {
    Outlined,
    Plain,
}

internal fun <T> Iterable<T>.immutableList(): List<T> =
    Collections.unmodifiableList(toList())
