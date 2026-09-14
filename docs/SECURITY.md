# Security & Privacy

Keyboards see everything typed — this design treats keystroke data as toxic by default.

## Threat model (what we defend against)

1. **Keystroke exfiltration** (network, logs, analytics, crash reports, backups, screenshots).
2. **Over-privileged components** (exported activities/services, confused-deputy intents).
3. **Data remanence** (learned words / clipboard surviving uninstall, leaking via backup or export).
4. **Supply chain** (malicious/typo-squatted dependencies, unsigned builds).
5. **Malicious packs** (crafted .pkb files crashing the IME, smuggling code, or exfiltrating data).

Out of scope: rooted-device keyloggers, OS-level compromise, shoulder surfing.

## Controls in place

| ID | Control | Evidence |
|---|---|---|
| SEC-01 | No network permission | `AndroidManifest.xml` — grep in CI |
| SEC-02 | No analytics/crash/ads SDKs, no WebView | `app/build.gradle.kts` review |
| SEC-03 | `allowBackup=false` + full/device-transfer exclusions | `res/xml/backup_rules.xml`, `data_extraction_rules.xml` |
| SEC-04 | IME service gated by `BIND_INPUT_METHOD`, single exported Activity (launcher only) | Manifest review |
| SEC-05 | No keystroke logging; no keystroke persistence except explicit features below | Code review + `grep -rn "Log\."` |
| SEC-06 | All `InputConnection` calls null-safe + exception-contained | `ProtoIME.kt` review |
| SEC-07 | Long-press popup never focusable (no focus hijack) | `KeyboardView.kt` review |
| SEC-08 | Vibrate-only permission; settings/clips in private DataStore, words in private Room DB | Manifest + code review |
| SEC-09 | Release builds minified (R8) | `app/build.gradle.kts` |
| SEC-10 | Signing keys never committed (`.gitignore`: jks/keystore/apk/aab) | Repo review |
| SEC-11 | Password fields: no suggestions/learning/autocorrect/glide | `ProtoIME` gates + MT-39 |
| SEC-12 | Sensitive clip sections: masked UI, 10-min auto-expire (unpinned) | `ClipboardBoardView`, `ClipboardStore` + UT-49 |
| SEC-13 | Sensitive clips never exported; never imported into sensitive sections | `BackupCodec` + UT-39 + MT-41 |
| SEC-14 | Backup import: strict version/size envelope, per-entry validation + caps | `BackupCodec.parse` + UT-40..42 |
| SEC-15 | Clipboard capture is toggleable; quick-paste requires explicit tap (never auto-paste) | Settings + MT-30/35 |
| SEC-16 | Packs are data-only: strict import validation, unknown fields ignored, no code/reflection/WebView | `PackCodec` + UT-72..81 + MT-48/49 |
| SEC-17 | Tool chains execute host-side with caps (20 steps, 4 KB input, literal replace — no pack regex) | `TextOps` + UT-70/71/99..104 |
| SEC-18 | Corrupt packs on disk skipped, never crash the IME; disabled packs contribute nothing | `PluginManager` + MT-45 |
| SEC-19 | Pack files live in app-private storage; prefs hold only active-theme + disabled set | `PluginManager` review |
| SEC-20 | Pack export is user-initiated via system picker; exports contain only the pack JSON | `StudioScreen` + MT-47 |
| SEC-21 | Compat gate: packs requiring newer API/host are rejected, never partially applied | `PluginValidator` + UT-82..86 |

## Planned (Phase 3b AI + release)

- SEC-22 on-device AI first (ML Kit class: no data leaves the phone).
- SEC-23 cloud AI only with the user's own API key, per-action explicit opt-in, visible "sending" indicator, never in password fields, no background sync. Key stored in EncryptedSecurity `MasterKey` store (security-crypto dep at that time).
- SEC-24 GIF/sticker search needs a network decision + provider privacy review (owner call, IDEA-15).
- SEC-25 release signing: dedicated upload key stored outside repo; `apksigner verify` in CI for the final artifact.
- SEC-26 dependency audit in CI/Studio + version pinning via catalog.
- SEC-27 signed packs (authenticity for shared .pkb files) — platform follow-up (IDEA-22).

## Security testing (runs in the security phase, before owner testing)

- **Static:** Android Lint (security rules) green; manifest/permission diff review; `grep` sweeps for network permission, `Log.`, `http://`, `WebView`, `MODE_WORLD`, cleartext flags, reflection (`Class.forName`, `loadDex`), JS interfaces; dependency list review.
- **Dynamic (device):** airplane-mode full pass (everything except future cloud must work), no keystrokes in logcat, backup rules honored, rotation/kill resilience, export file contains only expected keys (spot-check JSON), sensitive auto-expire timing.
- **Pack adversarial:** import oversized/garbage/wrong-kind/deep-nesting JSON, duplicate ids, 50k-word dictionary, 20-step chains — expect clean rejects or caps, no ANR/crash (MT-48/49 + UT-70/71).
- Results recorded with build hash; failures block the release gate.

## Incident rule
Any finding that keystrokes could leave the device = **P0**: stop the line, fix, add regression test, re-run full matrix before proceeding.
