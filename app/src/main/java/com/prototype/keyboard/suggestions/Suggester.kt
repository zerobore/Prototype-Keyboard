package com.prototype.keyboard.suggestions

import kotlin.math.abs

/**
 * On-device suggestion engine: prefix completions, edit-distance-1 autocorrect
 * and tiny next-word prediction. Pure Kotlin, fully unit-tested (SuggesterTest).
 *
 * Ranking: bundled words rank by list position (0 = most common); learned user
 * words always outrank bundled words, ordered by (-frequency, word).
 */
class Suggester(
    bundled: List<String>,
    userWords: Map<String, Int> = emptyMap(),
) {

    private val trie = Trie()
    private val ranks = HashMap<String, Int>()

    init {
        rebuild(bundled, userWords)
    }

    fun rebuild(bundled: List<String>, userWords: Map<String, Int>) {
        trie.clear()
        ranks.clear()
        bundled.forEachIndexed { index, raw ->
            val word = raw.trim().lowercase()
            if (isIndexable(word) && !ranks.containsKey(word)) {
                ranks[word] = index
            }
        }
        userWords.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .forEachIndexed { order, entry ->
                val word = entry.key.trim().lowercase()
                if (isIndexable(word)) {
                    // Negative ranks: user words always win, stable order.
                    ranks[word] = USER_RANK_BASE - order
                }
            }
        ranks.forEach { (word, rank) -> trie.insert(word, rank) }
    }

    fun isKnown(word: String): Boolean = trie.contains(word.lowercase())

    fun dictionarySize(): Int = trie.size

    fun completions(prefix: String, limit: Int = 3): List<String> {
        val clean = prefix.trim().lowercase()
        if (clean.isEmpty() || !isIndexable(clean)) return emptyList()
        return trie.completions(clean, limit)
    }

    /**
     * Returns a correction for [word], or null when the word looks fine.
     * Conservative by design: only single-edit corrections, and short words
     * only correct toward very common words.
     */
    fun autocorrect(word: String): String? {
        val clean = word.trim().lowercase()
        if (clean.length < 3 || clean.length > 14) return null
        if (!isIndexable(clean)) return null
        if (isKnown(clean)) return null
        var best: String? = null
        var bestRank = Int.MAX_VALUE
        for ((candidate, rank) in ranks) {
            if (abs(candidate.length - clean.length) > 1) continue
            if (!isEditDistanceOne(clean, candidate)) continue
            if (rank < bestRank) {
                bestRank = rank
                best = candidate
            }
        }
        if (best != null && clean.length <= 4 && bestRank > SHORT_WORD_MAX_RANK) return null
        return best
    }

    fun nextWords(previous: String, limit: Int = 3): List<String> =
        BIGRAMS[previous.trim().lowercase()]?.take(limit).orEmpty()

    companion object {
        private const val USER_RANK_BASE = -1_000_000
        private const val SHORT_WORD_MAX_RANK = 300

        fun isIndexable(word: String): Boolean =
            word.isNotEmpty() && word.all { it in 'a'..'z' || it == '\'' }

        /**
         * True when [a] and [b] differ by exactly one substitution, transposition,
         * insertion or deletion.
         */
        fun isEditDistanceOne(a: String, b: String): Boolean {
            if (a == b) return false
            val n = a.length
            val m = b.length
            if (abs(n - m) > 1) return false
            if (n == m) {
                var diff = 0
                var first = -1
                for (i in 0 until n) {
                    if (a[i] != b[i]) {
                        diff++
                        if (first == -1) first = i
                        if (diff > 2) return false
                    }
                }
                if (diff == 1) return true
                if (diff == 2) {
                    val i = first
                    return i + 1 < n && a[i] == b[i + 1] && a[i + 1] == b[i]
                }
                return false
            }
            val longer = if (n > m) a else b
            val shorter = if (n > m) b else a
            var i = 0
            var j = 0
            var skipped = false
            while (i < longer.length && j < shorter.length) {
                if (longer[i] == shorter[j]) {
                    i++
                    j++
                } else {
                    if (skipped) return false
                    skipped = true
                    i++
                }
            }
            return true
        }

        /** Starter next-word pairs. Grows with usage data in later phases. */
        val BIGRAMS: Map<String, List<String>> = mapOf(
            "i" to listOf("am", "love", "think", "will", "have"),
            "you" to listOf("are", "know", "have", "can", "will"),
            "how" to listOf("are", "do", "to", "is", "was"),
            "what" to listOf("are", "do", "is", "time", "happened"),
            "thank" to listOf("you", "god"),
            "thanks" to listOf("a", "for", "bro"),
            "good" to listOf("morning", "night", "luck", "job", "one"),
            "see" to listOf("you", "it", "that", "more"),
            "love" to listOf("you", "it", "this"),
            "do" to listOf("you", "not", "it", "know"),
            "to" to listOf("be", "the", "do", "go", "see"),
            "of" to listOf("the", "course", "it"),
            "on" to listOf("the", "my", "time", "monday"),
            "in" to listOf("the", "my", "a", "this"),
            "for" to listOf("you", "me", "the", "sure"),
            "with" to listOf("you", "me", "this", "my"),
            "at" to listOf("the", "home", "work", "night"),
            "be" to listOf("there", "honest", "safe", "back"),
            "will" to listOf("be", "do", "see", "come"),
            "can" to listOf("you", "i", "we", "not"),
            "let" to listOf("me", "us", "it", "them"),
            "tell" to listOf("me", "him", "her", "them"),
            "call" to listOf("me", "you", "back", "later"),
            "text" to listOf("me", "you", "later"),
            "happy" to listOf("birthday", "diwali", "new", "holi"),
            "very" to listOf("good", "nice", "happy", "much"),
            "so" to listOf("much", "sorry", "good", "nice"),
            "as" to listOf("soon", "well", "always"),
            "asap" to listOf("please", "bro"),
            "take" to listOf("care", "it", "your", "my"),
            "talk" to listOf("to", "later", "soon"),
            "coming" to listOf("home", "soon", "now"),
            "going" to listOf("home", "to", "out"),
            "my" to listOf("name", "phone", "friend", "work"),
            "your" to listOf("name", "phone", "turn", "welcome"),
            "no" to listOf("problem", "worries", "need", "way"),
            "not" to listOf("yet", "now", "sure", "really"),
            "have" to listOf("a", "to", "you", "fun"),
            "has" to listOf("been", "a", "to"),
            "had" to listOf("a", "to", "fun"),
            "was" to listOf("good", "nice", "great", "fun"),
            "were" to listOf("you", "good", "there"),
            "are" to listOf("you", "we", "they", "free"),
            "is" to listOf("it", "this", "that", "the"),
            "it" to listOf("is", "was", "will"),
            "this" to listOf("is", "one", "week", "weekend"),
            "that" to listOf("is", "was", "one", "sounds"),
            "there" to listOf("is", "are", "was"),
            "here" to listOf("is", "are", "you"),
            "where" to listOf("are", "is", "were"),
            "when" to listOf("are", "is", "will", "free"),
            "why" to listOf("not", "are", "is"),
            "which" to listOf("one", "is", "day"),
            "who" to listOf("is", "are", "else"),
            "congratulations" to listOf("bro", "on", "sir"),
            "all" to listOf("the", "good", "done", "best"),
            "best" to listOf("of", "wishes", "luck"),
            "one" to listOf("more", "day", "of", "second"),
            "first" to listOf("of", "time", "day"),
            "last" to listOf("night", "week", "time", "day"),
            "next" to listOf("week", "time", "month", "year"),
            "every" to listOf("day", "time", "one"),
            "new" to listOf("phone", "year", "job", "video"),
            "old" to listOf("one", "phone", "friend"),
            "big" to listOf("fan", "day", "win", "bro"),
            "much" to listOf("love", "appreciated", "better"),
            "many" to listOf("thanks", "happy", "more"),
            "more" to listOf("than", "or", "less"),
            "most" to listOf("of", "welcome", "likely"),
            "some" to listOf("time", "more", "of"),
            "any" to listOf("time", "plans", "update"),
            "late" to listOf("night", "reply", "wish"),
            "early" to listOf("morning", "wish", "happy"),
            "safe" to listOf("travels", "journey", "trip"),
            "drive" to listOf("safe", "carefully"),
            "get" to listOf("well", "home", "back", "ready"),
            "go" to listOf("ahead", "home", "for"),
            "come" to listOf("home", "here", "fast", "soon"),
            "wait" to listOf("a", "for", "please"),
            "hold" to listOf("on", "tight"),
            "looks" to listOf("good", "great", "nice"),
            "sounds" to listOf("good", "great", "fun"),
            "feels" to listOf("good", "great", "like"),
            "works" to listOf("for", "great", "now"),
            "done" to listOf("bro", "sir", "deal"),
            "noted" to listOf("bro", "sir", "thanks"),
            "okay" to listOf("done", "bro", "cool"),
            "sure" to listOf("bro", "thing", "will"),
            "please" to listOf("share", "send", "confirm", "check"),
            "kindly" to listOf("share", "send", "confirm", "check"),
            "send" to listOf("me", "it", "the", "location"),
            "share" to listOf("me", "it", "location", "your"),
            "check" to listOf("this", "it", "your", "mail"),
            "open" to listOf("the", "it", "this"),
        )
    }
}
