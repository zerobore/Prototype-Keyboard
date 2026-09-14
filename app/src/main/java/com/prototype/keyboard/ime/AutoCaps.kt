package com.prototype.keyboard.ime

/**
 * Pure auto-capitalization rule: shift the next character when the cursor is at
 * the start of a sentence (empty field, or sentence terminator followed only by
 * whitespace/closing quotes). Unit-tested in AutoCapsTest.
 */
fun shouldAutoCapitalize(textBeforeCursor: CharSequence): Boolean {
    var i = textBeforeCursor.length - 1
    while (i >= 0) {
        val c = textBeforeCursor[i]
        if (c.isWhitespace() || c == '"' || c == '\'' || c == '“' || c == '”' ||
            c == '‘' || c == '’' || c == '(' || c == '['
        ) {
            i--
        } else {
            break
        }
    }
    if (i < 0) return true
    val last = textBeforeCursor[i]
    return last == '.' || last == '!' || last == '?' || last == '…'
}
