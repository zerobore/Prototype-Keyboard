# Architecture

## Components

```
┌─ Companion app (Compose + Material 3) ─────────────┐
│ MainActivity → Setup / Settings / Test / About      │
│ Settings ⇄ SettingsRepository (DataStore)           │
└─────────────────────────┬──────────────────────────┘
                          │ same DataStore file
┌─ System keyboard (Views, InputMethodService) ───────┤
│ ProtoIME                                            │
│  ├─ SuggestionStripView (actions + suggestion slots)│
│  ├─ KeyboardView (custom draw + touch state machine)│
│  └─ Layouts (pure data) + AutoCaps (pure rule)      │
└─────────────────────────────────────────────────────┘
```

## Key decisions (and why)

1. **Custom `View` for the IME, Compose for the app.** IMEs redraw on every touch; a single Canvas view with precomputed rects is the lowest-latency, lowest-memory option. Compose would add measurement overhead per key. The app UI (used rarely) gets Compose for speed of development.
2. **Pure-logic core.** `KeyboardLayout`, `Layouts`, `AutoCaps` (and Phase 3's `Trie`/`Suggester`/`GlideTyper`/`BackupCodec`) have zero Android dependencies → fast, deterministic JVM unit tests.
3. **No network, ever.** No `INTERNET` permission; no networking libraries. Privacy is enforced by the manifest, not by policy.
4. **DataStore for settings, Room in Phase 3 for learned words.** Small key-values vs. relational learned dictionary — right tool per job.
5. **IME never steals focus.** Long-press popup is `focusable=false, touchable=false`; slide-to-select is tracked by `KeyboardView` itself.
6. **Defensive `InputConnection` use.** Every call is null-checked and wrapped in `runCatching` — third-party editors misbehave, the keyboard must not crash.

## IME lifecycle & state

- `onCreate` — repo + settings observer (Main dispatcher; DataStore handles IO).
- `onCreateInputView` — builds `SuggestionStrip (wrap) + KeyboardView (wrap)`.
- `onStartInput` — inspects `EditorInfo` (password? numeric? imeAction?).
- `onStartInputView` — resets mode (numeric fields → NUMERIC, else LETTERS), shift (unless caps-lock), enter label, auto-caps.
- `onFinishInput` — clears shift (unless caps-lock).
- `onDestroy` — cancels coroutine scope.

State ownership: `ProtoIME` owns mode/locale/shift/caps; `KeyboardView` owns touch/press/popup rendering state and reports events upward.

## Threading

- IME callbacks run on the main thread; all handlers are O(1)-ish and allocation-light.
- `KeyboardView.onDraw` reuses `Paint`/`RectF` objects; no allocations in the draw path.
- DataStore flows collected on Main (safe: DataStore does IO off-thread).

## Theming

- App: Material 3 dynamic color on API 31+, fallback schemes below; follows `ThemeMode` setting.
- IME: hand-tuned light/dark palettes in `KeyboardView` (system night mode respected in SYSTEM mode).

## Phase 3 insertion points (already prepared)

- `Layouts.layoutFor(locale, mode)` — add `es/de/fr/hi` tables; add subtypes to `res/xml/method.xml`.
- `SuggestionStripView.setSuggestions(words, locale)` — wire to `Suggester`.
- `ProtoIME.onSuggestion` / composing-text handling — wire to `InputSession` + `Trie`.
- `KeyboardView` — add glide-trail overlay + path callback for `GlideTyper`.
- `SettingsRepository` — autocorrect/glide flags already persisted.
- `util/Feedback` — unchanged. `data/` gains `UserDictionary` (Room) + `BackupCodec` (org.json, no new deps).
