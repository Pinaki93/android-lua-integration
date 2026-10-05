# ViewModel rules

- Name every ViewModel with the `Vm` suffix, never `ViewModel`.
- Use one feature-wide `Vm` per feature. Keep it within that feature's boundary; it must not own or access another feature's state or responsibilities.
