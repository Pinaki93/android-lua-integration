# Instructions
- Execute each todo under the Tasks section in order, using a different context window and agent for every task.
- Before starting, read `AGENTS.md`, `plan/Jetpack-Compose-integration/plan.md`, and the current repository state.
- Keep each change limited to its task, preserve completed work, and do not add speculative abstractions or dependencies.
- A task is done only after its stated completion gate passes; then change only its checkbox from `[ ]` to `[x]`.
- Never execute a completed task. Never execute tasks from the Archived Tasks section.

# Tasks
- [x] Create the independent `:lua-compose` Android library module, register the one-way app dependency, and add the immutable `UiNode`, `LuaUiResult`, and `LuaUiError` model. Completion gate: focused model unit tests and the library debug Kotlin compilation pass.
- [x] Implement `LuaUiEngine` with the minimal LuaJ sandbox, read-only state conversion, five-node Lua DSL, deterministic validation errors, UTF-8 500 KB script limit, tree/node/text limits, and cycle/capability rejection. Completion gate: plain in-memory JUnit tests cover every supported node/property plus syntax, runtime, invalid input, limit, cycle, and unsafe-capability cases, and all `:lua-compose` unit tests pass.
- [x] Implement the Material Compose `LuaUi` renderer for `Column`, `Row`, `Text`, `Card`, and `Button`, including safe failure UI, stable keys/data flow where needed, and accessible named-action buttons. Completion gate: renderer behavior that can be expressed as pure mappings has fast unit coverage, and the library unit tests and debug Kotlin compilation pass without XML or Android `View` code.
- [x] Add the dashboard Lua asset and app feature with immutable app-owned state, a small reducer, asset evaluation, named `refresh` action handling, and a Compose route wired into the existing navigation boundary. Completion gate: in-memory dashboard tests prove initial evaluation, action reduction, and re-evaluation, and app unit tests plus debug Kotlin compilation pass.
- [ ] Verify and document the finished integration: add concise usage, DSL, security/limit, state/event, and module-boundary documentation; run all repository unit tests and the Android debug build; fix every failure and remove unused or speculative code. Completion gate: the documented sample matches the shipped dashboard, all unit tests pass, the debug build succeeds, and no Lua file exceeds 500 KB.

# Archived Tasks
