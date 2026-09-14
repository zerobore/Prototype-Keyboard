package com.prototype.keyboard.plugins

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Validates the actual bundled res/raw packs (read as source files).
 * If a first-party pack fails to decode, the platform contract is broken.
 */
class FirstPartyPacksTest {

    private fun rawFile(name: String): File {
        var dir = File(System.getProperty("user.dir"))
        repeat(5) {
            listOf("src/main/res/raw/$name", "app/src/main/res/raw/$name").forEach { rel ->
                val f = File(dir, rel)
                if (f.exists()) return f
            }
            dir = dir.parentFile ?: return@repeat
        }
        throw AssertionError("Cannot find res/raw/$name from ${System.getProperty("user.dir")}")
    }

    private fun decode(name: String): PackCodec.DecodedPack =
        PackCodec.decodePack(rawFile(name).readText())

    @Test fun neonThemeSpotCheck() {
        val decoded = decode("pack_neon.json") as PackCodec.DecodedTheme
        assertEquals("Neon Nights", decoded.manifest.name)
        assertEquals("#00FFC8", decoded.pack.night.accent.uppercase())
        assertEquals(8, decoded.pack.keyRadiusDp)
        assertTrue(PluginValidator.isCompatible(decoded.manifest))
    }

    @Test fun midnightThemeSpotCheck() {
        val decoded = decode("pack_midnight.json") as PackCodec.DecodedTheme
        assertEquals("Midnight Blue", decoded.manifest.name)
        assertEquals("#0B1D3A", decoded.pack.night.background.uppercase())
        assertTrue(PluginValidator.isCompatible(decoded.manifest))
    }

    @Test fun hinglishDictionarySpotCheck() {
        val decoded = decode("pack_hinglish.json") as PackCodec.DecodedDictionary
        assertEquals("en", decoded.pack.locale)
        assertTrue(decoded.pack.words.size >= 80, "Expected 80+ words, got ${decoded.pack.words.size}")
        assertTrue("shukriya" in decoded.pack.words)
        assertTrue("namaste" in decoded.pack.words)
        assertTrue(decoded.pack.words.all { it.matches(Regex("^[a-z']+$")) })
    }

    @Test fun writerPromptsSpotCheck() {
        val decoded = decode("pack_writer.json") as PackCodec.DecodedPrompts
        assertEquals(5, decoded.pack.prompts.size)
        assertTrue(decoded.pack.prompts.any { it.id == "fix-grammar" })
        assertTrue(decoded.pack.prompts.all { "{{text}}" in it.template })
    }

    @Test fun starterToolsSpotCheck() {
        val decoded = decode("pack_starter_tools.json") as PackCodec.DecodedTools
        val shout = decoded.pack.tools.first { it.title == "SHOUT" }
        assertTrue(shout.chain.map { it.op } == listOf(TextOps.TRIM, TextOps.UPPER, TextOps.SUFFIX))
        assertEquals("HEY!!!", TextOps.runChain("  hey ", shout.chain))
        assertTrue(decoded.pack.tools.all { it.chain.isNotEmpty() })
        assertTrue(decoded.pack.tools.flatMap { it.chain }.all { it.op in TextOps.ALL })
    }

    @Test fun allFirstPartyPacksRoundTrip() {
        listOf("pack_neon.json", "pack_midnight.json", "pack_hinglish.json", "pack_writer.json", "pack_starter_tools.json")
            .forEach { name ->
                val first = decode(name)
                val json = when (first) {
                    is PackCodec.DecodedTheme -> PackCodec.encodeTheme(first.pack)
                    is PackCodec.DecodedDictionary -> PackCodec.encodeDictionary(first.pack)
                    is PackCodec.DecodedPrompts -> PackCodec.encodePrompts(first.pack)
                    is PackCodec.DecodedTools -> PackCodec.encodeTools(first.pack)
                }
                val second = PackCodec.decodePack(json)
                assertEquals(first.manifest.id, second.manifest.id, "Round-trip failed for $name")
            }
    }
}
