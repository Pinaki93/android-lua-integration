package com.example.luacompose

import java.util.Collections

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
    ) : UiNode {
        constructor(children: Iterable<UiNode>, gap: Int = 0) : this(children.immutableList(), gap)
    }

    data class Text(
        val text: String,
        val style: UiTextStyle = UiTextStyle.Body,
    ) : UiNode

    @ConsistentCopyVisibility
    data class Card private constructor(val children: List<UiNode>) : UiNode {
        constructor(children: Iterable<UiNode>) : this(children.immutableList())
    }

    data class Button(
        val text: String,
        val action: String,
        val enabled: Boolean = true,
    ) : UiNode

    data class TextField(
        val value: String,
        val label: String,
        val action: String,
        val enabled: Boolean = true,
        val error: String? = null,
    ) : UiNode

    data class Checkbox(
        val checked: Boolean,
        val label: String,
        val action: String,
        val enabled: Boolean = true,
    ) : UiNode
}

sealed interface UiInput {
    val action: String

    data class TextChanged(override val action: String, val value: String) : UiInput
    data class CheckedChanged(override val action: String, val checked: Boolean) : UiInput
}

enum class UiTextStyle {
    Body,
    Title,
    Metric,
}

internal fun <T> Iterable<T>.immutableList(): List<T> =
    Collections.unmodifiableList(toList())
