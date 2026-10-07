<div align="center">

# Lua Playground

### Safe Lua console and Compose UI examples for Android

Run bundled Lua 5.2 scripts in a clean Jetpack Compose app—with line-based input, captured output, and deliberately limited capabilities.

![Android](https://img.shields.io/badge/Android-23%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)
![Lua](https://img.shields.io/badge/Lua-5.2-2C2D72?logo=lua&logoColor=white)

</div>

## What it does

- Compiles root-level `lua/` sources to `.luac` assets during the Android build
- Shows each script's source before execution
- Accepts one input value per line
- Captures `print` and `io.write` output in the app
- Reports syntax and runtime errors without losing earlier output
- Runs scripts away from the main thread
- Renders a reactive dashboard described by a constrained Lua UI DSL

The console ships with three examples:

| Script | Try it with |
| --- | --- |
| `hello.lua` | A name |
| `calculator.lua` | Two numbers and an operator on separate lines |
| `word_count.lua` | A sentence |

The home screen also opens the bundled **Lua Compose dashboard**. Tap **Refresh dashboard** to send the named `refresh` action to Kotlin, update app-owned state, and evaluate the same asset again.

## Lua Compose screens

`:lua-compose` is an independent Android library. Stateless screens pass a script and read-only state map to `LuaUiEngine`; stateful screens use one persistent `LuaSession`. Both return `LuaUiResult`, and `LuaUi` renders only the validated tree.

The supported DSL is deliberately small:

| Constructor | Fields |
| --- | --- |
| `ui.column` | optional integer `gap`, optional list `children` |
| `ui.row` | optional integer `gap`, optional list `children` |
| `ui.text` | required string `text`, optional `style`: `body`, `title`, or `metric` |
| `ui.card` | optional list `children` |
| `ui.button` | required string `text` and 1–64 character named `action`; optional boolean `enabled` |
| `ui.textField` | required string `label`, `value`, and `action`; optional boolean `enabled` and string `error` |
| `ui.checkbox` | required string `label`, boolean `checked`, and `action`; optional boolean `enabled` |

The shipped [`dashboard.lua`](lua/dashboard.lua) is the canonical sample. Its state and action contract looks like this:

```lua
return ui.column {
  gap = 20,
  children = {
    ui.text { text = "Good morning, team", style = "title" },
    ui.row {
      gap = 12,
      children = {
        ui.card { children = {
          ui.text { text = state.revenue, style = "metric" }
        } },
        ui.card { children = {
          ui.text { text = state.reliability, style = "metric" }
        } }
      }
    },
    ui.button { text = "Refresh dashboard", action = "refresh" },
    ui.text { text = state.refreshedAt }
  }
}
```

The complete dashboard also reads `state.activeUsers` and `state.activityNote`; all five state keys shown in the asset are supplied by `DashboardVm`. Kotlin supplies primitive values, nested string-keyed maps, lists, and `null`. State tables are read-only. A button emits only its validated action name; action names start with a lowercase ASCII letter and otherwise contain only letters, digits, `_`, `.`, or `-`. The dashboard reducer creates new immutable Kotlin state and re-evaluates the script.

The library API stays equally small:

```kotlin
val result = LuaUiEngine().evaluate(script, state)
LuaUi(result, onAction = ::dispatch)
```

Compose scripts get safe base functions and the supported `ui` constructors. Persistent sessions additionally get string/table functions and, only when explicitly granted, a frozen `store` capability. They cannot access Java reflection, Android APIs, arbitrary files, network, processes, modules, debug functions, or dynamic loading. Unknown fields/types, cycles, invalid actions, syntax/runtime failures, and limits return a deterministic `LuaUiResult.Failure` rather than a partial tree. Limits are 500 KiB per UTF-8 script, depth 32, 1,000 nodes, 10,000 Unicode code points per text value, 100,000 total text code points, and gaps from 0 to 1,000.

The shipped [`todo.lua`](lua/todo.lua) owns its draft, validation, IDs, item mutations, persistence decisions, messages, and rendering. A session script returns exactly `{ render = function, onEvent = function }`; the host admits only matching enabled action, text, or checked events from the latest validated tree. Closures survive events for the session lifetime.

The todo document is JSON in the app-private `todos.json` file:

```json
{"version":1,"nextId":2,"items":[{"id":1,"title":"Example","completed":false}]}
```

`JsonStore` exposes only `create`, `read`, `update`, and `delete`. Documents are validated with `kotlinx-serialization-json`, encoded as UTF-8, capped at 256 KiB, and atomically replaced. Lua receives JSON-compatible tables; mixed/sparse/cyclic tables, non-finite numbers, excessive nesting, invalid values, and writes during rendering are rejected. Empty Lua tables encode as arrays. Corrupt data blocks mutation rather than being overwritten, and storage errors never expose paths or document contents.

The todo schema allows at most 100 items and 200 Unicode code points per title. Lua validates the complete document after every read and commits local changes only after a successful store operation. Recreating the feature starts a fresh session: persisted items reload, while an unsaved draft may be lost. Only bundled, trusted scripts are supported.

## Safety by design

Console Lua receives only the capabilities the app installs explicitly:

- Base, string, table, math, bit32, package, and coroutine libraries
- `print`, `io.write`, `io.read()` / `io.read("*l")`, and `io.flush`
- No storage, network, Android API, or Java-reflection access

Each execution gets fresh globals, so scripts cannot leak state into the next run. Keep every Lua file at or below **500 KiB**.

## Run it

Requirements:

- Android Studio with Android SDK 37
- JDK 17

Open the project in Android Studio and run the `app` configuration, or use the Gradle wrapper:

```sh
./gradlew testDebugUnitTest assembleDebug
```

The debug APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Add a script

Create a `.lua` file in `lua/console`. The build compiles it into the app's generated assets and it appears automatically in the script picker.

```lua
io.write("What is your name? ")
local name = io.read()
print("Hello, " .. (name or "friend") .. "!")
```

Add fast JVM coverage in `LuaScriptsTest` for every behavior and edge case the script introduces.

## How it fits together

```text
Compose UI  →  feature ViewModel  →  LuaEngine  →  LuaJ
                    ↓
             bundled assets
```

The boundaries stay intentionally small: `AssetManager` lists and reads bundled scripts, `LuaEngine` owns the permitted runtime, and feature containers wire dependencies without a framework.

## Test

```sh
./gradlew testDebugUnitTest
```

The suite uses plain JUnit, coroutine test dispatchers, and fakes—no emulator required.
