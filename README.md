# Lua Playground

An Android Jetpack Compose playground that runs bundled Lua 5.2 scripts with line-based input and captured output.

## Run

Open the project in Android Studio, or build from the command line with an Android SDK configured:

```sh
./gradlew testDebugUnitTest assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## First-stage scope

- Playground home screen and console screen
- Three scripts in `app/src/main/assets/lua`
- `print`, `io.write`, `io.read()` / `io.read("*l")`, and `io.flush`
- Lua base, string, table, math, bit32, and coroutine libraries
- No storage, network, Android API, or Java-reflection access from Lua
