# Working agreements

- Keep production code and tests extremely readable: short names only when obvious, small functions, and no spaghetti control flow.
- Always build Android UI with Jetpack Compose. Do not add XML layouts or programmatic View hierarchies.
- Every behavior and edge case must be visible in fast, in-memory unit tests. Prefer plain JUnit and fakes over reflection, emulators, or heavyweight test frameworks.
- Reuse the smallest existing boundary that fits. Extract shared behavior after a real second use; do not add speculative frameworks, factories, or interfaces.
- Keep Lua capabilities explicit. Storage, network, and Android APIs must only be exposed through narrow, tested adapters.
- Lua scripts must not exceed 500 KB per file.
- A change is complete only when its unit tests pass.
