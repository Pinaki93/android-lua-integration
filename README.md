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
- A Lua-driven OkHttp Contributors screen that demonstrates GitHub API pagination, retry and refresh actions, and contributor avatars through a restricted native HTTP adapter.
- A Lua-driven reading list with locally saved entries and notes, status and tag filters, title retrieval, and browser launching through a dedicated native capability.
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
├── docs/prd/      Product requirements for example flows
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

`ui.listItem { key = "item.1", children = { ... } }` is a generic container with a stable list key.
Build its content with `ui.row`, `ui.checkbox`, `ui.text`, and `ui.iconButton`, as in `lua/todo.lua`.
Checkboxes can use `showLabel = false` while retaining their accessible `label`; text can use
`weight = true` inside a row and `strikeThrough = true`. Icon buttons require an accessible
`label` and an `action`, and support the Compose icons `delete`, `add`, `check`, and `close`.
The former todo-specific `listItem` fields are replaced by `key` and `children`.

The existing `playground.lua` and `todo.lua` scripts are the simplest references for navigation, events, and persistence.

### Reading list

Open **Reading list** from the playground to save an HTTPS article URL with an
optional title, up to five tags, and a note. Entries can be filtered by read status
and tag, edited, marked read or unread, and deleted with confirmation. Tapping an
article opens a bottom sheet with **Read**, **Edit**, and **Delete** actions.
Read opens the original URL in the browser; Edit opens the saved entry and note.
Canceling deletion returns to the sheet, and failed writes keep the entry and
confirmation available for retry. The list shows 20 entries per page. Duplicate
normalized URLs open the existing entry.

Saved entries and notes work offline; original articles open in the browser and
are not downloaded. The screen persists a versioned `reading-list.json` document
through `JsonStore`, within its 256 KiB quota. Failed writes preserve the previous
list, and invalid saved documents show a reload action without resetting data.

Only `reading-list.luac` receives the frozen `reading` capability. It provides
URL normalization, title fetching, browser launching, IDs, timestamps, and small
text helpers. URLs must use HTTPS on port 443 without credentials; normalization
removes fragments and preserves path and query semantics. Title fetching rejects
private and reserved destinations, checks resolved and connected addresses,
disables redirects and cookies, and uses a 30-second timeout and 1 MiB response
limit. Lua receives only a title or error, never raw HTML or response headers.

Title retrieval starts after a new entry is saved or on explicit Retry. Failures
retain the supplied title or URL fallback; successful retrieval preserves user
titles. Interrupted requests remain retryable after reopening the screen. The
bounded parser supports UTF-8/ASCII HTML with a closed head in its first 64 KiB
and a limited set of named entities plus numeric entities.

The example adds clickable cards, wrapping rows, multiline fields, confirmation
dialogs, modal bottom sheets, and filter/destructive button styles to the validated Compose UI model.
Its cream, teal, and gold theme applies only to the reading-list route. See
[`lua/reading-list/reading-list.lua`](lua/reading-list/reading-list.lua) and the
[product requirements](docs/prd/offline-reading-list.md) for the flow and scope.

Use `ui.bottomSheet { content = ..., dismissAction = "screen.dismiss" }` in a
scaffold's `bottomSheet` slot. Sheets render with Material 3 Compose, scroll their
content, and accept an optional integer `cornerRadius` from 0 to 64 dp (default:
28). While a sheet is open, only its actions and inputs are admitted. A scaffold
alert takes precedence over the sheet until dismissed.

## Design principles

- **Native-owned capabilities:** Lua receives only the minimum APIs required by a screen.
- **Validated output:** malformed or unsupported UI trees fail safely instead of reaching Compose.
- **Bounded inputs:** scripts, UI trees, text, and persisted JSON all have explicit limits.
- **Deterministic behavior:** business rules remain testable with plain JUnit and in-memory fakes.
- **Small surface area:** the compatibility layer grows only when a real screen needs a new primitive.

## Status

This project is an experimental reference implementation. It demonstrates the core runtime boundary and several end-to-end flows, but its Compose compatibility layer is intentionally incomplete and its scripts are packaged locally with the application.

### HTTP capability

Network access is assigned in Kotlin by compiled script asset identity, in
`HttpPermissions.kt`. Only `okhttp-contributors.luac` receives the adapter; other
scripts have no `http` global. Scripts cannot grant themselves permission. The
application owns the Android Ktor engine. Redirects are disabled (the Android
engine also disables underlying connection redirects). New adapters must use an
engine that does not follow redirects itself.

```lua
http.request({
  url = "https://api.github.com/repos/lysine-dev/okhttp/contributors",
  method = "GET", -- GET is the default
  query = { page = "1", per_page = "100", anon = "1" },
  headers = { Accept = "application/vnd.github+json" }
}, function(response)
  -- response.status, response.headers (lowercase keys), response.body
  -- response.json for application/json and +json content types
  -- response.error.code when a request fails
end)
```

The generic adapter supports GET, POST, PUT, PATCH, DELETE, HEAD and OPTIONS.
Options accept string query/header values and either `body` (a string) or `json`
(a JSON-compatible Lua table). GET and HEAD reject bodies to preserve their method
on the Android engine. JSON null uses `http.null`, including inside
arrays. Callbacks must return no values. Requests are allowed during script
initialization, events and callbacks, but never during rendering. The capability
table is frozen. Validation errors with a valid callback are queued just like
network results; invalid callback signatures and render-time requests raise Lua
errors.

Error codes are `validation`, `policy`, `transport`, `timeout`, `invalid_json`,
`request_size`, `response_size` and `pending_limit`. HTTP error statuses and
redirects remain inspectable responses, with status, headers and raw body.
Limits are 30 seconds, 256 KiB request bodies, 1 MiB streamed response bodies,
JSON depth 32 and four outstanding requests per session. Session closure or
terminal Lua failure cancels outstanding requests. Callbacks run serially through
the ViewModel event queue, on the Lua worker, and duplicate/late completions are
ignored.

The native contributors policy allows only GET over HTTPS to port 443 on
`api.github.com`, exactly `/repos/lysine-dev/okhttp/contributors`, with one each of
`page` (positive integer), `per_page=100` and `anon=1`. Credentials, fragments,
encoded/ambiguous paths and routing-header overrides are rejected before sending.

The OkHttp Contributors example fetches API pages sequentially and displays 50
records per UI page. Failed pages keep earlier records explicitly marked
incomplete; Retry resumes the failed page and Refresh starts from page one.
Requests are public and unauthenticated. GitHub documents
[pagination and anonymous contributors](https://docs.github.com/en/rest/repos/repos#list-repository-contributors)
and the shared [60 requests/hour unauthenticated IP limit](https://docs.github.com/en/rest/using-the-rest-api/rate-limits-for-the-rest-api).
Contributor avatars are circle cropped; missing or invalid avatar URLs show a bundled default profile picture.
Authentication, uploads and automatic retries are outside this example.

### Images

`ui.image { url = "https://example.com/photo.jpg", label = "Photo", width = 96, height = 96 }`
loads a cropped image through Coil. Set `circleCrop = true` to clip it to a circle
(default: false). Omit `url` to show the bundled default profile picture. The label is required for accessibility. Width and
height default to 48 dp and must be integers from 1 to 1024. URLs must use HTTPS
on port 443 without credentials or fragments; local files and Android resources
are not exposed. Image loading is a separate network capability available to UI
scripts, independent of the `http` adapter's API allowlist. Failed loads leave the
image space empty and do not interrupt the rest of the UI.
