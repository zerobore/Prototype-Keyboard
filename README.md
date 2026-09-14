# Prototype Keyboard ⌨

A private Android system keyboard (IME) + companion app — merging a Gboard-class typing core, CleverType-class AI (hybrid, private by default), Keys-Cafe-class design, and owner-invented features. Built phase by phase, with testing gates before every release.

- **Package:** `com.prototype.keyboard`
- **Stack:** Kotlin, Jetpack Compose + Material 3 (app), custom Views (IME for performance), DataStore, Room
- **Compatibility:** Android 7.0+ (minSdk 24) → latest (targetSdk 34)
- **Privacy:** Offline-first. No network permission. No accounts, ads, analytics, or auto-backup. Cloud features (later) are strictly opt-in per action.
- **House rule:** everything is optional — every feature has a toggle.
- **Plugins:** file-pack platform (.pkb JSON, API v1) — themes, dictionaries, AI prompts, text tools. Build/import/update/share packs in the Studio tab, no coding needed.

## Project status

| Phase | Scope | Status |
|---|---|---|
| 0 · Spec & architecture | Locked with owner (IME + app, Kotlin/Compose, advanced V1, wide compat) | ✅ Done |
| 1 · Codebase + UI scaffold | Project, IME shell, tap typing, settings/test/about screens | ✅ Done |
| 2 · Deep testing (round 1) | Unit + instrumented + manual matrix, `docs/TEST_PLAN.md` | ⏳ Needs CI green + device runs |
| 3a · Smart typing core | Suggestions, autocorrect+revert, next-word, glide, emoji, clipboard sections, es/de/fr/hi, learning, backup/restore | ✅ Built (v0.2.0) |
| 3P · Plugin platform | .pkb packs (theme/dictionary/prompts/tools), Studio + Builder, 5 first-party packs, tools board, theme engine | ✅ Built (this branch, v0.3.0) |
| 3b · AI pillar | Runner for prompt packs: on-device first (ML Kit class) + optional cloud assistant with user's own key (hybrid) | ⏳ Prompts install now; runner specified in `docs/IDEAS.md` |
| 3c · Design pillar | Theme-pack extensions: touch animations, SFX packs, photo backgrounds, more presets | ⏳ Specified in `docs/IDEAS.md` |
| 4 · Security testing | Static + dynamic plan in `docs/SECURITY.md` | ⏳ After 3b/3c |
| 5 · Owner testing + feedback | You test the debug APK, I fix + retest | ⏳ After Phase 4 |
| 6 · Final APK | Release-signed APK/AAB when you approve | ⏳ After Phase 5 |

**Workflow rule:** no phase is marked complete without real verification evidence (CI logs, test reports, device runs). See `docs/TEST_PLAN.md`.

## Quick start

### Option A — Android Studio (recommended for development)
1. Install Android Studio Ladybug+ with JDK 17.
2. Open this folder. Let Gradle sync (needs internet on first sync).
3. Run `app` on a device/emulator (API 24+).
4. In the app: **Setup → Enable → Switch → Test drive**. Then open **Studio** to theme it.

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
  ime/ProtoIME.kt                # InputMethodService: boards, suggestions, glide, clipboard, plugins
  ime/AutoCaps.kt                # Pure auto-caps rule (unit-tested)
  ime/InputSession.kt            # Pure word helpers + revert span (unit-tested)
  keyboard/KeyboardView.kt       # Custom keys + touch + long-press + glide trail + themes
  keyboard/KeyboardTheme.kt      # Resolved themes + hex parser (unit-tested)
  keyboard/SuggestionStripView.kt# Actions + quick-paste chip + live suggestions + tools button
  keyboard/EmojiBoardView.kt     # Offline emoji/kaomoji/text-art board
  keyboard/ClipboardBoardView.kt # Sectioned clipboard board (sensitive masking)
  keyboard/TextToolsBoardView.kt # Text-tools board (runs tool-pack chains)
  keyboard/GlideTyper.kt         # Geometric glide decoder (unit-tested)
  keyboard/KeyboardLayout.kt     # Pure layout model (unit-tested)
  keyboard/Layouts.kt            # en/es/de/fr/hi + symbols + numeric (unit-tested)
  keyboard/EmojiData.kt          # Bundled emoji tables (offline)
  plugins/PluginApi.kt           # API version + compat constants
  plugins/PackModels.kt          # Manifest + 4 payload models + Builder (unit-tested)
  plugins/PackCodec.kt           # Strict .pkb JSON codec (unit-tested)
  plugins/PluginValidator.kt     # Compat rules (unit-tested)
  plugins/PluginManager.kt       # Install/enable/update/export/delete + preinstall
  plugins/TextOps.kt             # 12 text ops + chain executor (unit-tested)
  suggestions/Trie.kt            # Prefix trie (unit-tested)
  suggestions/Suggester.kt       # Completions + autocorrect + bigrams (unit-tested)
  data/SettingsRepository.kt     # Private DataStore settings (everything optional)
  data/ClipboardStore.kt         # Pure sections/clips logic (unit-tested)
  data/ClipboardRepository.kt    # Private JSON persistence
  data/BackupCodec.kt            # Versioned export/import codec (unit-tested)
  data/UserDictionary.kt         # Learned-words repository (Room)
  data/db/AppDatabase.kt         # Room entity/DAO/database
  ui/                            # Compose app: Setup / Studio / Data / Settings / Test / About
  ui/PackBuilderScreen.kt        # In-app pack builder (all 4 kinds)
  util/Feedback.kt               # Haptics + click sound (platform APIs only)
app/src/main/res/raw/            # words_en.txt + 5 first-party .pkb packs
docs/
  ARCHITECTURE.md  TEST_PLAN.md  SECURITY.md  IDEAS.md  PLUGIN_API.md
```

## Documentation
- `docs/ARCHITECTURE.md` — components, IME lifecycle, threading, key decisions
- `docs/TEST_PLAN.md` — unit / instrumented / manual / regression matrices + gates
- `docs/SECURITY.md` — threat model, permissions, privacy, hardening checklist
- `docs/IDEAS.md` — owner ideas backlog (drop new ones in chat anytime)
- `docs/PLUGIN_API.md` — plugin pack format + Builder cookbook (API v1)

## Current limitations (honest list)
- AI prompt packs install, but their runner ships in 3b (on-device first, cloud opt-in).
- Bundled dictionary is English (+ Hinglish pack); es/de/fr/hi suggest from learned words + packs until their dictionaries ship (IDEA-16).
- Glide typing is English-only for the same reason (IDEA-17).
- Cloud AI, GIF search, voice typing need owner decisions (network/mic) — see IDEAS.md.
- Launcher icon is a vector placeholder; adaptive icon ships before Play release.
- No Gradle wrapper jar committed yet (sandbox has no network) — CI installs Gradle 8.9; run `gradle wrapper` once locally to generate it.
