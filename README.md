# Prototype Keyboard ⌨

A private, offline-first Android system keyboard (IME) + companion app — built in the open, phase by phase, with testing gates before every release.

- **Package:** `com.prototype.keyboard`
- **Stack:** Kotlin, Jetpack Compose + Material 3 (app), custom Views (IME for performance), DataStore, Room (Phase 3)
- **Compatibility:** Android 7.0+ (minSdk 24) → latest (targetSdk 34)
- **Privacy:** 100% offline. No `INTERNET` permission. No accounts, ads, analytics, or auto-backup.

## Project status

| Phase | Scope | Status |
|---|---|---|
| 0 · Spec & architecture | Locked with owner (IME + app, Kotlin/Compose, advanced V1, wide compat) | ✅ Done |
| 1 · Codebase + UI scaffold | Project, IME shell, tap typing, settings/test/about screens, CI | ✅ In review (this branch) |
| 2 · Deep feature + regression testing | Unit + instrumented + manual matrix in `docs/TEST_PLAN.md` | ⏳ After CI is green |
| 3 · Smart engine | Suggestions, autocorrect, glide, emoji, clipboard, es/de/fr/hi, backup/restore | ⏳ Next build |
| 4 · Security testing | Static + dynamic plan in `docs/SECURITY.md` | ⏳ After Phase 3 |
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
gradle :app:assembleDebug            # debug APK
gradle :app:testDebugUnitTest        # JVM unit tests
gradle :app:connectedDebugAndroidTest # instrumented tests (needs device/emulator)
gradle :app:lintDebug                # Android Lint
```

### Option B — CI build (no local setup)
Push to any branch (or open a PR): `.github/workflows/android-ci.yml` builds the debug APK, runs unit tests on JVM, runs instrumented tests on an API-30 emulator, runs Lint, and uploads all reports as artifacts.

## Repo map

```
app/src/main/java/com/prototype/keyboard/
  ProtoKeyboardApp.kt        # Application
  ime/ProtoIME.kt            # InputMethodService: typing, fields, editor actions
  ime/AutoCaps.kt            # Pure auto-caps rule (unit-tested)
  keyboard/KeyboardView.kt   # Custom key rendering + touch + long-press + gestures
  keyboard/SuggestionStripView.kt  # Strip above keys (smart slots land in Phase 3)
  keyboard/KeyboardLayout.kt # Pure layout model (unit-tested)
  keyboard/Layouts.kt        # en/symbols/numeric layouts (es/de/fr/hi in Phase 3)
  data/SettingsRepository.kt # DataStore settings (private, on-device)
  ui/                        # Compose app: Setup / Settings / Test / About
  util/Feedback.kt           # Haptics + click sound (platform APIs only)
docs/
  ARCHITECTURE.md  TEST_PLAN.md  SECURITY.md
```

## Documentation
- `docs/ARCHITECTURE.md` — components, IME lifecycle, threading, key decisions
- `docs/TEST_PLAN.md` — unit / instrumented / manual / regression matrices + gates
- `docs/SECURITY.md` — threat model, permissions, privacy, hardening checklist

## Current limitations (honest list)
- English only; suggestions/autocorrect/glide/emoji/clipboard/backup arrive in Phase 3.
- Toggles for autocorrect/glide persist but take effect in Phase 3 (labeled in UI).
- Launcher icon is a vector placeholder; adaptive icon ships before Play release.
- No Gradle wrapper jar committed yet (sandbox has no network) — CI installs Gradle 8.9; run `gradle wrapper` once locally to generate it.
