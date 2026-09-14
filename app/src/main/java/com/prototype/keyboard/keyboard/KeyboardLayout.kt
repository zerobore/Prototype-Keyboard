package com.prototype.keyboard.keyboard

/**
 * Pure data model describing a keyboard layout. No Android dependencies,
 * so it is fully unit-testable on the JVM.
 */
enum class KeyAction {
    /** Commits [KeySpec.label] (shift-aware for letters). */
    CHAR,
    SHIFT,
    DELETE,
    SPACE,
    ENTER,
    MODE_SYMBOLS,
    MODE_SYMBOLS_MORE,
    MODE_LETTERS,
    /** Phase 3: cycles locales (long-press on space in Phase 1). */
    LANG,
    HIDE,
}

object KeyCodes {
    const val SHIFT = -1
    const val MODE_SYMBOLS = -2
    const val MODE_LETTERS = -3
    const val ENTER = -4
    const val DELETE = -5
    const val MODE_SYMBOLS_MORE = -6
    const val LANG = -7
    const val HIDE = -8
    const val SPACE = 32
}

data class KeySpec(
    /** Unicode code point for [KeyAction.CHAR], otherwise a [KeyCodes] constant. */
    val code: Int,
    /** Base label. Single chars only for CHAR (space is `" "`). */
    val label: String,
    val action: KeyAction = KeyAction.CHAR,
    /** Long-press alternatives, committed on slide-select. */
    val longPress: List<String> = emptyList(),
    /** Relative width within the row. */
    val widthWeight: Float = 1f,
) {
    /** What the key shows when shift/caps is active. */
    fun displayLabel(shifted: Boolean): String {
        if (action != KeyAction.CHAR || !shifted) return label
        return label.uppercase()
    }

    /** What gets committed to the editor, or null for non-character keys. */
    fun commitText(shifted: Boolean): String? {
        if (action != KeyAction.CHAR) return null
        if (label.isEmpty()) return " "
        if (!shifted) return label
        return label.uppercase()
    }
}

data class KeyRow(
    val keys: List<KeySpec>,
    val heightWeight: Float = 1f,
)

data class KeyboardLayout(
    val id: String,
    val locale: String,
    val rows: List<KeyRow>,
)

enum class KeyboardMode {
    LETTERS,
    SYMBOLS,
    SYMBOLS_MORE,
    NUMERIC,
}
