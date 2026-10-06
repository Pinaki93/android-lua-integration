# Lua-owned forms in a reusable LuaBrowser

## Instructions and ownership

Read AGENTS.md and plan/storage/plan.md. This plan supersedes the earlier Kotlin-owned todo design. Keep completed form-node work; replace the todo application layer. Mark each task only after its tests pass. Resolve architecture with the parent orchestrator; give implementation agents exact contracts, bounded files, and focused completion commands.

LuaBrowser belongs to :lua-compose and runs one persistent sandboxed session for one script. Lua owns application state, handlers, validation, domain limits, serialization, and persistence decisions. Kotlin owns script loading, generic lifecycle/event delivery, narrow capability adapters, validated UiNode conversion, and Compose rendering. No TodoVm, TodoState, Kotlin todo model/reducer, title validator, ID generator, or todo persistence codec remains.

Preserve the existing stateless LuaUiEngine.evaluate(script, state) and dashboard. Reuse their sandbox and parser through small internal functions because the browser is their second real consumer; do not build an extensible runtime framework.

## Public API and script contract

    @Composable
    fun LuaBrowser(
        script: String,
        storage: LuaStorage? = null,
        modifier: Modifier = Modifier,
    )

The script argument contains the source of one host-selected file, not a path Lua may traverse. The app loads its bundled asset and passes its source. Storage is the explicit host-scoped capability from the storage plan; null means no storage global. Equal source and the same storage instance retain the session across recompositions. A changed source or storage instance creates a new session. The app supplies theme, navigation, scrolling, safe drawing/IME insets, and padding around the browser.

Add a plain public LuaSession(script: String, storage: LuaStorage? = null) with start(): LuaUiResult, dispatch(event: LuaEvent): LuaUiResult, and close(). Its small synchronous API lets app-module tests drive the actual asset without Compose; callers serialize access. Construction performs no Lua execution or IO. Start loads the chunk once; repeated calls cannot repeat initialization. The session retains globals and closures until close, with no Kotlin application state map.

The chunk returns exactly one table with exactly two required function fields: render and onEvent. Render returns exactly one supported UI node. OnEvent receives one event, returns no values, and changes Lua-owned state; the host then calls render and validates a new tree. Named actions remain strings. Functions never enter UiNode or Compose callbacks.

    local draft = ""
    local message = nil

    local function on_event(event)
      if event.type == "text" and event.action == "draft" then
        draft = event.value
        message = nil
      elseif event.type == "action" and event.action == "save" then
        local ok = pcall(function() storage.set("draft", draft) end)
        message = ok and "Saved" or "Could not save. Try again."
      end
    end

    local function render()
      return ui.column { gap = 16, children = {
        ui.textField { label = "Note", value = draft, action = "draft" },
        ui.button { text = "Save", action = "save" },
        ui.text { text = message or "" }
      } }
    end

    return { render = render, onEvent = on_event }

## Event protocol

Use a public sealed generic LuaEvent with Action(action), TextChanged(action, value), and CheckedChanged(action, checked), beside UiInput or in LuaSession.kt. Map existing LuaUi callbacks into it; preserve dashboard callers. Each Lua event is a fresh read-only table:

| Kotlin event | Lua fields |
| --- | --- |
| Action("add") | type = "action", action = "add" |
| TextChanged("draft", "Buy milk") | type = "text", action = "draft", value = "Buy milk" |
| CheckedChanged("complete.7", true) | type = "checked", action = "complete.7", value = true |

Validate action syntax and text payload budgets before entering Lua. Admit events only when the latest successful tree contains a matching enabled button/text field/checkbox of the corresponding kind. Unknown, stale, disabled, or wrong-kind events are no-ops. Check when consuming a queued event, not just when enqueuing. Lua additionally validates domain cases such as deleted item IDs.

## Scheduling, lifecycle, and errors

- Execute initialization, handlers, rendering, and synchronous storage calls on one serial coroutine worker off the main thread. Never enter the same LuaJ globals concurrently. Only complete immutable LuaUiResult values return to Compose.
- LuaBrowser.kt owns the small generic lifecycle/queue bridge. Use existing coroutines and a bounded FIFO channel of 64 events. Do not disable/re-enable the text field per character. Overflow surfaces a safe terminal browser failure instead of silently dropping input. Prefer remember plus lifecycle effects over an unnecessary ViewModel or controller hierarchy.
- Show a generic loading label before initialization completes. Dispose closes the queue, rejects new events, discards pending events, and suppresses late UI publication. An already-running synchronous storage call may finish. The storage plan serializes whole operations across instances with one process-wide lock, so a reopened browser waits for a finishing old write before loading.
- Configuration recreation may create a fresh session: Lua reloads persistent items, while unsaved drafts are ephemeral. Document this limitation. Process restart follows the same initialization path. Do not add save-state machinery.
- Syntax, descriptor, uncaught handler, runtime-limit, and render-validation failures return existing categorized LuaUiResult failures with safe messages. A failed session stops accepting events because earlier Lua mutations cannot reliably be rolled back. A generic Retry control creates a fresh sandbox; never claim to roll back completed storage effects.
- Expected storage errors remain catchable through Lua pcall. Lua retains prior state and renders its own recovery controls. Kotlin never inspects todo messages or chooses domain recovery actions.
- Do not swallow coroutine cancellation in general exception handling. Close is idempotent; no handlers or callbacks run after close. Synchronize disposal with session ownership without accessing globals on two threads.

## Sandbox, forms, and limits

Preserve the existing form constructors and strict validation: textField requires value, nonempty label, and action, with optional boolean enabled and string error; checkbox requires boolean checked, nonempty label, and action, with optional enabled; button retains optional enabled. Preserve controlled Material inputs, a single labeled checkbox target with minimumInteractiveComponentSize(), and exact typed callbacks. No XML/View UI.

Retain limits: 500 KiB UTF-8 script size, tree depth 32, 1,000 nodes, 10,000 Unicode code points per value/label/error, 100,000 aggregate text code points, and the existing 1–64-character action grammar. Reset parser counters per render. Check size before compilation. Storage quotas remain independent.

Reuse safe base functions and add only LuaJ StringLib and TableLib for actual application logic and serialization. Keep Java reflection, raw IO/files, OS/process, package/require, dynamic load/dofile, Android, network, debug globals, and metatable escape paths unavailable. Freeze host capability tables and never coerce Java objects into Lua. Session-local Lua state is intentionally mutable.

Bound each chunk/handler/render invocation with a host-only instruction counter, initially 1,000,000 Lua instructions. LuaJ's internal debug hook may count instructions, but expose no debug global. The budget signal must escape Lua pcall and be handled at the Kotlin session boundary. Test direct and protected infinite loops. This is not an OS memory sandbox: only bundled/trusted scripts are supported, with no remote script loading.

## Lua todo application

Use the storage plan's exact string API: storage.get(key) returns string or nil; storage.set(key, string) and storage.remove(key) return true; stable errors are catchable using pcall. The host chooses the namespace. Kotlin knows nothing about value contents.

Rewrite app/src/main/assets/todo.lua as the full application. It owns items, draft, validation/storage errors, load success, next numeric ID, and every add/complete/delete/reload rule. Persist one key, tasks. Specify its versioned document as the ASCII header TODO1 followed by colon-terminated decimal next-ID and item-count fields; each item has colon-terminated decimal ID, completion (0 or 1), and UTF-8 title-byte-length fields, followed immediately by exactly that many title bytes. No separator is required after a length-delimited title. Use bounded substring decoding; reject truncation, trailing data, unknown versions, duplicate IDs, and invalid fields. Never evaluate saved text as Lua. Keep local codec helpers readable. IDs are positive decimal integers below 2,147,483,647; next-ID must exceed every stored ID. Reject add at ID exhaustion and preserve the document. Persist the next ID monotonically and convert IDs to strings for actions.

Lua retains the current domain limits: 100 tasks and 200 Unicode code points per title. Implement title trimming, nonblank validation, and UTF-8 code-point counting in Lua, not generic-looking Kotlin helpers. Load validates the same schema and limits. Bad or unreadable snapshots block writes until an explicit successful reload; never erase original data automatically.

For a mutation, build candidate items, encode, call storage.set inside pcall, then replace Lua items and clear draft only after success. Failed add/complete/delete preserves items and draft. Repeated checked values, unknown actions/IDs, and wrong event kinds do not save. Draft changes are ephemeral.

Render one page with title, incomplete-task count, text field, add button, errors/recovery controls, empty state, and cards with labeled checkbox and delete button. Disable add at capacity. Keep the input before the dynamic list.

## Implementation tasks

- [ ] Refactor lua-compose/src/main/java/com/example/luacompose/LuaUiEngine.kt minimally to share sandbox construction, frozen tables, root parsing, and failure conversion with a new LuaSession.kt. Extract LuaSandbox.kt only if necessary for readability. Keep old evaluator behavior. Implement descriptor validation, closures, event admission, invocation budget, and render conversion in LuaSession.kt. Gate: new LuaSessionTest.kt and existing engine/limit/model tests pass.
- [ ] Add lua-compose/src/main/java/com/example/luacompose/LuaBrowser.kt with the public composable and generic serial lifecycle bridge. Add explicit existing-version coroutines and coroutines-test dependencies to lua-compose/build.gradle.kts if needed; no framework dependency. Reuse LuaUi/UiInput and preserve accessible checkbox sizing in LuaUi.kt. Gate: in-memory LuaBrowserTest.kt covers ordering, replacement, disposal, overflow, late completion, and restart; library compilation passes.
- [ ] Implement generic LuaStorage and its explicit sandbox grant according to plan/storage/plan.md. Coordinate shared sandbox edits with that implementer. Gate: no grant means no storage; granted capability exposes only documented operations; quotas/errors/recreation/concurrent instances have in-memory tests.
- [ ] Rewrite app/src/main/assets/todo.lua. Replace app/src/test/java/com/example/luaplayground/feature/todo/TodoTest.kt with tests driving the actual asset through LuaSession and inspecting UI and stored strings. Delete obsolete TodoStoreTest.kt; move meaningful corruption/data-loss checks into generic storage and Lua asset tests. Gate: all application tests run without Kotlin todo models or reducers.
- [ ] Delete app/src/main/java/com/example/luaplayground/feature/todo/TodoStore.kt. Replace feature/todo/Todo.kt with at most a thin route around LuaBrowser, or move the small route into LuaApp.kt and delete Todo.kt. Remove TodoVm, TodoState, TodoItem, TodoContainer, reducers, codec, and domain constants. Update AppContainer.kt and MainActivity.kt for generic storage/script wiring; retain existing asset/navigation boundaries and playground entry. Update AppContainerTest.kt. Gate: Kotlin production contains no todo business state/rules, and app/navigation/asset tests pass.
- [ ] Update README.md with LuaBrowser usage, descriptor/event contract, Lua-owned state and validation, explicit storage grant, lifecycle limitations, and the actual todo asset. Remove obsolete claims that todo logic belongs to Kotlin or browser scripts cannot use granted storage; keep dashboard documentation accurate. Run the final commands and record actual results.

## In-memory test matrix

LuaSessionTest.kt: initialize once; closures survive repeated events/renders; separate sessions isolate globals; malformed descriptors/roots; exact three event kinds; strict input/action validation; stale/disabled/wrong-kind events; counters reset; absent/frozen capabilities and escape attempts; syntax/handler/render failures become terminal; direct/protected infinite loops; close/restart; catchable storage failures.

LuaBrowserTest.kt: FIFO worker and no concurrent Lua access; ordinary recomposition retains session; script/storage changes replace it; disposal rejects queued work and late results; queue overflow fails visibly; recovery starts fresh; new session loads after an old in-flight write. Use coroutine test dispatchers and in-memory fakes. Existing LuaUiTest.kt covers exact form callbacks and disabled guards.

Actual todo.lua tests: empty load; add/trim/blank/Unicode-boundary/overflow input; 100-item capacity; completion both ways and no redundant save; delete exact ID; stale/unknown/wrong-kind events; remaining count; failed load/reload recovery; failed add/complete/delete preserves items/draft and retry succeeds; fresh sessions restore order, values, and next ID; corrupt/truncated/version/duplicate-ID snapshots block overwrite; quotes/newlines/backslashes/Unicode round-trip; maximum list fits engine limits. Assert actual UiNodes and storage values instead of duplicating Lua business logic in Kotlin tests.

## Completion

    ./gradlew :lua-compose:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug
    find app/src/main/assets -name '*.lua' -size +512000c -print
    git diff --check

- [ ] JVM tests pass.
- [ ] Android debug build passes.
- [ ] Lua size check prints nothing; diff check passes.
- [ ] Todo business logic exists only in todo.lua; Kotlin browser and storage are generic.

No schema framework, callback registry, plugin system, Kotlin state mirror, multi-script module loader, database, or remote loading is required.
