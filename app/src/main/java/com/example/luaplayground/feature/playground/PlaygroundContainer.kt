package com.example.luaplayground.feature.playground

import androidx.compose.runtime.Immutable
import com.example.luaplayground.Navigator

@Immutable
class PlaygroundContainer(val navigator: Navigator) {
    fun createVm() = PlaygroundVm(navigator)
}
