# Prototype Keyboard ⌨

A private Android system keyboard (IME) + companion app — merging a Gboard-class typing core, CleverType-class AI (hybrid, private by default), Keys-Cafe-class design, and owner-invented features. Built phase by phase, with testing gates before every release.

- **Package:** `com.prototype.keyboard`
- **Stack:** Kotlin, Jetpack Compose + Material 3 (app), custom Views (IME for performance), DataStore, Room
- **Compatibility:** Android 7.0+ (minSdk 24) → latest (targetSdk 34)
- **Privacy:** Offline-first. No network permission. No accounts, ads, analytics, or auto-backup. Cloud features (later) are strictly opt-in per action.
- **House rule:** everything is optional — every feature has a toggle.

## Project status

| Phase | Scope | Status |
|---|---|---|
| 0 · Spec & architecture | Locked with owner (IME + app, Kotlin/Compose, advanced V1, wide compat) | ✅ Done |
| 1 · Codebase + UI scaffold | Project, IME shell, tap typing, settings/test/about screens | ✅ Done |
| 2 · Deep testing (round 1) | Unit + instrumented + manual matrix, `docs/TEST_PLAN.md` | ⏳ Needs CI green + device runs |
| 3a · Smart typing core | Suggestions, autocorrect+revert, next-word, glide, emoji, clipboard sections, es/de/fr/hi, learning, backup/restore | ✅ Built (this branch, v0.2.0) |
| 3b · AI pillar | On-device pack + optional cloud assistant (hybrid) | ⏳ Specified in `docs/IDEAS.md` |
| 3c · Design pillar | Touch animations, SFX, DIY theme builder, preset packs | ⏳ Specified in `docs/IDEAS.md` |
| 4 · Security testing | Static + dynamic plan in `docs/SECURITY.md` | ⏳ After 3b/3c |
| 5 · Owner testing + feedback | You test the debug APK, I fix + retest | ⏳ After Phase 4 |
| 6 · Final APK | Release-signed APK/AAB when you approve | ⏳ After Phase 5 |

**Workflow rule:** no phase is marked complete without real verification evidence (CI logs, test reports, device runs). See `docs/TEST_PLAN.md`.

## Quick start

### Option A — Android Studio (recommended for development)
1. Install Android Studio Ladybug+ with JDK 17.
2. Open this folder. Let Gradle sync (needs internet on first sync).
3. Run `app` on a device/emulator (API 24+).
4. In the app: **Setup → Enable → Switch → Test drive**.

Useful Gradle tasks:
```bash
gradle :app:assembleDebug              # debug APK
gradle :app:testDebugUnitTest          # JVM unit tests
gradle :app:connectedDebugAndroidTest   # instrumented tests (needs device/emulator)
gradle :app:lintDebug                  # Android Lint
```

### Option B — CI build (no local setup)
Add `.github/workflows/android-ci.yml` via the GitHub web UI (content held by your dev partner — ask for it), then push: CI builds the debug APK, runs unit tests, runs instrumented tests on an API-30 emulator, runs Lint, and uploads all reports as artifacts.

## Repo map

```
app/src/main/java/com/prototype/keyboard/
  ProtoKeyboardApp.kt            # Application
  ime/ProtoIME.kt                # InputMethodService: boards, suggestions, glide, clipboard
  ime/AutoCaps.kt                # Pure auto-caps rule (unit-tested)
  ime/InputSession.kt            # Pure word helpers + revert span (unit-tested)
  keyboard/KeyboardView.kt       # Custom keys + touch + long-press + glide trail
  keyboard/SuggestionStripView.kt# Actions + quick-paste chip + live suggestions
  keyboard/EmojiBoardView.kt     # Offline emoji/kaomoji/text-art board
  keyboard/ClipboardBoardView.kt # Sectioned clipboard board (sensitive masking)
  keyboard/GlideTyper.kt         # Geometric glide decoder (unit-tested)
  keyboard/KeyboardLayout.kt     # Pure layout model (unit-tested)
  keyboard/Layouts.kt            # en/es/de/fr/hi + symbols + numeric (unit-tested)
  keyboard/EmojiData.kt          # Bundled emoji tables (offline)
  suggestions/Trie.kt            # Prefix trie (unit-tested)
  suggestions/Suggester.kt       # Completions + autocorrect + bigrams (unit-tested)
  data/SettingsRepository.kt     # Private DataStore settings (everything optional)
  data/ClipboardStore.kt         # Pure sections/clips logic (unit-tested)
  data/ClipboardRepository.kt    # Private JSON persistence
  data/BackupCodec.kt            # Versioned export/import codec (unit-tested)
  data/UserDictionary.kt         # Learned-words repository (Room)
  data/db/AppDatabase.kt         # Room entity/DAO/database
  ui/                            # Compose app: Setup / Data / Settings / Test / About
  util/Feedback.kt               # Haptics + click sound (platform APIs only)
docs/
  ARCHITECTURE.md  TEST_PLAN.md  SECURITY.md  IDEAS.md
```

## Documentation
- `docs/ARCHITECTURE.md` — components, IME lifecycle, threading, key decisions
- `docs/TEST_PLAN.md` — unit / instrumented / manual / regression matrices + gates
- `docs/SECURITY.md` — threat model, permissions, privacy, hardening checklist
- `docs/IDEAS.md` — owner ideas backlog (drop new ones in chat anytime)

## Current limitations (honest list)
- Bundled dictionary is English (+ Hinglish chat words); es/de/fr/hi suggest from learned words until their dictionaries ship (IDEA-13).
- Glide typing is English-only for the same reason (IDEA-14).
- Cloud AI, GIF search, voice typing need owner decisions (network/mic) — see IDEAS.md.
- Launcher icon is a vector placeholder; adaptive icon ships before Play release.
- No Gradle wrapper jar committed yet (sandbox has no network) — CI installs Gradle 8.9; run `gradle wrapper` once locally to generate it.
