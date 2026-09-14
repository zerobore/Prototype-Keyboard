package com.prototype.keyboard.ime

/**
 * Pure word-extraction helpers for the suggestion pipeline.
 * Unit-tested in InputSessionTest.
 */

/** Trailing run of letters/apostrophes (the word being typed). */
fun extractCurrentWord(textBeforeCursor: CharSequence): String {
    var i = textBeforeCursor.length - 1
    while (i >= 0 && (textBeforeCursor[i].isLetter() || textBeforeCursor[i] == '\'')) {
        i--
    }
    return textBeforeCursor.substring(i + 1).toString()
}

/**
 * Last full word before trailing non-letters. Used for next-word prediction
 * when the cursor sits after a space/punctuation.
 */
fun previousWord(textBeforeCursor: CharSequence): String {
    var i = textBeforeCursor.length - 1
    while (i >= 0 && !textBeforeCursor[i].isLetter()) {
        i--
    }
    if (i < 0) return ""
    val end = i + 1
    while (i >= 0 && (textBeforeCursor[i].isLetter() || textBeforeCursor[i] == '\'')) {
        i--
    }
    return textBeforeCursor.substring(i + 1, end).toString()
}

/** Remembers an applied autocorrection so one backspace can revert it. */
data class AutocorrectSpan(val original: String, val corrected: String)
