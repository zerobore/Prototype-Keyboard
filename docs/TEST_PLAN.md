# Test Plan & Quality Gates

**Rule: nothing is marked "tested" without evidence.** Evidence = CI run links, uploaded reports, device run notes with build hash. This file is the contract.

## How to run

| Suite | Command | Where |
|---|---|---|
| Unit (JVM) | `gradle :app:testDebugUnitTest` | Local + CI job `build-unit-lint` |
| Lint | `gradle :app:lintDebug` | Local + CI |
| Instrumented | `gradle :app:connectedDebugAndroidTest` | Emulator (CI: API 30, pixel_6) |
| Manual | Matrices below | Physical devices (see coverage) |

Device coverage target: one low-end API 24–26 device, one mid API 29–31, one modern API 33–34, one foldable or tablet if available, plus rotation + dark mode on each.

## Unit matrix (Phase 1)

| ID | Test | File |
|---|---|---|
| UT-01 | English letters contain a–z | LayoutsTest |
| UT-02 | Symbols layer contains 0–9 | LayoutsTest |
| UT-03 | Numeric layer: digits + delete + enter | LayoutsTest |
| UT-04 | Every layout has ENTER + a way back (no stuck states) | LayoutsTest |
| UT-05 | Rows non-empty, weights positive | LayoutsTest |
| UT-06 | Labels valid; space is the only blank | LayoutsTest |
| UT-07 | Shift uppercases letters, symbols unchanged | LayoutsTest |
| UT-08 | Letter keys unique | LayoutsTest |
| UT-09..12 | Auto-caps rule (empty / terminators / mid-sentence / whitespace) | AutoCapsTest |
| UT-13..14 | Privacy-respecting defaults | SettingsDefaultsTest |

Phase 3 adds: UT-20+ Trie/Suggester (prefix, edit-distance-1, ranking), GlideTyper (resample, decode accuracy on fixtures), BackupCodec (round-trip, version reject, corrupt input), UserDictionary (Room, instrumented).

## Instrumented matrix

| ID | Test | Status |
|---|---|---|
| IT-01 | App launches, Setup visible | ✅ implemented (AppSmokeTest) |
| IT-02 | Settings tab reachable | ✅ implemented |
| IT-03 | Toggle theme persists across restart | ⏳ Phase 2 |
| IT-04 | Room user-dictionary CRUD (in-memory) | ⏳ Phase 3 |
| IT-05 | IME service starts, input view non-null | ⏳ Phase 2 (service test) |

## Manual matrix (Phase 1) — run on each device, record PASS/FAIL + build hash

Setup & fields:
- MT-01 enable → switch → test drive flow
- MT-02 plain text field: type sentence, auto-caps after “. ”
- MT-03 password field: types, no strip suggestions area changes, no crash
- MT-04 email field: “@” reachable via symbols
- MT-05 number/phone field: numeric layout shows, digits + delete + enter work
- MT-06 editor actions: Go/Search/Send/Next/Done labels + behavior in a real app

Keys & gestures:
- MT-07 shift once (single caps), double-tap caps lock, tap to release, long-press shift locks
- MT-08 ?123 → =\\< → ABC round-trip; never stuck
- MT-09 long-press e → slide to é; release-without-slide commits e
- MT-10 delete tap + hold-repeat
- MT-11 double-space → “. ”; does not trigger after existing space/newline
- MT-12 swipe-down-from-space hides keyboard
- MT-13 enter inserts newline in multi-line; performs action when action set

Settings & robustness:
- MT-14 theme System/Light/Dark applies to app + keyboard (toggle system dark too)
- MT-15 key height slider resizes; borders toggle redraws
- MT-16 haptics on/off + strength; sound on/off
- MT-17 rotation while keyboard open; fold/unfold; multi-window (no crash, keys aligned)
- MT-18 rapid typing stress (no ANR, no stuck keys); switch apps mid-compose
- MT-19 kill app, reopen: settings persist; keyboard still enabled
- MT-20 RTL layout direction sanity (supportsRtl=true)

## Regression policy
- Every bug fix must add or extend a test (unit preferred; instrumented/manual if UI-only) and note its ID in the fix commit.
- Before each APK delivery: full unit + lint + instrumented green, plus MT-01..MT-20 re-run on at least one device.

## Gates
- **Phase 1 exit:** CI green (build + UT + Lint + IT-01/02 on emulator), MT-01..MT-20 recorded on ≥1 device.
- **Phase 3 exit:** new UT/IT green, manual smart-engine matrix (separate section to be added) recorded.
- **Release gate:** all gates + `docs/SECURITY.md` checklist + owner sign-off.
