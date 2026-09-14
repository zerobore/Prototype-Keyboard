package com.prototype.keyboard.keyboard

import kotlin.math.sqrt

/** One sampled point of a glide trail (view pixels). */
data class TrailPoint(val x: Float, val y: Float)

/** Center of a letter key on screen. */
data class KeyCenter(val ch: Char, val x: Float, val y: Float)

/**
 * Geometric glide-typing decoder. Compares the resampled finger path against
 * ideal key-center paths, anchored at the start/end letters. Pure Kotlin,
 * fully unit-tested (GlideTyperTest).
 */
object GlideTyper {

    const val SAMPLES = 24

    private fun dist(ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = ax - bx
        val dy = ay - by
        return sqrt(dx * dx + dy * dy)
    }

    /** Resample [points] to exactly [n] evenly spaced points along the path. */
    fun resample(points: List<TrailPoint>, n: Int = SAMPLES): List<TrailPoint> {
        require(n >= 2)
        if (points.isEmpty()) return emptyList()
        if (points.size == 1) return List(n) { points[0] }
        var total = 0f
        for (i in 1 until points.size) {
            total += dist(points[i - 1].x, points[i - 1].y, points[i].x, points[i].y)
        }
        if (total <= 0f) return List(n) { points[0] }
        val step = total / (n - 1)
        val out = ArrayList<TrailPoint>(n)
        out.add(points[0])
        var prevX = points[0].x
        var prevY = points[0].y
        var acc = 0f
        var i = 1
        while (out.size < n - 1 && i < points.size) {
            val p = points[i]
            val d = dist(prevX, prevY, p.x, p.y)
            if (acc + d >= step) {
                val need = step - acc
                val t = if (d == 0f) 0f else need / d
                val nx = prevX + (p.x - prevX) * t
                val ny = prevY + (p.y - prevY) * t
                out.add(TrailPoint(nx, ny))
                prevX = nx
                prevY = ny
                acc = 0f
            } else {
                acc += d
                prevX = p.x
                prevY = p.y
                i++
            }
        }
        while (out.size < n) out.add(points.last())
        return out
    }

    /**
     * Decode a glide [path] into candidate words.
     *
     * @param keys key centers of the current letters layout.
     * @param keyWidth average key width in px (anchor tolerance scales with it).
     * @param dictionary lowercase candidate words.
     */
    fun decode(
        path: List<TrailPoint>,
        keys: List<KeyCenter>,
        keyWidth: Float,
        dictionary: List<String>,
        maxResults: Int = 3,
    ): List<String> {
        if (path.size < 2 || keys.isEmpty() || keyWidth <= 0f) return emptyList()
        val byChar = HashMap<Char, KeyCenter>()
        for (k in keys) byChar.putIfAbsent(k.ch.lowercaseChar(), k)
        val start = path.first()
        val end = path.last()
        val tolerance = keyWidth * ANCHOR_TOLERANCE_KEYS
        val resampled = resample(path)
        data class Candidate(val word: String, val score: Float)
        val candidates = ArrayList<Candidate>()
        for (raw in dictionary) {
            val word = raw.trim().lowercase()
            if (word.length < 2 || word.length > MAX_WORD_LEN) continue
            if (!word.all { it in 'a'..'z' }) continue
            val centers = ArrayList<KeyCenter>(word.length)
            var ok = true
            for (c in word) {
                val k = byChar[c]
                if (k == null) {
                    ok = false
                    break
                }
                centers.add(k)
            }
            if (!ok) continue
            val first = centers.first()
            val last = centers.last()
            if (dist(start.x, start.y, first.x, first.y) > tolerance) continue
            if (dist(end.x, end.y, last.x, last.y) > tolerance) continue
            val ideal = resample(centers.map { TrailPoint(it.x, it.y) })
            var sum = 0f
            for (i in ideal.indices) {
                val dx = ideal[i].x - resampled[i].x
                val dy = ideal[i].y - resampled[i].y
                sum += dx * dx + dy * dy
            }
            candidates.add(Candidate(word, sum / ideal.size))
        }
        return candidates.sortedBy { it.score }.take(maxResults).map { it.word }
    }

    private const val ANCHOR_TOLERANCE_KEYS = 1.6f
    private const val MAX_WORD_LEN = 14
}
