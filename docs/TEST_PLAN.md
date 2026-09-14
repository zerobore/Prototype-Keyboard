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

## Unit matrix

Phase 1 (UT-01..UT-14): layouts, auto-caps, defaults — see git history (still green).

Phase 3a (UT-20..UT-58): Trie/Suggester/Glide/Backup/Clipboard/InputSession/Locales — see git history (still green).

Phase 3P (UT-59..UT-111):

| ID | Test | File |
|---|---|---|
| UT-59..66 | TextOps: case ops, title, sentence, trim/squeeze/no-spaces, prefix/suffix/replace, reverse/upside-down | TextOpsTest |
| UT-67..69 | Chain order, empty chain, unknown-op passthrough | TextOpsTest |
| UT-70..71 | 20-step cap, 4 KB input truncation | TextOpsTest |
| UT-72..76 | Codec: theme round-trip, dict bad-word/empty reject, tools bad-op reject, prompts placeholder reject | PackCodecTest |
| UT-77..81 | Codec: bad id/version/color reject, radius reject on decode, Builder clamps radius | PackCodecTest |
| UT-82..86 | Compat accept, newer-API reject, newer-host reject, wrong-format reject, unknown-kind reject | PackCodecTest |
| UT-87..90 | Theme day/night resolve, per-field fallback, radius clamp | ThemeTest |
| UT-91..92 | Default themes differ, hex parser garbage reject | ThemeTest |
| UT-93..97 | First-party packs decode + spot checks (neon/midnight/hinglish/writer/tools) | FirstPartyPacksTest |
| UT-98 | All first-party packs encode→decode round-trip | FirstPartyPacksTest |
| UT-99..104 | Chain safety: empty-find noop, empty-args noop, 30-step stop, 50-suffix cap, unicode, purity | ToolsBoardBehaviorTest |
| UT-105..106 | SHOUT + quote starter contracts | ToolsBoardBehaviorTest |
| UT-107..110 | Builder flows: theme/dict/prompts/tools build→encode→decode→compatible | PluginFlowTest |
| UT-111 | File contract markers + update-by-id identity (2 tests, one ID) | PluginFlowTest |

## Instrumented matrix

| ID | Test | Status |
|---|---|---|
| IT-01 | App launches, Setup visible | ✅ (AppSmokeTest) |
| IT-02 | Settings tab reachable | ✅ (AppSmokeTest) |
| IT-03 | Toggle theme persists across restart | ⏳ Phase 2 |
| IT-04 | Room user-dictionary CRUD (in-memory) | ✅ (UserDictionaryInstrumentedTest) |
| IT-05 | IME service starts, input view non-null | ⏳ Phase 2 (service test) |
| IT-06 | Studio tab lists 5 preinstalled packs | ⏳ with device runs |

## Manual matrix — Phase 1 (MT-01..MT-20, still required every round)

Setup & fields, keys & gestures, settings & robustness — full list in git history
(`docs/TEST_PLAN.md` @ Phase 1). Re-run fully before every APK delivery.

## Manual matrix — Phase 3a (MT-21..MT-42)

Smart typing, boards/clipboard, locales/learning, backup — full list in git history
(`docs/TEST_PLAN.md` @ v0.2.0). Re-run fully before every APK delivery.

## Manual matrix — Phase 3P (MT-43..MT-54)

Studio & packs:
- MT-43 Studio tab: 5 first-party packs listed under their kinds; platform line shows host + API versions
- MT-44 tap "Neon Nights" theme chip → keys re-theme instantly (day + night); "Default" chip restores
- MT-45 disable Starter Tools → 🛠 board shows empty-hint; re-enable → tools return
- MT-46 delete a pack → gone from list; delete active theme → keyboard falls back to default
- MT-47 export a pack → `.json` file; import it back (rename first) → installs/updates cleanly
- MT-48 import garbage file → clean "Rejected: …" message, installed packs untouched
- MT-49 import pack with `pluginApiVersion: 99` → rejected with compat message

Builder:
- MT-50 build a theme (weird colors + radius 0/16) → saves, previews, applies from Studio
- MT-51 build a dictionary (paste words incl. invalid lines) → valid count shown; suggestions include new words
- MT-52 build a tool pack (2 tools, multi-step chains) → Try-it previews; 🛠 board runs them on selection + on current word
- MT-53 build a prompt pack → installs and lists (runner message shown; execution is 3b)
- MT-54 rotation + dark mode + low-end device: Studio scrolls, no ANR, IME unaffected

## Regression policy
- Every bug fix must add or extend a test (unit preferred; instrumented/manual if UI-only) and note its ID in the fix commit.
- Before each APK delivery: full unit + lint + instrumented green, plus MT-01..MT-54 re-run on at least one device.

## Gates
- **Phase 1 exit:** CI green (build + UT + Lint + IT-01/02 on emulator), MT-01..MT-20 recorded on ≥1 device.
- **Phase 3a exit:** CI green incl. UT-20..58 + IT-04, MT-01..MT-42 recorded on ≥1 device.
- **Phase 3P exit:** CI green incl. UT-59..111, MT-01..MT-54 recorded on ≥1 device, static verify script all-pass.
- **Release gate:** all gates + `docs/SECURITY.md` checklist + owner sign-off.
