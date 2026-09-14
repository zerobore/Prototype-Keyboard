package com.prototype.keyboard.plugins

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Host-side execution safety for tool chains (the Tools board never runs
 * pack code — packs only declare op chains that [TextOps] executes).
 */
class ToolsBoardBehaviorTest {

    @Test fun replaceWithEmptyFindIsNoop() {
        assertEquals("abc", TextOps.runChain("abc", listOf(TextOp(TextOps.REPLACE, "", "x"))))
    }

    @Test fun emptyArgsAreNoops() {
        assertEquals("x", TextOps.runChain("x", listOf(TextOp(TextOps.PREFIX), TextOp(TextOps.SUFFIX))))
    }

    @Test fun thirtyStepChainStopsAtTwenty() {
        val chain = List(30) { TextOp(TextOps.UPPER) }
        assertEquals("A", TextOps.runChain("a", chain))
    }

    @Test fun fiftySuffixesCapOutput() {
        val chain = List(50) { TextOp(TextOps.SUFFIX, "a") }
        assertEquals("x" + "a".repeat(20), TextOps.runChain("x", chain))
    }

    @Test fun unicodeInputDoesNotCrash() {
        val out = TextOps.runChain("hi 👋 bye", listOf(TextOp(TextOps.UPSIDE_DOWN), TextOp(TextOps.REVERSE)))
        assertTrue(out.isNotEmpty())
    }

    @Test fun executionIsPure() {
        val chain = listOf(TextOp(TextOps.TRIM), TextOp(TextOps.TITLE))
        assertEquals(TextOps.runChain(" a b ", chain), TextOps.runChain(" a b ", chain))
    }

    @Test fun starterShoutContract() {
        val shout = listOf(TextOp(TextOps.TRIM), TextOp(TextOps.UPPER), TextOp(TextOps.SUFFIX, "!!!"))
        assertEquals("HEY!!!", TextOps.runChain("  hey ", shout))
    }

    @Test fun quoteContract() {
        val quote = listOf(TextOp(TextOps.TRIM), TextOp(TextOps.PREFIX, "\""), TextOp(TextOps.SUFFIX, "\""))
        assertEquals("\"hi\"", TextOps.runChain("hi", quote))
    }
}
