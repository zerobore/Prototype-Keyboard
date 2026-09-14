# Security & Privacy

Keyboards see everything typed — this design treats keystroke data as toxic by default.

## Threat model (what we defend against)

1. **Keystroke exfiltration** (network, logs, analytics, crash reports, backups, screenshots).
2. **Over-privileged components** (exported activities/services, confused-deputy intents).
3. **Data remanence** (learned words / clipboard surviving uninstall, leaking via backup or export).
4. **Supply chain** (malicious/typo-squatted dependencies, unsigned builds).

Out of scope: rooted-device keyloggers, OS-level compromise, shoulder surfing.

## Controls in place (Phase 1)

| ID | Control | Evidence |
|---|---|---|
| SEC-01 | No `INTERNET` (or any network) permission | `AndroidManifest.xml` — grep in CI |
| SEC-02 | No analytics/crash/ads SDKs, no WebView | `app/build.gradle.kts` review |
| SEC-03 | `allowBackup=false` + full/device-transfer exclusions | `res/xml/backup_rules.xml`, `data_extraction_rules.xml` |
| SEC-04 | IME service gated by `BIND_INPUT_METHOD`, single exported Activity (launcher only) | Manifest review |
| SEC-05 | No `Log.d` of keystrokes; no keystroke persistence in Phase 1 | Code review + `grep -rn "Log\."` |
| SEC-06 | All `InputConnection` calls null-safe + exception-contained | `ProtoIME.kt` review |
| SEC-07 | Long-press popup never focusable (no focus hijack) | `KeyboardView.kt` review |
| SEC-08 | Vibrate-only permission; settings stored in private DataStore | Manifest + code review |
| SEC-09 | Release builds minified (R8) | `app/build.gradle.kts` |
| SEC-10 | Signing keys never committed (`.gitignore`: jks/keystore/apk/aab) | Repo review |

## Phase 3 additions (planned)

- SEC-11 clipboard history: in-memory first, redaction of password-field copies (never stored), auto-expire, user wipe.
- SEC-12 learned dictionary: Room, private, per-app; no learning in password/incognito fields; user wipe + per-word delete.
- SEC-13 backup/restore: explicit user export via Storage Access Framework only; versioned JSON, strict parser (reject unknown versions, cap sizes); import requires explicit user action; file never auto-created.
- SEC-14 release signing: dedicated upload key, stored outside repo; `apksigner verify` in CI for the final artifact.
- SEC-15 dependency audit: `gradle dependencyCheck`? (no network in sandbox — run in CI/Studio) + version pinning via catalog.

## Security testing (runs in the security phase, before owner testing)

- **Static:** Android Lint (security rules) green; manifest/permission diff review; `grep` sweeps for `INTERNET`, `Log.`, `http://`, `WebView`, `MODE_WORLD`, cleartext flags; dependency list review.
- **Dynamic (device):** verify no traffic (offline — airplane-mode + proxy check), no keystrokes in logcat, backup rules honored (`adb backup` refuses / empty), rotation/kill resilience, export file contains only expected keys.
- Results recorded with build hash; failures block the release gate.

## Incident rule
Any finding that keystrokes could leave the device = **P0**: stop the line, fix, add regression test, re-run full matrix before proceeding.
