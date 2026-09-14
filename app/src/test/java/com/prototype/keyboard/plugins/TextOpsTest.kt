package com.prototype.keyboard.plugins

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TextOpsTest {

    @Test fun upperAndLower() {
        assertEquals("ABC", TextOps.runChain("AbC", listOf(TextOp(TextOps.UPPER))))
        assertEquals("abc", TextOps.runChain("AbC", listOf(TextOp(TextOps.LOWER))))
    }

    @Test fun titleCase() {
        assertEquals("Hello World", TextOps.runChain("hello world", listOf(TextOp(TextOps.TITLE))))
    }

    @Test fun sentenceCase() {
        assertEquals(
            "Hello. World",
            TextOps.runChain("hello. world", listOf(TextOp(TextOps.SENTENCE)))
        )
    }

    @Test fun trimSqueezeNoSpaces() {
        assertEquals("x", TextOps.runChain("  x  ", listOf(TextOp(TextOps.TRIM))))
        assertEquals("a b", TextOps.runChain("a   b", listOf(TextOp(TextOps.SQUEEZE))))
        assertEquals("abc", TextOps.runChain("a b  c", listOf(TextOp(TextOps.NO_SPACES))))
    }

    @Test fun prefixSuffixReplace() {
        assertEquals(">>x", TextOps.runChain("x", listOf(TextOp(TextOps.PREFIX, ">>"))))
        assertEquals("x!!", TextOps.runChain("x", listOf(TextOp(TextOps.SUFFIX, "!!"))))
        assertEquals("bbb", TextOps.runChain("aaa", listOf(TextOp(TextOps.REPLACE, "a", "b"))))
    }

    @Test fun reverseAndUpsideDown() {
        assertEquals("cba", TextOps.runChain("abc", listOf(TextOp(TextOps.REVERSE))))
        assertEquals("ollǝH", TextOps.runChain("Hello", listOf(TextOp(TextOps.UPSIDE_DOWN))))
    }

    @Test fun chainRunsInOrder() {
        val out = TextOps.runChain(
            "  hello   world  ",
            listOf(TextOp(TextOps.TRIM), TextOp(TextOps.SQUEEZE), TextOp(TextOps.UPPER))
        )
        assertEquals("HELLO WORLD", out)
    }

    @Test fun emptyChainReturnsInput() {
        assertEquals("keep me", TextOps.runChain("keep me", emptyList()))
    }

    @Test fun unknownOpPassesThrough() {
        assertEquals("Ab", TextOps.runChain("Ab", listOf(TextOp("nonsense"))))
    }

    @Test fun chainHonorsTwentyStepCap() {
        val chain = List(25) { TextOp(TextOps.SUFFIX, "a") }
        assertEquals("x" + "a".repeat(20), TextOps.runChain("x", chain))
    }

    @Test fun hugeInputIsTruncatedFirst() {
        val big = "a".repeat(5000)
        val out = TextOps.runChain(big, listOf(TextOp(TextOps.SUFFIX, "!")))
        assertTrue(out.length == 4097 && out.endsWith("!"))
    }
}
