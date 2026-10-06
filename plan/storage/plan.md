# Generic Lua storage capability

Read `AGENTS.md` and `../form-ui/plan.md` first. This replaces the earlier Kotlin-owned todo design. Storage belongs to `:lua-compose`; todo state, serialization, validation, and event handling belong entirely to Lua.

## Contract

Create `lua-compose/src/main/java/com/example/luacompose/LuaStorage.kt`:

```kotlin
class LuaStorage(
    private val read: () -> ByteArray?,
    private val write: (ByteArray) -> Unit,
) {
    fun get(key: String): String?
    fun set(key: String, value: String)
    fun remove(key: String)
}

fun persistentLuaStorage(directory: File): LuaStorage
```

The two functions are the native/in-memory adapter. `read == null` means no saved snapshot. `write` atomically replaces a snapshot or throws, preserving the previous data. No interface hierarchy is needed. Kotlin knows only string keys/values, never todo fields.

The persistent session exposes a read-only capability table:

```lua
local encoded = storage.get("todos.v1") -- string, or nil when absent
local ok, failure = pcall(storage.set, "todos.v1", candidate_snapshot)
if ok then
  items = candidate_items -- Lua commits its state after successful persistence
else
  message = "Could not save tasks. Please try again."
end
storage.remove("optional.setting") -- true, including when already absent
```

`get` returns one string or nil. `set` and `remove` return true after success; unchanged set/absent removal need not write. Use dot syntax, exact argument counts, and actual Lua string types without coercion. Reject tables, functions, userdata, numbers, booleans, and nil as keys/values. Values are strict UTF-8, including empty strings, newlines, NUL, and Unicode; reject malformed bytes instead of replacing them. Lua owns serialization; do not add table serialization, JSON dependencies, source execution, or Kotlin domain schemas.

## Validation, quotas, and errors

| Boundary | Fixed limit |
| --- | --- |
| Key | ASCII `[A-Za-z][A-Za-z0-9_.-]{0,63}`, case-sensitive |
| Entries | 64 distinct keys |
| One value | 128 KiB UTF-8 (131,072 bytes) |
| Namespace | 256 KiB summed key and value UTF-8 bytes |
| Encoded snapshot | `256 * 1024 + 12 + 8 * 64` bytes |

Validate before writing; replacing a key credits its old value and removal releases quota. The 128 KiB value limit accommodates 100 titles of 200 non-BMP Unicode code points (80,000 raw UTF-8 title bytes) plus the compact raw length-prefixed Lua codec's version, IDs, completion flags, and lengths. Do not expand titles into escaped Unicode sequences; test the complete maximum-size todo snapshot against this quota. The aggregate limit still includes keys, so two full 128 KiB values cannot coexist under nonempty keys. Keys never become paths. The host chooses an app-private directory per namespace. Lua cannot select namespaces, list keys, clear all storage, access files, or receive Java/Android objects.

Map failures to stable LuaError messages without backend details or stored data: `storage: invalid argument`, `storage: quota exceeded`, `storage: invalid data`, `storage: unavailable`, and `storage: unavailable during render`. Use a small internal error/code distinction rather than matching exception text. Catch expected exceptions, not cancellation or fatal VM errors. Lua may handle errors through pcall; uncaught errors follow the generic session failure result.

## Storage algorithm and native adapter

Use one process-wide lock for every complete get/set/remove operation, including read, validation, and replacement. Read a fresh snapshot on every operation; do not cache a stale map between instances. This also prevents leave/reopen and activity recreation races when an earlier write is still finishing. Different instances over the same namespace must see one another's writes. Add a `ponytail:` comment documenting the bounded whole-map IO/global-lock ceiling and single-process scope; move to a database only if real scale requires it.

Missing snapshot means empty map. Corrupt, unsupported, oversized, or unreadable snapshots fail and are never silently reset. A mutation reads and validates the existing map, builds a candidate, checks quotas, encodes, then writes. A transient read/write error is retryable; invalid input never invokes write. There is no optimistic published cache to roll back.

Use standard byte/data streams and a deterministic generic format: integer magic, version 1, count, then sorted keys with integer UTF-8 byte lengths and bytes for each key/value. Do not use writeUTF, whose modified-UTF size ceiling conflicts with the value limit. Validate count/lengths and remaining bytes before allocation. Reject duplicate/invalid keys, bad magic/version, malformed UTF-8, truncation, trailing bytes, and all quota violations.

`persistentLuaStorage(directory)` lives in the library and safely creates the host-owned directory. Use fixed `storage.bin` with native AtomicFile backup/recovery and the existing bounded-read pattern generalized from TodoStore. Read no more than the snapshot cap plus one byte; return null only when neither a base nor recoverable backup exists. Use startWrite, flush/descriptor sync, finishWrite, and failWrite on failure. Since native finalization may log failures instead of throwing, verify the committed bytes before returning success. Never silently delete damaged data. Keep the adapter small; JVM tests do not claim to simulate filesystem crash recovery.

## LuaBrowser/session integration

- Keep the existing stateless LuaUiEngine.evaluate sandbox without storage. The generic persistent LuaSession explicitly receives a LuaStorage and installs the capability; without one, storage is absent.
- Reuse the frozen capability-table implementation after its real second use. Do not add a capability registry.
- The form plan supplies LuaBrowser and a retained LuaSession whose script returns `{ render = function() ... end, onEvent = function(event) ... end }`. Lua owns captured mutable state. Scripts are loaded once, not once per input event.
- Run initialization, events, render, and all storage calls on the browser's one serialized background worker. Storage is available during initialization/events only; reject calls during render before any backend access. Compose only displays the resulting validated tree.
- Disposal rejects queued/new events; a synchronous write already running may finish. A new session's storage initialization waits for the shared operation lock and observes that write.
- Each storage operation is atomic individually. An error later in a Lua handler does not roll back an already successful set/remove. Todo uses one key for a complete snapshot and updates local state only after that single write succeeds.
- MainActivity creates `persistentLuaStorage(File(applicationContext.filesDir, "lua-todo"))`; app wiring passes this generic capability to LuaBrowser. No TodoStore/TodoItem/TodoVm or domain reducers remain in Kotlin.
- todo.lua owns its versioned non-executable codec, IDs, title/list limits, add/toggle/delete, draft, validation, error UI, and reload behavior. Corrupt domain data is detected in Lua and preserved until successfully reloaded. The library sees only a string under `todos.v1`.

## Tasks and exact files

- [ ] Add library `LuaStorage.kt` and `src/test/java/com/example/luacompose/LuaStorageTest.kt`. Gate: generic snapshot, quota, synchronization, and failure tests pass using a byte-array fake.
- [ ] Integrate the adapter/phase guard into `LuaSession.kt` and `LuaBrowser.kt` from the form plan. Add adapter/phase checks to `LuaSessionTest.kt`. Gate: fresh sessions restore values and stateless evaluation still has no capability.
- [ ] Replace app composition-root store wiring in MainActivity.kt/AppContainer.kt and migrate domain state/codec into `app/src/main/assets/todo.lua`. Delete `app/src/main/java/com/example/luaplayground/feature/todo/TodoStore.kt` and `Todo.kt` once the generic browser route works. Delete obsolete TodoStoreTest.kt/TodoTest.kt after their behavior coverage is replaced by actual Lua-asset session tests; update container/route tests. Gate: Kotlin production contains no todo domain models, reducers, codec, or ViewModel.
- [ ] Update README.md with API, limits, namespace/phase rules, UTF-8 and error semantics, and per-operation atomicity. Leave any old `todos.bin` untouched; this unshipped prototype needs no automatic migration from the abandoned Kotlin format.
- [ ] Run completion checks below, fix failures, and mark only passing implementation tasks complete.

## Required in-memory checks

- Missing versus empty value; set/get/replace/remove; no-op operations; deterministic bytes; Unicode/newlines/NUL; recreation with fresh store/session instances over the same bytes.
- Every quota at exact limit and one over, including values of exactly 131,072 UTF-8 bytes accepted and 131,073 rejected, with both ASCII and mixed multibyte inputs. Construct an aggregate snapshot with exactly 262,144 key-plus-value UTF-8 bytes and reject one byte more; account for keys explicitly. Replacement/removal reclaim capacity; malformed arguments never write.
- All Lua arity/types, invalid UTF-8 bytes, read-only table, and denied Android/reflection/file/network capabilities.
- Bad headers/version/count/lengths, invalid or duplicate keys, malformed UTF-8, truncation/trailing bytes, and oversized persisted input never overwrite saved bytes; transient read failure can recover.
- Failed write preserves old durable data, valid retry succeeds, and concurrent distinct updates through different instances preserve both keys and quotas.
- Storage allowed in initialization/events, rejected before backend access in render, absent without injection; later handler error does not undo a completed operation; disposal/reopen waits for an ongoing write.
- Actual todo.lua through LuaSession covers add/toggle/delete, reload, malformed domain data, failed saves preserving draft/committed items, and restoration through a fresh session. Persist and restore 100 titles containing 200 non-BMP code points each through the actual Lua codec and confirm the resulting UI still renders successfully. Domain behavior comes from Lua rather than a test-side Kotlin copy.

```sh
./gradlew :lua-compose:testDebugUnitTest --tests '*LuaStorageTest' --tests '*LuaSessionTest'
./gradlew testDebugUnitTest assembleDebug
```

Check every Lua file is at most 500 KiB and search Kotlin production for removed todo domain types. Report actual checks and device-verification limits honestly.
