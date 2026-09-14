package com.prototype.keyboard.keyboard

/**
 * Built-in keyboard layouts: en/es/de/fr/hi + symbols + numeric.
 * Pure data; unit-tested (LayoutsTest, LocalesTest).
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
        return when (mode) {
            KeyboardMode.LETTERS -> when (locale) {
                "es" -> spanishLetters()
                "de" -> germanLetters()
                "fr" -> frenchLetters()
                "hi" -> hindiLetters()
                else -> englishLetters()
            }
            KeyboardMode.SYMBOLS -> symbols(if (locale == "hi") "अ" else "ABC")
            KeyboardMode.SYMBOLS_MORE -> if (locale == "hi") hindiMore() else symbolsMore()
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

    fun spanishLetters(): KeyboardLayout = KeyboardLayout(
        id = "es_letters",
        locale = "es",
        rows = listOf(
            KeyRow(
                listOf(
                    ch("q"), ch("w"),
                    ch("e", listOf("é", "è", "ê", "ë")),
                    ch("r"), ch("t"), ch("y"),
                    ch("u", listOf("ú", "ü", "ù", "û")),
                    ch("i", listOf("í", "ì", "î", "ï")),
                    ch("o", listOf("ó", "ò", "ô", "ö")),
                    ch("p"),
                )
            ),
            KeyRow(
                listOf(
                    ch("a", listOf("á", "à", "ä", "â")),
                    ch("s"), ch("d"), ch("f"), ch("g"), ch("h"), ch("j"), ch("k"),
                    ch("l"), ch("ñ"),
                )
            ),
            KeyRow(
                listOf(
                    act(KeyCodes.SHIFT, "⇧", KeyAction.SHIFT, 1.5f),
                    ch("z"), ch("x"), ch("c", listOf("ç")), ch("v"), ch("b"),
                    ch("n", listOf("ñ")),
                    ch("m"),
                    act(KeyCodes.DELETE, "⌫", KeyAction.DELETE, 1.5f),
                )
            ),
            KeyRow(
                listOf(
                    act(KeyCodes.MODE_SYMBOLS, "?123", KeyAction.MODE_SYMBOLS, 1.5f),
                    ch(",", listOf(";", ":")),
                    ch(" ", weight = 4f),
                    ch(".", listOf("…", "•")),
                    act(KeyCodes.ENTER, "⏎", KeyAction.ENTER, 1.5f),
                )
            ),
        )
    )

    /** German QWERTZ with umlaut long-presses. */
    fun germanLetters(): KeyboardLayout = KeyboardLayout(
        id = "de_letters",
        locale = "de",
        rows = listOf(
            KeyRow(
                listOf(
                    ch("q"), ch("w"),
                    ch("e", listOf("ë", "ê", "é", "è")),
                    ch("r"), ch("t"), ch("z"),
                    ch("u", listOf("ü", "û", "ù", "ú")),
                    ch("i", listOf("î", "ï", "í", "ì")),
                    ch("o", listOf("ö", "ô", "ò", "ó")),
                    ch("p"),
                )
            ),
            KeyRow(
                listOf(
                    ch("a", listOf("ä", "â", "à", "á")),
                    ch("s", listOf("ß", "ś", "š")),
                    ch("d"), ch("f"), ch("g"), ch("h"), ch("j"), ch("k"),
                    ch("l"),
                )
            ),
            KeyRow(
                listOf(
                    act(KeyCodes.SHIFT, "⇧", KeyAction.SHIFT, 1.5f),
                    ch("y"), ch("x"), ch("c", listOf("ç")), ch("v"), ch("b"),
                    ch("n"), ch("m"),
                    act(KeyCodes.DELETE, "⌫", KeyAction.DELETE, 1.5f),
                )
            ),
            KeyRow(
                listOf(
                    act(KeyCodes.MODE_SYMBOLS, "?123", KeyAction.MODE_SYMBOLS, 1.5f),
                    ch(",", listOf(";", ":")),
                    ch(" ", weight = 4f),
                    ch(".", listOf("…", "•")),
                    act(KeyCodes.ENTER, "⏎", KeyAction.ENTER, 1.5f),
                )
            ),
        )
    )

    /** French AZERTY with accent long-presses. */
    fun frenchLetters(): KeyboardLayout = KeyboardLayout(
        id = "fr_letters",
        locale = "fr",
        rows = listOf(
            KeyRow(
                listOf(
                    ch("a", listOf("à", "â", "æ")),
                    ch("z"),
                    ch("e", listOf("é", "è", "ê", "ë")),
                    ch("r"), ch("t"), ch("y"),
                    ch("u", listOf("ù", "û", "ü")),
                    ch("i", listOf("î", "ï", "í")),
                    ch("o", listOf("ô", "œ", "ò")),
                    ch("p"),
                )
            ),
            KeyRow(
                listOf(
                    ch("q"), ch("s"), ch("d"), ch("f"), ch("g"), ch("h"), ch("j"),
                    ch("k"), ch("l"), ch("m"),
                )
            ),
            KeyRow(
                listOf(
                    act(KeyCodes.SHIFT, "⇧", KeyAction.SHIFT, 1.5f),
                    ch("w"), ch("x"),
                    ch("c", listOf("ç", "ć", "č")),
                    ch("v"), ch("b"), ch("n"),
                    ch(",", listOf(";", ":")),
                    ch("?", listOf("!")),
                    act(KeyCodes.DELETE, "⌫", KeyAction.DELETE, 1.5f),
                )
            ),
            KeyRow(
                listOf(
                    act(KeyCodes.MODE_SYMBOLS, "?123", KeyAction.MODE_SYMBOLS, 1.5f),
                    ch(",", listOf(";", ":")),
                    ch(" ", weight = 4f),
                    ch(".", listOf("…", "•")),
                    act(KeyCodes.ENTER, "⏎", KeyAction.ENTER, 1.5f),
                )
            ),
        )
    )

    /**
     * Hindi (Devanagari) page 1: vowels + first consonant block.
     * Vowel long-presses give matras; page 2 lives in [hindiMore].
     * Prototype layout — will be refined with native-speaker feedback.
     */
    fun hindiLetters(): KeyboardLayout = KeyboardLayout(
        id = "hi_letters",
        locale = "hi",
        rows = listOf(
            KeyRow(
                listOf(
                    ch("अ", listOf("अं", "अः")),
                    ch("आ", listOf("ा")),
                    ch("इ", listOf("ि")),
                    ch("ई", listOf("ी")),
                    ch("उ", listOf("ु")),
                    ch("ऊ", listOf("ू")),
                    ch("ए", listOf("े")),
                    ch("ऐ", listOf("ै")),
                    ch("ओ", listOf("ो")),
                    ch("औ", listOf("ौ")),
                )
            ),
            KeyRow(
                listOf(
                    ch("क"), ch("ख"), ch("ग"), ch("घ"), ch("ङ"),
                    ch("च"), ch("छ"), ch("ज"), ch("झ"), ch("ञ"),
                )
            ),
            KeyRow(
                listOf(
                    act(KeyCodes.SHIFT, "⇧", KeyAction.SHIFT, 1.5f),
                    ch("ट"), ch("ठ"), ch("ड"), ch("ढ"), ch("ण"),
                    ch("त"), ch("थ"), ch("द"), ch("ध"),
                    act(KeyCodes.DELETE, "⌫", KeyAction.DELETE, 1.5f),
                )
            ),
            KeyRow(
                listOf(
                    act(KeyCodes.MODE_SYMBOLS, "?123", KeyAction.MODE_SYMBOLS, 1.5f),
                    ch(","),
                    ch(" ", weight = 4f),
                    ch(".", listOf("।", "…")),
                    act(KeyCodes.ENTER, "⏎", KeyAction.ENTER, 1.5f),
                )
            ),
        )
    )

    /** Hindi page 2 (reached via =\\< from the symbols page). */
    fun hindiMore(): KeyboardLayout = KeyboardLayout(
        id = "hi_more",
        locale = "hi",
        rows = listOf(
            KeyRow(
                listOf(
                    ch("न"), ch("प"), ch("फ"), ch("ब"), ch("भ"),
                    ch("म"), ch("य"), ch("र"), ch("ल"), ch("व"),
                )
            ),
            KeyRow(
                listOf(
                    ch("श"), ch("ष"), ch("स"), ch("ह"),
                    ch("ऋ", listOf("ृ")),
                    ch("्"), ch("ं"), ch("ः"), ch("ज्ञ"), ch("ळ"),
                )
            ),
            KeyRow(
                listOf(
                    act(KeyCodes.MODE_SYMBOLS, "123", KeyAction.MODE_SYMBOLS, 1.5f),
                    ch("ा"), ch("ि"), ch("ी"), ch("ु"), ch("ू"),
                    act(KeyCodes.DELETE, "⌫", KeyAction.DELETE, 1.5f),
                )
            ),
            KeyRow(
                listOf(
                    act(KeyCodes.MODE_LETTERS, "अ", KeyAction.MODE_LETTERS, 1.5f),
                    ch(","),
                    ch(" ", weight = 4f),
                    ch("."),
                    act(KeyCodes.ENTER, "⏎", KeyAction.ENTER, 1.5f),
                )
            ),
        )
    )

    fun symbols(lettersLabel: String = "ABC"): KeyboardLayout = KeyboardLayout(
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
                    act(KeyCodes.MODE_LETTERS, lettersLabel, KeyAction.MODE_LETTERS, 1.5f),
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
