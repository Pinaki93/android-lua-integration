<div align="center">

# Lua Playground

### Safe, Lua-driven Jetpack Compose UI for Android

![Android](https://img.shields.io/badge/Android-23%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)
![Lua](https://img.shields.io/badge/Lua-5.2-2C2D72?logo=lua&logoColor=white)

</div>

## Overview

Lua Playground explores how Lua can orchestrate Android UI and business logic while Kotlin and Jetpack Compose retain control of platform capabilities and rendering.

Lua scripts describe a small, validated UI tree and respond to user events. The native layer interprets that tree, renders Material 3 components, and exposes only the capabilities each script needs. This keeps the dynamic layer flexible without giving it unrestricted access to Android APIs.

The repository is a focused playground rather than a production-ready server-driven UI framework. Scripts are currently compiled and bundled with the app; remote delivery, signing, versioning, and rollback are intentionally outside the current scope.

## Lua as an alternative to server-driven UI

Traditional server-driven UI often requires a backend-defined UI schema, action contracts, and client-side logic for interpreting increasingly complex configurations. Lua offers a different trade-off: a compact script can own the orchestration of a screen while the application continues to own its native primitives and security boundaries.

With an appropriate delivery system, this approach could support:

- Shipping new flows and updating existing ones without rebuilding their native orchestration.
- Fixing script-level bugs independently of a full application release.
- Applying themes or content variations dynamically.
- Running controlled A/B experiments across complete user flows.

Any production implementation would still need to comply with app-store policies and provide secure script distribution, integrity checks, compatibility management, observability, and rollback.

## Lua orchestrates; the native stack does the heavy lifting

The project deliberately keeps Lua capabilities narrow. Lua decides what to render and how to react to supported UI events; Kotlin performs platform-sensitive work.

The native layer is responsible for:

- Loading and executing Lua bytecode.
- Validating UI trees before rendering them with Compose.
- Reading and writing bounded JSON documents through an explicit storage adapter.
- Managing navigation and passing validated route arguments.
- Controlling concurrency, lifecycle, errors, and platform access.

Lua is not given direct access to the filesystem, network, or Android framework. New capabilities should be introduced as small, purpose-built, tested adapters rather than by exposing broad platform APIs.

## What this project contains

- A small Lua-to-Compose compatibility layer with columns, rows, text, cards, buttons, text fields, and checkboxes.
- A Lua application entry point (`lua/app.lua`) that registers dynamic routes and selects the start route.
- Stateful Lua sessions with explicit `render` and `onEvent` functions.
- Native navigation helpers for route changes, back navigation, and route arguments.
- A persistent JSON storage adapter with validation, size limits, and atomic file writes.
- A native dashboard rendered from a Lua-defined UI tree and Kotlin-provided state.
- A Lua-driven todo screen that demonstrates input handling, validation, persistence, and error states.
- A Gradle build step that compiles `.lua` sources into stripped `.luac` assets before the Android build.
- Fast unit tests for the Lua boundary, UI parsing, storage, navigation, application startup, and example screens.

## How it works

1. Gradle compiles every script in `lua/` to bytecode and adds the generated files to the app's assets.
2. `app.luac` registers the available Lua routes and the application's start route.
3. Kotlin validates the route definitions and loads each referenced script.
4. A `LuaSession` executes the selected script and receives a UI tree from its `render` function.
5. The native renderer validates the tree and maps it to Material 3 Compose components.
6. User interactions are returned to the session as narrow action, text, or checkbox events.
7. The script updates its state and renders the next tree.

## Project structure

```text
.
├── app/           Android application, navigation, native containers, and examples
├── lua/           Lua application entry point and screen scripts
└── lua-compose/   Lua runtime boundary, UI model, Compose renderer, and JSON storage
```

## Getting started

### Requirements

- Android Studio with Android SDK 37 installed.
- JDK 17.
- An emulator or device running Android 6.0 (API 23) or newer.

Clone the repository, open it in Android Studio, allow Gradle to sync, and run the `app` configuration. Lua compilation is part of the normal Android build, so no separate Lua installation is required.

You can also build and test from the command line:

```bash
./gradlew test
./gradlew assembleDebug
```

To compile only the Lua sources:

```bash
./gradlew :app:compileLua
```

## Adding a Lua screen

1. Add a `.lua` file under `lua/` that returns `render` and `onEvent` functions.
2. Register its route and generated `.luac` filename in `lua/app.lua`.
3. Use only the UI nodes and native adapters explicitly exposed by the runtime.
4. Keep each script below the enforced 500 KB limit.
5. Add fast unit tests for the screen's behavior and edge cases.

The existing `playground.lua` and `todo.lua` scripts are the simplest references for navigation, events, and persistence.

## Design principles

- **Native-owned capabilities:** Lua receives only the minimum APIs required by a screen.
- **Validated output:** malformed or unsupported UI trees fail safely instead of reaching Compose.
- **Bounded inputs:** scripts, UI trees, text, and persisted JSON all have explicit limits.
- **Deterministic behavior:** business rules remain testable with plain JUnit and in-memory fakes.
- **Small surface area:** the compatibility layer grows only when a real screen needs a new primitive.

## Status

This project is an experimental reference implementation. It demonstrates the core runtime boundary and several end-to-end flows, but its Compose compatibility layer is intentionally incomplete and its scripts are packaged locally with the application.
