<div align="center">

# Lua Playground

### A small, safe Lua console for Android

Run bundled Lua 5.2 scripts in a clean Jetpack Compose app—with line-based input, captured output, and deliberately limited capabilities.

![Android](https://img.shields.io/badge/Android-23%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)
![Lua](https://img.shields.io/badge/Lua-5.2-2C2D72?logo=lua&logoColor=white)

</div>

## What it does

- Discovers `.lua` files bundled in `app/src/main/assets/lua`
- Shows each script's source before execution
- Accepts one input value per line
- Captures `print` and `io.write` output in the app
- Reports syntax and runtime errors without losing earlier output
- Runs scripts away from the main thread

The project ships with three examples:

| Script | Try it with |
| --- | --- |
| `hello.lua` | A name |
| `calculator.lua` | Two numbers and an operator on separate lines |
| `word_count.lua` | A sentence |

## Safety by design

Lua receives only the capabilities the app installs explicitly:

- Base, string, table, math, bit32, package, and coroutine libraries
- `print`, `io.write`, `io.read()` / `io.read("*l")`, and `io.flush`
- No storage, network, Android API, or Java-reflection access

Each execution gets fresh globals, so scripts cannot leak state into the next run. Keep every Lua file below **500 KB**.

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

Create a `.lua` file in `app/src/main/assets/lua`. It will appear automatically in the script picker.

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
