package com.prototype.keyboard.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** UT-34..UT-37 — glide decoder on a fake QWERTY grid (docs/TEST_PLAN.md). */
class GlideTyperTest {

    private fun qwertyCenters(): List<KeyCenter> {
        val rows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
        val out = ArrayList<KeyCenter>()
        rows.forEachIndexed { r, row ->
            row.forEachIndexed { c, ch ->
                out.add(KeyCenter(ch, c + r * 0.5f, r.toFloat()))
            }
        }
        return out
    }

    private fun pathFor(word: String, keys: List<KeyCenter>): List<TrailPoint> {
        val byChar = keys.associateBy { it.ch }
        val out = ArrayList<TrailPoint>()
        word.forEach { ch ->
            val k = byChar[ch] ?: error("no key for $ch")
            // Start point, mid blend, and exact center per letter (mimics a finger).
            out.add(TrailPoint(k.x + 0.08f, k.y - 0.06f))
            out.add(TrailPoint(k.x - 0.05f, k.y + 0.07f))
            out.add(TrailPoint(k.x, k.y))
        }
        return out
    }

    @Test
    fun `UT-34 resample returns exact counts`() {
        val points = listOf(TrailPoint(0f, 0f), TrailPoint(3f, 4f))
        assertEquals(24, GlideTyper.resample(points).size)
        assertEquals(7, GlideTyper.resample(points, 7).size)
        val single = GlideTyper.resample(listOf(TrailPoint(1f, 2f)), 5)
        assertEquals(5, single.size)
        assertTrue(single.all { it.x == 1f && it.y == 2f })
    }

    @Test
    fun `UT-35 hello decodes first`() {
        val keys = qwertyCenters()
        val path = pathFor("hello", keys)
        val dict = listOf("hello", "help", "hero", "world", "held", "hell", "home")
        val results = GlideTyper.decode(path, keys, 1.0f, dict)
        assertTrue("expected non-empty, got $results", results.isNotEmpty())
        assertEquals("hello", results[0])
    }

    @Test
    fun `UT-36 far-away anchors yield nothing`() {
        val keys = qwertyCenters()
        val path = listOf(TrailPoint(100f, 100f), TrailPoint(105f, 105f))
        val results = GlideTyper.decode(path, keys, 1.0f, listOf("hello"))
        assertTrue(results.isEmpty())
    }

    @Test
    fun `UT-37 degenerate input is safe`() {
        val keys = qwertyCenters()
        assertTrue(GlideTyper.decode(emptyList(), keys, 1.0f, listOf("hello")).isEmpty())
        assertTrue(
            GlideTyper.decode(
                listOf(TrailPoint(0f, 0f)),
                keys, 1.0f, listOf("hello")
            ).isEmpty()
        )
        assertTrue(
            GlideTyper.decode(
                listOf(TrailPoint(0f, 0f), TrailPoint(1f, 1f)),
                emptyList(), 1.0f, listOf("hello")
            ).isEmpty()
        )
    }
}
