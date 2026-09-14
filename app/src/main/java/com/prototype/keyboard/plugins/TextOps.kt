package com.prototype.keyboard.plugins

/**
 * Host-side text operations. Tool packs declare chains of these ops;
 * the HOST executes them, so packs stay data-only and safe.
 * Pure Kotlin, unit-tested (TextOpsTest).
 */
object TextOps {

    const val UPPER = "upper"
    const val LOWER = "lower"
    const val TITLE = "title"
    const val SENTENCE = "sentence"
    const val TRIM = "trim"
    const val SQUEEZE = "squeeze"
    const val NO_SPACES = "no_spaces"
    const val PREFIX = "prefix"
    const val SUFFIX = "suffix"
    const val REPLACE = "replace"
    const val REVERSE = "reverse"
    const val UPSIDE_DOWN = "upside_down"

    val ALL = listOf(
        UPPER, LOWER, TITLE, SENTENCE, TRIM, SQUEEZE, NO_SPACES,
        PREFIX, SUFFIX, REPLACE, REVERSE, UPSIDE_DOWN
    )

    const val MAX_IN = 20_000
    const val MAX_OUT = 20_000
    const val MAX_OPS = 20

    fun runChain(input: String, chain: List<TextOp>): String {
        var current = input.take(MAX_IN)
        chain.take(MAX_OPS).forEach { op ->
            current = apply(current, op).take(MAX_OUT)
        }
        return current
    }

    fun apply(input: String, op: TextOp): String = when (op.op) {
        UPPER -> input.uppercase()
        LOWER -> input.lowercase()
        TITLE -> input.split(' ').joinToString(" ") { word ->
            word.lowercase().replaceFirstChar { it.uppercase() }
        }
        SENTENCE -> SENTENCE_REGEX.replace(input) {
            it.groupValues[1] + it.groupValues[2].uppercase()
        }
        TRIM -> input.trim()
        SQUEEZE -> input.trim().replace(WHITESPACE_REGEX, " ")
        NO_SPACES -> input.replace(WHITESPACE_REGEX, "")
        PREFIX -> op.arg1 + input
        SUFFIX -> input + op.arg1
        REPLACE -> if (op.arg1.isEmpty()) input else input.replace(op.arg1, op.arg2)
        REVERSE -> input.reversed()
        UPSIDE_DOWN -> input.reversed().map { FLIP[it] ?: it }.joinToString("")
        else -> input
    }

    private val SENTENCE_REGEX = Regex("(^|[.!?]\\s+)([a-z])")
    private val WHITESPACE_REGEX = Regex("\\s+")

    private val FLIP: Map<Char, Char> = buildMap {
        val lower = "abcdefghijklmnopqrstuvwxyz"
        val lowerFlip = "ɐqɔpǝɟƃɥıɾʞlɯuodbɹsʇnʌʍxʎz"
        lower.forEachIndexed { i, c -> put(c, lowerFlip[i]) }
        put('A', '∀'); put('C', 'Ↄ'); put('E', 'Ǝ'); put('H', 'H'); put('I', 'I')
        put('M', 'W'); put('N', 'N'); put('O', 'O'); put('T', '⊥'); put('U', '∩')
        put('V', 'Λ'); put('W', 'M'); put('X', 'X'); put('Y', '⅄')
        put('.', '˙'); put(',', '\''); put('?', '¿'); put('!', '¡')
        put('(', ')'); put(')', '('); put('[', ']'); put(']', '[')
        put('{', '}'); put('}', '{'); put('<', '>'); put('>', '<')
        put('\'', ','); put('_', '‾')
    }
}
