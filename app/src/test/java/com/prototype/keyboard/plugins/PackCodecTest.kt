package com.prototype.keyboard.plugins

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PackCodecTest {

    private fun manifest(kind: PackKind) = PluginManifest(
        id = "com.test.pack", name = "T", author = "Me", version = "1.0.0",
        pluginApiVersion = 1, minHostVersion = "0.3.0", kind = kind, description = ""
    )

    private val day = ThemeColors("#D8DCE3", "#FFFFFF", "#B9C0CB", "#9FB4D8", "#1F1F1F", "#0B57D0")
    private val night = ThemeColors("#28292A", "#3E3F42", "#2F3033", "#5F6368", "#E8EAED", "#A8C7FA")

    @Test fun themeRoundTrip() {
        val pack = ThemePack(manifest(PackKind.THEME), day, night, 8)
        val decoded = PackCodec.decodePack(PackCodec.encodeTheme(pack)) as PackCodec.DecodedTheme
        assertEquals("#A8C7FA", decoded.pack.night.accent)
        assertEquals(8, decoded.pack.keyRadiusDp)
        assertEquals("com.test.pack", decoded.manifest.id)
    }

    @Test fun dictionaryRejectsBadWord() {
        val pack = DictionaryPack(manifest(PackKind.DICTIONARY), "en", listOf("fine", "bad1"))
        assertFailsWith<PackCodec.PackException> { PackCodec.encodeDictionary(pack) }
    }

    @Test fun dictionaryRejectsEmpty() {
        val pack = DictionaryPack(manifest(PackKind.DICTIONARY), "en", emptyList())
        assertFailsWith<PackCodec.PackException> { PackCodec.encodeDictionary(pack) }
    }

    @Test fun toolsRejectUnknownOp() {
        val tool = TextTool("t", "T", "x", listOf(TextOp("bogus")))
        val pack = TextToolPack(manifest(PackKind.TOOLS), listOf(tool))
        assertFailsWith<PackCodec.PackException> { PackCodec.encodeTools(pack) }
    }

    @Test fun promptsRequireTextPlaceholder() {
        val prompt = AiPrompt("p", "P", "no placeholder here")
        val pack = PromptPack(manifest(PackKind.PROMPTS), listOf(prompt))
        assertFailsWith<PackCodec.PackException> { PackCodec.encodePrompts(pack) }
    }

    @Test fun manifestIdRejected() {
        val bad = manifest(PackKind.THEME).copy(id = "Bad ID!")
        assertFailsWith<PackCodec.PackException> {
            PackCodec.encodeTheme(ThemePack(bad, day, night, 6))
        }
    }

    @Test fun versionRejected() {
        val bad = manifest(PackKind.THEME).copy(version = "1.0")
        assertFailsWith<PackCodec.PackException> {
            PackCodec.encodeTheme(ThemePack(bad, day, night, 6))
        }
    }

    @Test fun colorRejected() {
        val badDay = day.copy(background = "red")
        assertFailsWith<PackCodec.PackException> {
            PackCodec.encodeTheme(ThemePack(manifest(PackKind.THEME), badDay, night, 6))
        }
    }

    @Test fun radiusOutOfRangeRejectedOnDecode() {
        val json = PackCodec.encodeTheme(ThemePack(manifest(PackKind.THEME), day, night, 6))
            .replace("\"keyRadiusDp\": 6", "\"keyRadiusDp\": 99")
        assertFailsWith<PackCodec.PackException> { PackCodec.decodePack(json) }
    }

    @Test fun builderClampsRadius() {
        val pack = PackModels.Builder.theme("com.t", "T", "M").day("#111111", "#222222", "#333333", "#444444", "#555555", "#666666")
            .night("#111111", "#222222", "#333333", "#444444", "#555555", "#666666")
            .radius(99).build()
        assertEquals(16, pack.keyRadiusDp)
    }

    @Test fun compatAcceptsCurrentApiAndHost() {
        assertTrue(PluginValidator.isCompatible(manifest(PackKind.THEME)))
    }

    @Test fun compatRejectsNewerApi() {
        assertFalse(PluginValidator.isCompatible(manifest(PackKind.THEME).copy(pluginApiVersion = 2)))
    }

    @Test fun compatRejectsNewerHost() {
        assertFalse(PluginValidator.isCompatible(manifest(PackKind.THEME).copy(minHostVersion = "9.9.9")))
    }

    @Test fun decodeRejectsWrongFormat() {
        assertFailsWith<PackCodec.PackException> { PackCodec.decodePack("""{"format":"nope"}""") }
    }

    @Test fun decodeRejectsUnknownKind() {
        val json = """
            {"format":"pkb","formatVersion":1,
             "manifest":{"id":"com.t","name":"T","author":"M","version":"1.0.0",
             "pluginApiVersion":1,"minHostVersion":"0.3.0","kind":"widget","description":""},
             "payload":{}}
        """.trimIndent()
        assertFailsWith<PackCodec.PackException> { PackCodec.decodePack(json) }
    }
}
