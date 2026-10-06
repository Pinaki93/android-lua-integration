package com.example.luacompose

sealed interface LuaUiResult {
    data class Success(val root: UiNode) : LuaUiResult

    @ConsistentCopyVisibility
    data class Failure private constructor(val errors: List<LuaUiError>) : LuaUiResult {
        constructor(errors: Iterable<LuaUiError>) : this(errors.immutableList())
    }
}

data class LuaUiError(
    val kind: Kind,
    val message: String,
) {
    enum class Kind {
        Syntax,
        Runtime,
        Validation,
        Limit,
    }
}
