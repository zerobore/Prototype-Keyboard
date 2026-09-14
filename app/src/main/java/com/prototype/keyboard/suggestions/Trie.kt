package com.prototype.keyboard.suggestions

/**
 * Prefix trie mapping words to frequency ranks (lower rank = more common).
 * Pure Kotlin, fully unit-tested (TrieTest).
 */
class Trie {

    private class Node {
        val children = HashMap<Char, Node>()
        var rank: Int? = null
    }

    private val root = Node()
    var size: Int = 0
        private set

    fun clear() {
        root.children.clear()
        root.rank = null
        size = 0
    }

    /** Insert keeping the best (lowest) rank for duplicates. */
    fun insert(word: String, rank: Int) {
        if (word.isEmpty()) return
        var node = root
        for (c in word) {
            node = node.children.getOrPut(c) { Node() }
        }
        val prev = node.rank
        if (prev == null) {
            size++
            node.rank = rank
        } else if (rank < prev) {
            node.rank = rank
        }
    }

    fun contains(word: String): Boolean {
        if (word.isEmpty()) return false
        var node = root
        for (c in word) {
            node = node.children[c] ?: return false
        }
        return node.rank != null
    }

    /** Top-[limit] words under [prefix], best rank first. */
    fun completions(prefix: String, limit: Int): List<String> {
        if (prefix.isEmpty() || limit <= 0) return emptyList()
        var node = root
        for (c in prefix) {
            node = node.children[c] ?: return emptyList()
        }
        val found = ArrayList<Pair<String, Int>>()
        val current = StringBuilder(prefix)
        fun dfs(n: Node) {
            n.rank?.let { found.add(current.toString() to it) }
            for ((c, child) in n.children) {
                current.append(c)
                dfs(child)
                current.deleteCharAt(current.length - 1)
            }
        }
        dfs(node)
        return found.sortedBy { it.second }.take(limit).map { it.first }
    }
}
