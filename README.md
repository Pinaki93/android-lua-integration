<div align="center">

# Lua Playground

### Safe Lua-driven Compose UI examples for Android

![Android](https://img.shields.io/badge/Android-23%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)
![Lua](https://img.shields.io/badge/Lua-5.2-2C2D72?logo=lua&logoColor=white)

</div>

## What it does

This is a playground of sorts to orchestrate UI and business logic via lua scripting instead of native Kotlin code.

## Lua as an alternative to Server Driven UI
### 
Lua presents an alternative to Server Driven UI. Instead of syncing a UI tree and coordinating actions through APIs or complex dynamic configurations, we can instead download Lua scripts which would orchestrate the UI. This gives us an option to change business logic and UI on the fly without an App Release. You can
 - Deliver new features
 - Deliver bug fixes faster
 - Deliver new themes
 - Perform A/B on different flows with confidence
... and much more, without the hassles of Play Store review cycles.

### Let Lua orchestrate, Delegate Heavy-lifting to Native Stack
Under the hood, let the native stack (written in Kotlin + Compose for this project) do the heavy-lifting. Let Kotlin 
- read files from storage
- Interpret UI trees and draw the actual UI
- Parse JSON
- Perform Network operations
... All this while Lua handles the orchestration of UI and UI events.

## What this project contains
- A small (read incomplete) lua compatibility wrapper over compose so that Lua can define UI trees while the native stack can render it
- A hook for app startup in Lua (App.lua) which registers which screens are to be shown
- A generic container for lua screen which provides other utilities such as storage
- Screens built using a JSON file as a backend for storing data
- A gradle task that compiles lua into luac files which are then dynamically loaded by the app 

