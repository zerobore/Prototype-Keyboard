# Architecture

## Components (v0.2.0)

```
┌─ Companion app (Compose + Material 3) ───────────────────┐
│ MainActivity → Setup / Data / Settings / Test / About     │
│ Settings ⇄ SettingsRepository (DataStore)                 │
│ Words ⇄ UserDictionary (Room) · Clips ⇄ ClipboardRepository│
│ Backup: SAF export/import ⇄ BackupCodec (versioned JSON)  │
└─────────────────────────┬────────────────────────────────┘
                          │ same private files
┌─ System keyboard (Views, InputMethodService) ─────────────┤
│ ProtoIME                                                  │
│  ├─ SuggestionStripView (actions + quick-paste + slots)   │
│  ├─ ViewFlipper: KeyboardView / EmojiBoard / ClipboardBoard│
│  ├─ Suggester (Trie + edit-1 autocorrect + bigrams)       │
│  ├─ GlideTyper (geometric decode, en-only for now)        │
│  ├─ Layouts (en/es/de/fr/hi + symbols + numeric)          │
│  └─ Clipboard auto-capture (ClipboardManager listener)    │
└───────────────────────────────────────────────────────────┘
```

## Key decisions (and why)

1. **Custom `View` for the IME, Compose for the app.** IMEs redraw on every touch; a single Canvas view with precomputed rects is the lowest-latency option.
2. **Pure-logic core.** `KeyboardLayout`, `Layouts`, `AutoCaps`, `Trie`, `Suggester`, `GlideTyper`, `ClipboardStore`, `BackupCodec`, word helpers — zero Android dependencies → fast deterministic JVM unit tests.
3. **Everything optional.** Every feature (suggestions, autocorrect, glide, learning, capture, quick-paste, haptics, sound…) has a persisted toggle. Owner house rule.
4. **No network, ever (core).** No network permission; no networking libraries. GIF/cloud-AI need explicit owner approval + permission change (see IDEAS.md).
5. **Conservative autocorrect.** Single-edit corrections only, short words only toward common words, case-preserving, one-backspace revert that learns the original.
6. **Password-field lockdown.** Zero suggestions/learning/autocorrect/glide in password fields — enforced in `ProtoIME`, not by convention.
7. **Sensitive clipboard sections.** Masked until long-press reveal, 10-min auto-expire (unpinned), never exported, never imported-into.
8. **IME never steals focus.** Long-press popup is `focusable=false, touchable=false`; slide-to-select tracked by `KeyboardView` itself.
9. **Defensive `InputConnection` use.** Every call null-checked and `runCatching`-wrapped — third-party editors misbehave; the keyboard must not crash.
10. **kapt for Room (for now).** Zero version-matching risk with Kotlin 1.9.24; KSP migration noted for later.

## IME lifecycle & state

- `onCreate` — repos, clipboard listener, settings observer (locale changes rebuild the suggester), background dictionary load.
- `onCreateInputView` — `Strip + ViewFlipper(keys/emoji/clips)`; boards bound with the service scope.
- `onStartInput(View)` — inspects `EditorInfo`, resets mode/board/shift, refreshes suggestions + quick-paste.
- Suggestion pipeline — reads `getTextBeforeCursor(64)` on demand (robust to cursor jumps); completions while typing, bigrams after space.
- `onDestroy` — removes clipboard listener, cancels scope.

## Threading

- IME callbacks on Main; dictionary/DB work on `Dispatchers.Default`; DataStore/Room suspend calls never block the UI thread.
- `KeyboardView.onDraw` reuses paints/rects; trail capped at 256 points.
- Glide decode (~800-word scan) runs synchronously on finger-lift; measured scope is trivial (<5 ms class).

## Theming

- App: Material 3 dynamic color on API 31+, fallback below; follows `ThemeMode`.
- IME: hand-tuned light/dark palettes; full theme engine (DIY builder, animations, SFX) is the Design pillar (Phase 3c).

## Insertion points

- **3b AI:** `ProtoIME.acceptSuggestion` pipeline + strip slots for AI actions; `Suggester` gains ML-Kit/smart-reply sources; cloud path isolated behind per-action opt-in + key storage (EncryptedSharedPreferences).
- **3c Design:** `KeyboardView` paint layer (animations), `Feedback` (SFX packs), `Layouts` untouched, new `ThemeRepository` + builder screens.
- **Dictionaries:** `res/raw/words_XX.txt` + `bundledForLocale()`; glide follows automatically.
