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

Phase 3a (UT-20..UT-58):

| ID | Test | File |
|---|---|---|
| UT-20..24 | Trie insert/contains/ranked completions/limit/clear | TrieTest |
| UT-25..27 | Completions order, user-word priority, isKnown | SuggesterTest |
| UT-28..29 | Autocorrect transpositions + best-candidate pick | SuggesterTest |
| UT-30 | Autocorrect leaves good/hopeless words alone | SuggesterTest |
| UT-31 | Next-word bigrams | SuggesterTest |
| UT-32 | Edit-distance-1 unit cases | SuggesterTest |
| UT-33 | Suggester rebuild swaps dictionaries | SuggesterTest |
| UT-34..37 | Glide resample, hello-decodes-first, anchor filter, degenerate safety | GlideTyperTest |
| UT-38..42 | Backup round-trip, sensitive exclusion, corrupt/version/oversize reject | BackupCodecTest |
| UT-43..50 | Clipboard add/dedupe/pin/auto-route/sections/move/expire/clear | ClipboardStoreTest |
| UT-51..52 | Current-word + previous-word extraction | InputSessionTest |
| UT-53..58 | es ñ, de QWERTZ+ß, fr AZERTY, hi pages, per-locale completeness | LocalesTest |

## Instrumented matrix

| ID | Test | Status |
|---|---|---|
| IT-01 | App launches, Setup visible | ✅ (AppSmokeTest) |
| IT-02 | Settings tab reachable | ✅ (AppSmokeTest) |
| IT-03 | Toggle theme persists across restart | ⏳ Phase 2 |
| IT-04 | Room user-dictionary CRUD (in-memory) | ✅ (UserDictionaryInstrumentedTest) |
| IT-05 | IME service starts, input view non-null | ⏳ Phase 2 (service test) |

## Manual matrix — Phase 1 (MT-01..MT-20, still required every round)

Setup & fields, keys & gestures, settings & robustness — full list in git history
(`docs/TEST_PLAN.md` @ Phase 1). Re-run fully before every APK delivery.

## Manual matrix — Phase 3a (MT-21..MT-42)

Smart typing:
- MT-21 type "hel" → hello/help/hero suggestions; tap one → committed + space
- MT-22 type "teh" + space (autocorrect ON) → "the "; backspace → reverts to "teh"
- MT-23 autocorrect OFF → "teh" stays; learned after space (check Data tab)
- MT-24 after "thank " → "you" predicted; tap → committed
- MT-25 type word with shift (auto-caps) → suggestion commits capitalized
- MT-26 glide "hello" (glide ON, English) → "hello " + alternates in strip; tap alternate replaces
- MT-27 glide garbage path → nearest-letter fallback or nothing (no crash, no junk)
- MT-28 glide OFF → long slides behave like before (final key commits)

Boards & clipboard:
- MT-29 😀 board: each category renders, tap commits emoji/kaomoji; ⌨ returns
- MT-30 copy text in another app → appears in General; quick-paste chip shows; tap pastes
- MT-31 quick-paste toggle OFF → chip hidden; ON → returns (persistent, not one-shot)
- MT-32 copy URL → Links section; copy code block → Codes section
- MT-33 create "Passwords" (sensitive) section in Data tab → masked in board; long-press reveals; tap pastes
- MT-34 pin a clip → floats to top; delete works; section counts update
- MT-35 auto-capture OFF → copies stop landing in the board

Locales & learning:
- MT-36 long-press space cycles en→es→de→fr→hi with toast; layouts render correctly
- MT-37 hi: vowels/consonants commit; ?123 → digits; =\\< → page 2; अ returns
- MT-38 type a new word 3× → learned (Data tab shows ×3); suggests on prefix next time
- MT-39 password field: no suggestions, no learning (Data tab unchanged), autocorrect/glide inert
- MT-40 rotation + dark mode + low-end device: no ANR, keys aligned, boards fit

Backup:
- MT-41 export → file created; import on fresh install → settings/words/clips restored; sensitive section empty
- MT-42 import garbage file → clean "rejected" message, app state untouched

## Regression policy
- Every bug fix must add or extend a test (unit preferred; instrumented/manual if UI-only) and note its ID in the fix commit.
- Before each APK delivery: full unit + lint + instrumented green, plus MT-01..MT-42 re-run on at least one device.

## Gates
- **Phase 1 exit:** CI green (build + UT + Lint + IT-01/02 on emulator), MT-01..MT-20 recorded on ≥1 device.
- **Phase 3a exit:** CI green incl. UT-20..58 + IT-04, MT-01..MT-42 recorded on ≥1 device.
- **Release gate:** all gates + `docs/SECURITY.md` checklist + owner sign-off.
