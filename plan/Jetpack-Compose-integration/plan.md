# Jetpack Compose integration

## Goal

Let the Android app describe safe, reactive Jetpack Compose screens in Lua while Kotlin owns execution, validation, state, events, and rendering.

## Scope

- Add a separate `:lua-compose` Android library using the already-installed LuaJ dependency.
- Evaluate a constrained Lua UI script into a Kotlin `UiNode` tree.
- Render that tree with one Compose `LuaUi` entry point.
- Return validation and runtime failures through `LuaUiResult` instead of crashing.
- Add an app route backed by a sample dashboard Lua asset.

## Non-goals

- Arbitrary Java, Android, network, or storage access from Lua.
- XML layouts, Android `View` construction, hot reload, remote script delivery, or a general plugin system.
- Custom layout engines, dynamic Kotlin class loading, speculative interfaces, factories, or multiple renderers.

## Library API

```kotlin
class LuaUiEngine {
    fun evaluate(script: String, state: Map<String, Any?> = emptyMap()): LuaUiResult
}

sealed interface LuaUiResult {
    data class Success(val root: UiNode) : LuaUiResult
    data class Failure(val errors: List<LuaUiError>) : LuaUiResult
}

@Composable
fun LuaUi(
    result: LuaUiResult,
    onAction: (String) -> Unit,
    modifier: Modifier = Modifier,
)
```

`UiNode` is a sealed immutable model with only the supported nodes: `Column`, `Row`, `Text`, `Card`, and `Button`. Node properties use small Kotlin value types and validated primitives.

## Lua DSL

Lua receives only a read-only `state` table and the `ui` constructors. The script returns exactly one root node.

```lua
return ui.column {
  gap = 16,
  children = {
    ui.text { text = "Operations dashboard", style = "title" },
    ui.row {
      gap = 12,
      children = {
        ui.card {
          children = {
            ui.text { text = "Active users" },
            ui.text { text = tostring(state.activeUsers), style = "metric" }
          }
        },
        ui.card {
          children = {
            ui.text { text = "Revenue" },
            ui.text { text = state.revenue, style = "metric" }
          }
        }
      }
    },
    ui.button { text = "Refresh", action = "refresh" }
  }
}
```

Example state:

```lua
state = {
  activeUsers = 1284,
  revenue = "$42,680",
  refreshedAt = "09:45"
}
```

## Data flow

```text
App state + Lua asset -> LuaUiEngine -> validated UiNode tree/LuaUiResult
                                    -> LuaUi -> Jetpack Compose
Button action name -> app reducer -> new immutable state -> evaluate again
```

The library never owns business state. The app loads the asset, supplies state, handles actions, and chooses how state changes.

## State/events

Buttons emit named actions such as `refresh`; Lua cannot pass executable callbacks. The app reduces an action into a new immutable state map, then re-evaluates the same script. Compose observes the new `LuaUiResult`. This keeps event handling testable and prevents mutable Lua state from becoming a second source of truth.

## Security/errors

- Create a sandboxed LuaJ environment containing only the minimal safe Lua functions plus `ui` and read-only `state`.
- Expose no Java reflection, class loading, Android APIs, network, storage, process, package, debug, or module-loading capabilities.
- Reject scripts over 500 KB measured as UTF-8 bytes before evaluation.
- Enforce explicit maximum tree depth, node count, per-text length, and total text length while converting Lua tables to `UiNode`.
- Reject unknown node types, properties, styles, actions, invalid value types, cycles, multiple roots, and missing required fields.
- Convert syntax, runtime, validation, and limit failures into stable `LuaUiError` values; render a safe error state and never a partial tree.

## Modules and files

```text
settings.gradle.kts                         register :lua-compose
lua-compose/
  build.gradle.kts                         Android library, Compose, LuaJ
  src/main/java/.../LuaUiEngine.kt          sandbox, evaluation, table parsing
  src/main/java/.../UiNode.kt               sealed immutable UI model
  src/main/java/.../LuaUiResult.kt           result and error values
  src/main/java/.../LuaUi.kt                 Compose renderer
  src/test/java/.../LuaUiEngineTest.kt       in-memory engine tests
  src/test/java/.../LuaUiLimitsTest.kt       boundary and sandbox tests
app/
  src/main/assets/dashboard.lua             sample dashboard
  src/main/java/.../dashboard/Dashboard.kt  state, reducer, asset evaluation, route
  src/test/java/.../dashboard/DashboardTest.kt
```

Keep functions focused and files small. Extract shared code only after a second real use.

## Unit tests

Use plain JUnit and in-memory scripts/fakes. Cover every supported node and property, nested ordering, state conversion, named actions, immutable re-evaluation, syntax/runtime errors, unknown fields, invalid types, cycles, missing root, UTF-8 size enforcement, depth/node/text limits, and attempts to reach Java, Android, network, storage, loading, or reflection. Test the dashboard reducer and evaluation without an emulator.

## Implementation phases

1. Register `:lua-compose` and its minimal dependencies.
2. Add `UiNode`, `LuaUiResult`, and failing engine tests.
3. Implement the sandbox, DSL constructors, parser, validation, and limits until engine tests pass.
4. Implement `LuaUi` for the five node types with Material Compose and accessible button semantics.
5. Add the dashboard asset, immutable state/reducer, and app route.
6. Run all unit tests and the Android build; fix failures before completion.

## Acceptance criteria

- `:lua-compose` is an independent Android library and the app depends on it in one direction.
- A Lua dashboard using `ui.column`, `ui.row`, `ui.text`, `ui.card`, `ui.button`, and `state` renders through Jetpack Compose with no XML.
- Named button actions update app-owned immutable state and re-evaluate the script.
- Unsafe capabilities and every configured size/shape limit are rejected with deterministic `LuaUiResult.Failure` errors.
- Production behavior and edge cases have fast, in-memory unit tests.
- All unit tests and the Android build pass.
- The implementation contains no one-implementation interfaces, factories, or unused extension points.
