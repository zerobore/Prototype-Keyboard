# Prototype Keyboard — Plugin API v1

Host: **0.3.0** · `PLUGIN_API_VERSION = 1` · `SUPPORTED_API = 1..1`

## 1. What a plugin is

A plugin is a **.pkb file**: strict JSON (`{"format":"pkb","formatVersion":1,...}`),
data-only, no code. Four kinds: `theme`, `dictionary`, `prompts`, `tools`.
Packs install from a file, enable/disable individually, update by reinstalling
the same id, and export back to a shareable file. Everything is optional:
with zero packs installed the keyboard is 100% functional.

Safety model: every pack is validated at import ([PackCodec]) and rejected
with a human message on any violation. Unknown JSON fields are ignored
(forward-compatible reads). Corrupt packs on disk are skipped, never crash
the IME. Tool chains execute host-side via [TextOps] (20-step cap, 4 KB
input cap) — packs never run code.

## 2. Manifest (every pack)

```json
"manifest": {
  "id": "com.you.theme.sunset",
  "name": "Sunset",
  "author": "You",
  "version": "1.0.0",
  "pluginApiVersion": 1,
  "minHostVersion": "0.3.0",
  "kind": "theme",
  "description": "Warm evening colors."
}
```

| Field | Rule |
|---|---|
| `id` | 3–64 chars, `[A-Za-z0-9._-]`, reverse-dns style. Same id reinstall = update in place |
| `name` / `author` | 1–48 chars |
| `version` | `x.y.z` semver |
| `pluginApiVersion` | integer, must be in host `SUPPORTED_API` |
| `minHostVersion` | semver; host version must be >= it |
| `kind` | `theme` · `dictionary` · `prompts` · `tools` |
| `description` | ≤ 280 chars, may be empty |

Compatibility: `api in SUPPORTED_API && hostVersion >= minHostVersion`.
Semver compares numerically per part (`0.3.0 < 0.10.0`).

## 3. Theme packs

```json
"payload": {
  "day":   { "background": "#D8DCE3", "key": "#FFFFFF", "funcKey": "#B9C0CB",
             "pressedKey": "#9FB4D8", "text": "#1F1F1F", "accent": "#0B57D0" },
  "night": { "background": "#0A0E1A", "key": "#16213E", "funcKey": "#0F3460",
             "pressedKey": "#E94560", "text": "#FFFFFF", "accent": "#00FFC8" },
  "keyRadiusDp": 8
}
```

Colors are `#RRGGBB` (alpha `#AARRGGBB` also accepted at render).
`keyRadiusDp` 0–16. One theme is active at a time (or the built-in default);
day/night follows the app's theme mode. Bad colors fall back per-field.

## 4. Dictionary packs

```json
"payload": { "locale": "en", "words": ["achha", "shukriya", "yaar"] }
```

`locale`: `en|es|de|fr|hi`. 1–50,000 words, each 2–32 chars of `[a-z']`
(lowercase latin; other scripts arrive with their locales). Words merge with
the bundled + learned dictionaries for suggestions/autocorrect/glide decoding.

## 5. Prompt packs (AI, runner in 3b)

```json
"payload": { "prompts": [
  { "id": "fix-grammar", "title": "Fix grammar",
    "template": "Fix the grammar of this text. Reply with only the corrected text:\n\n{{text}}",
    "description": "Correct mistakes, keep meaning." }
]}
```

1–50 prompts per pack. `id` 1–32 (`[a-z0-9-]`), `title` 1–48,
`template` 1–4000 chars and **must contain `{{text}}`** (may also use
`{{locale}}`), `description` ≤ 140. Templates render host-side; the model
runner (on-device first, cloud optional) lands in Phase 3b.

## 6. Tool packs

Tools run on the selected text, else the current word.

```json
"payload": { "tools": [
  { "id": "shout", "title": "SHOUT", "icon": "📢",
    "chain": [ {"op": "trim"}, {"op": "upper"}, {"op": "suffix", "arg1": "!!!"} ] }
]}
```

1–50 tools per pack, 1–20 steps per chain. `id` 1–32, `title` 1–32,
`icon` ≤ 8 chars.

### Text ops

| op | args | effect |
|---|---|---|
| `upper` / `lower` | — | case |
| `title` | — | Title Case Every Word |
| `sentence` | — | Capitalize sentence starts |
| `trim` / `squeeze` | — | edge whitespace / collapse inner runs |
| `no_spaces` | — | delete all whitespace |
| `prefix` / `suffix` | `arg1` (≤200) | add text |
| `replace` | `arg1` find, `arg2` (≤200 each) | literal replace-all |
| `reverse` | — | reverse characters |
| `upside_down` | — | Upside-down map (best-effort) |

Unknown ops are rejected at import. Empty chains rejected.

## 7. Host behaviors

- **Preinstall:** 5 first-party packs ship in `res/raw` (Neon Nights,
  Midnight Blue, Hinglish Essentials, Writer's Assistant, Starter Tools),
  copied to private storage on first run. Deletable like any pack.
- **Enable:** per-pack toggle; disabled packs contribute nothing.
- **Update:** import a pack with an existing id → replaced in place.
- **Export:** any installed pack → `.json` file via the system picker.
- **Delete:** removes the file; active-theme selection resets if needed.
- **Storage:** app-private `files/plugin_packs/`; prefs hold only
  `active_theme` + `disabled_packs`. Included in app backup as normal files.

## 8. Builder cookbook (in-app Studio → New pack)

1. Fill manifest (id/name/author/version/description).
2. **Theme:** pick 12 colors (live validity dots) → radius slider →
   day/night preview → Save. **Dictionary:** pick locale → paste one word
   per line → live valid/skipped counts → Save. **Prompts:** title +
   template with `{{text}}` (+ `+ Add prompt`) → Save. **Tools:** title +
   icon → `+ Add step` chain (args appear when needed) → Try-it box → Save.
3. Validation errors appear inline ("Fix: …"); success installs immediately
   and returns to the pack list.

## 9. API evolution

- v1 is additive-only: new ops / fields / kinds get minor host bumps;
  breaking changes bump `PLUGIN_API_VERSION` and extend `SUPPORTED_API`
  (old packs keep working).
- Planned (not in v1): sticker/image packs, sound packs, layout packs,
  signed packs, pack store/sharing intents.
