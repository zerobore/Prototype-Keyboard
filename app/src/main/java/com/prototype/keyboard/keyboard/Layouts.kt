package com.prototype.keyboard.keyboard

/**
 * Built-in keyboard layouts.
 *
 * Phase 1: English + symbols + numeric. The [layoutFor] signature already takes
 * a locale so Phase 3 (es/de/fr/hi) slots in without touching callers.
 */
object Layouts {

    private fun ch(
        label: String,
        longPress: List<String> = emptyList(),
        weight: Float = 1f,
    ) = KeySpec(
        code = if (label.isEmpty()) KeyCodes.SPACE else label.codePointAt(0),
        label = label,
        action = KeyAction.CHAR,
        longPress = longPress,
        widthWeight = weight,
    )

    private fun act(
        code: Int,
        label: String,
        action: KeyAction,
        weight: Float = 1f,
    ) = KeySpec(code = code, label = label, action = action, widthWeight = weight)

    fun layoutFor(locale: String, mode: KeyboardMode): KeyboardLayout {
        // `locale` reserved for Phase 3 multi-language. English fallback for now.
        return when (mode) {
            KeyboardMode.LETTERS -> englishLetters()
            KeyboardMode.SYMBOLS -> symbols()
            KeyboardMode.SYMBOLS_MORE -> symbolsMore()
            KeyboardMode.NUMERIC -> numeric()
        }
    }

    fun englishLetters(): KeyboardLayout = KeyboardLayout(
        id = "en_letters",
        locale = "en",
        rows = listOf(
            KeyRow(
                listOf(
                    ch("q"), ch("w"),
                    ch("e", listOf("è", "é", "ê", "ë")),
                    ch("r"), ch("t"),
                    ch("y", listOf("ý", "ÿ")),
                    ch("u", listOf("ù", "ú", "û", "ü")),
                    ch("i", listOf("ì", "í", "î", "ï")),
                    ch("o", listOf("ò", "ó", "ô", "ö", "œ", "ø")),
                    ch("p"),
                )
            ),
            KeyRow(
                listOf(
                    ch("a", listOf("à", "á", "â", "ä", "æ", "ã", "å", "ā")),
                    ch("s", listOf("ß", "ś", "š")),
                    ch("d"), ch("f"), ch("g"), ch("h"), ch("j"), ch("k"),
                    ch("l", listOf("ł")),
                )
            ),
            KeyRow(
                listOf(
                    act(KeyCodes.SHIFT, "⇧", KeyAction.SHIFT, 1.5f),
                    ch("z", listOf("ž", "ź", "ż")),
                    ch("x"),
                    ch("c", listOf("ç", "ć", "č")),
                    ch("v"), ch("b"),
                    ch("n", listOf("ñ", "ń")),
                    ch("m"),
                    act(KeyCodes.DELETE, "⌫", KeyAction.DELETE, 1.5f),
                )
            ),
            KeyRow(
                listOf(
                    act(KeyCodes.MODE_SYMBOLS, "?123", KeyAction.MODE_SYMBOLS, 1.5f),
                    ch(",", listOf(";", ":")),
                    ch(" ", weight = 4f), // space
                    ch(".", listOf("…", "•")),
                    act(KeyCodes.ENTER, "⏎", KeyAction.ENTER, 1.5f),
                )
            ),
        )
    )

    fun symbols(): KeyboardLayout = KeyboardLayout(
        id = "en_symbols",
        locale = "en",
        rows = listOf(
            KeyRow("1234567890".map { ch(it.toString()) }),
            KeyRow(
                listOf(
                    ch("-"), ch("/"), ch(":"), ch(";"),
                    ch("("), ch(")"),
                    ch("$", listOf("€", "£", "¥", "₹", "¢")),
                    ch("&"), ch("@"),
                )
            ),
            KeyRow(
                listOf(
                    act(KeyCodes.MODE_SYMBOLS_MORE, "=\\<", KeyAction.MODE_SYMBOLS_MORE, 1.5f),
                    ch("."), ch(","), ch("?", listOf("¿")), ch("!", listOf("¡")),
                    ch("'", listOf("`", "´", "'")),
                    act(KeyCodes.DELETE, "⌫", KeyAction.DELETE, 1.5f),
                )
            ),
            KeyRow(
                listOf(
                    act(KeyCodes.MODE_LETTERS, "ABC", KeyAction.MODE_LETTERS, 1.5f),
                    ch(","),
                    ch(" ", weight = 4f),
                    ch("."),
                    act(KeyCodes.ENTER, "⏎", KeyAction.ENTER, 1.5f),
                )
            ),
        )
    )

    fun symbolsMore(): KeyboardLayout = KeyboardLayout(
        id = "en_symbols_more",
        locale = "en",
        rows = listOf(
            KeyRow(
                listOf(
                    ch("["), ch("]"), ch("{"), ch("}"), ch("#"),
                    ch("%"), ch("^"), ch("*"), ch("+"), ch("="),
                )
            ),
            KeyRow(
                listOf(
                    ch("_"), ch("\\"), ch("|"), ch("~"), ch("<"),
                    ch(">"), ch("€"), ch("£"), ch("¥"), ch("•"),
                )
            ),
            KeyRow(
                listOf(
                    act(KeyCodes.MODE_SYMBOLS, "123", KeyAction.MODE_SYMBOLS, 1.5f),
                    ch("."), ch(","), ch("?"), ch("!"), ch("'"),
                    act(KeyCodes.DELETE, "⌫", KeyAction.DELETE, 1.5f),
                )
            ),
            KeyRow(
                listOf(
                    act(KeyCodes.MODE_LETTERS, "ABC", KeyAction.MODE_LETTERS, 1.5f),
                    ch(","),
                    ch(" ", weight = 4f),
                    ch("."),
                    act(KeyCodes.ENTER, "⏎", KeyAction.ENTER, 1.5f),
                )
            ),
        )
    )

    /** Compact layout for number / phone / date-time fields. */
    fun numeric(): KeyboardLayout = KeyboardLayout(
        id = "numeric",
        locale = "en",
        rows = listOf(
            KeyRow(listOf(ch("1"), ch("2"), ch("3"))),
            KeyRow(listOf(ch("4"), ch("5"), ch("6"))),
            KeyRow(listOf(ch("7"), ch("8"), ch("9"))),
            KeyRow(
                listOf(
                    ch("+"), ch("0"), ch("."),
                    act(KeyCodes.DELETE, "⌫", KeyAction.DELETE),
                )
            ),
            KeyRow(
                listOf(
                    ch("*"), ch("#"), ch(","),
                    act(KeyCodes.ENTER, "⏎", KeyAction.ENTER),
                )
            ),
        )
    )
}
