package com.prototype.keyboard.plugins

/** All colors are #RRGGBB. */
data class ThemeColors(
    val background: String,
    val key: String,
    val funcKey: String,
    val pressedKey: String,
    val text: String,
    val accent: String,
)

/** v1 themes are color + radius. v2 (design pillar) adds backgrounds/effects/sfx. */
data class ThemePack(
    val manifest: PluginManifest,
    val day: ThemeColors,
    val night: ThemeColors,
    val keyRadiusDp: Int = 6,
)

/** Extra suggestion words for one locale (merged under user words). */
data class DictionaryPack(
    val manifest: PluginManifest,
    val locale: String,
    val words: List<String>,
)

/** One AI prompt template. {{text}} = selected/current text, {{locale}} = keyboard locale. */
data class AiPrompt(
    val id: String,
    val title: String,
    val template: String,
    val description: String = "",
)

/** Prompt packs install now; the AI runner that executes them lands in Phase 3b. */
data class PromptPack(
    val manifest: PluginManifest,
    val prompts: List<AiPrompt>,
)

/** One declarative operation. Unknown op names are rejected at import. */
data class TextOp(
    val op: String,
    val arg1: String = "",
    val arg2: String = "",
)

/** A tool = ordered chain of ops, executed host-side (packs never run code). */
data class TextTool(
    val id: String,
    val title: String,
    val icon: String = "\uD83D\uDEE0",
    val chain: List<TextOp>,
)

data class TextToolPack(
    val manifest: PluginManifest,
    val tools: List<TextTool>,
)
