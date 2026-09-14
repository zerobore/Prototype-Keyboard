# Architecture

## Components (v0.3.0)

```
┌─ Companion app (Compose + Material 3) ───────────────────┐
│ MainActivity → Setup / Studio / Data / Settings / Test / About│
│ Settings ⇄ SettingsRepository (DataStore)                 │
│ Words ⇄ UserDictionary (Room) · Clips ⇄ ClipboardRepository│
│ Packs ⇄ PluginManager (private files + prefs)             │
│ Studio: pack list + Builder (all 4 kinds) + import/export │
│ Backup: SAF export/import ⇄ BackupCodec (versioned JSON)  │
└─────────────────────────┬────────────────────────────────┘
                          │ same private files
┌─ System keyboard (Views, InputMethodService) ─────────────┤
│ ProtoIME                                                  │
│  ├─ SuggestionStripView (actions + quick-paste + slots + 🛠)│
│  ├─ ViewFlipper: keys / emoji / clips / text-tools boards │
│  ├─ Suggester (Trie + edit-1 autocorrect + bigrams)       │
│  │    words = bundled + enabled dictionary packs + learned│
│  ├─ KeyboardTheme (built-in default or active theme pack) │
│  ├─ GlideTyper (geometric decode, en-only for now)        │
│  ├─ Layouts (en/es/de/fr/hi + symbols + numeric)          │
│  └─ Clipboard auto-capture (ClipboardManager listener)    │
└───────────────────────────────────────────────────────────┘
```

Plugin pipeline: `.pkb` JSON → `PackCodec` (strict validate) →
`PluginManager` (private `files/plugin_packs/`) → IME consumes themes
(`KeyboardTheme.resolve`), dictionary words (`dictionariesFor`), tool chains
(`TextOps.runChain`). Prompt packs stored now; executed by the 3b AI runner.
Full format spec: `docs/PLUGIN_API.md`.

## Key decisions (and why)

1. **Custom `View` for the IME, Compose for the app.** IMEs redraw on every touch; a single Canvas view with precomputed rects is the lowest-latency option.
2. **Pure-logic core.** `KeyboardLayout`, `Layouts`, `AutoCaps`, `Trie`, `Suggester`, `GlideTyper`, `ClipboardStore`, `BackupCodec`, `PackModels`, `PackCodec`, `PluginValidator`, `TextOps`, `parseHexColor`, word helpers — zero Android dependencies → fast deterministic JVM unit tests.
3. **Everything optional.** Every feature (suggestions, autocorrect, glide, learning, capture, quick-paste, haptics, sound, every pack…) has a persisted toggle. Owner house rule.
4. **No network, ever (core).** No network permission; no networking libraries. GIF/cloud-AI need explicit owner approval + permission change (see IDEAS.md).
5. **Conservative autocorrect.** Single-edit corrections only, short words only toward common words, case-preserving, one-backspace revert that learns the original.
6. **Password-field lockdown.** Zero suggestions/learning/autocorrect/glide in password fields — enforced in `ProtoIME`, not by convention.
7. **Sensitive clipboard sections.** Masked until long-press reveal, 10-min auto-expire (unpinned), never exported, never imported-into.
8. **IME never steals focus.** Long-press popup is `focusable=false, touchable=false`; slide-to-select tracked by `KeyboardView` itself.
9. **Defensive `InputConnection` use.** Every call null-checked and `runCatching`-wrapped — third-party editors misbehave; the keyboard must not crash.
10. **kapt for Room (for now).** Zero version-matching risk with Kotlin 1.9.24; KSP migration noted for later.
11. **Packs are data, not code.** `.pkb` JSON validated at import; unknown fields ignored; corrupt packs skipped, never crash. No code loading, no reflection, no WebView.
12. **Tools execute host-side.** Packs declare op chains; `TextOps` runs them (20-step cap, 4 KB input cap, literal replace only — no regex from packs).
13. **Update-by-id.** Reinstalling a pack id replaces it in place; export round-trips byte-faithfully through the codec.
14. **Plugins-first roadmap.** AI (3b) and design (3c) ship as packs: prompt packs already install; theme packs gain animations/SFX extensions.

## IME lifecycle & state

- `onCreate` — repos, plugin preinstall, clipboard listener, settings observer (locale changes rebuild the suggester), plugin-state observer (theme swaps + dictionary rebuilds), background dictionary load.
- `onCreateInputView` — `Strip + ViewFlipper(keys/emoji/clips/tools)`; boards bound with the service scope.
- `onStartInput(View)` — inspects `EditorInfo`, resets mode/board/shift, refreshes suggestions + quick-paste.
- Suggestion pipeline — reads `getTextBeforeCursor(64)` on demand (robust to cursor jumps); completions while typing, bigrams after space.
- `onDestroy` — removes clipboard listener, cancels scope.

## Threading

- IME callbacks on Main; dictionary/DB/pack work on `Dispatchers.Default`/`IO`; DataStore/Room suspend calls never block the UI thread.
- `KeyboardView.onDraw` reuses paints/rects; trail capped at 256 points.
- Glide decode (~800-word scan) runs synchronously on finger-lift; measured scope is trivial (<5 ms class).
- Pack disk scans are small-file reads driven by prefs changes; theme resolution is O(1).

## Theming

- App: Material 3 dynamic color on API 31+, fallback below; follows `ThemeMode`.
- IME: built-in light/dark palettes + theme packs (day/night, radius, per-field fallback). Touch animations, SFX packs, photo backgrounds arrive as pack extensions (Phase 3c).

## Insertion points

- **3b AI:** prompt packs already install via `PluginManager.promptPacks()`; runner renders `{{text}}`/`{{locale}}` and calls on-device model first, cloud only per-action opt-in with EncryptedSharedPreferences key storage.
- **3c Design:** `ThemePack` gains animation/SFX fields (additive, API-compatible); `KeyboardView` paint layer reads them; new `SoundPack` kind follows the same codec pattern.
- **Dictionaries:** dictionary packs per locale (`dictionariesFor(locale)`); glide follows automatically.
