<div align="center">

# Lua Playground

### Safe Lua-driven Compose UI examples for Android

![Android](https://img.shields.io/badge/Android-23%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)
![Lua](https://img.shields.io/badge/Lua-5.2-2C2D72?logo=lua&logoColor=white)

</div>

## What it does

- Renders a reactive dashboard described by a constrained Lua UI DSL
- Runs a persistent Lua todo page with app-private JSON storage
- Registers bundled Lua pages and the start destination from `lua/app.lua`
- Uses Jetpack Compose throughout, with Lua isolated behind explicit capabilities

## Lua Compose screens

`:lua-compose` is an independent Android library. Stateless screens pass a script and read-only state map to `LuaUiEngine`; stateful screens use one persistent `LuaSession`. Both return `LuaUiResult`, and `LuaUi` renders only the validated tree.

The supported DSL includes columns, rows, text, cards, buttons, text fields, and checkboxes. The shipped [`dashboard.lua`](lua/dashboard.lua) demonstrates read-only Kotlin state and named actions:

```kotlin
val result = LuaUiEngine().evaluate(script, state)
LuaUi(result, onAction = ::dispatch)
```

Compose scripts receive safe base functions and supported `ui` constructors. Persistent sessions additionally receive only explicitly supplied navigation and storage capabilities. They cannot access Java reflection, Android APIs, arbitrary files, network, processes, modules, debug functions, or dynamic loading.

Scripts are limited to 500 KiB. Invalid fields or types, cycles, invalid actions, runtime failures, and resource-limit violations produce a deterministic `LuaUiResult.Failure` rather than a partial tree.

## Persistent todo page

The shipped [`todo.lua`](lua/todo.lua) owns its draft, validation, item mutations, messages, rendering, and persistence decisions. It uses an app-private JSON document through the narrow storage API:

```lua
local store = storage.open("todos.json")
```

Storage names must be safe `.json` basenames. Documents are UTF-8 JSON, capped at 256 KiB, and atomically replaced. Corrupt data blocks mutation instead of being overwritten, and storage errors never expose paths or document contents.

## Lua page navigation

The startup script registers destinations atomically:

```lua
registerRoute("playground", "playground.luac")
registerRoute("todo", "todo.luac")
setStartRoute("playground")
```

Routes support literal path segments and `{name}` placeholders. Page scripts receive decoded placeholders through `navigation.arguments`, and can call `navigation.navigate("item/42")` or `navigation.back()`. Invalid startup scripts, route patterns, destinations, or assets produce a generic startup error without publishing a partial graph.

## Run and test

Requirements are Android Studio with Android SDK 37 and JDK 17.

```sh
./gradlew testDebugUnitTest assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`. The unit suite uses plain JUnit and fakes; no emulator is required.
