package com.prototype.keyboard.plugins

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * End-to-end platform flow through the public Builder API:
 * build → encode (.pkb JSON) → decode → compatible.
 * Also pins the file contract and the update-by-id path.
 */
class PluginFlowTest {

    @Test fun themeFlowThroughBuilder() {
        val pack = PackModels.Builder.theme("com.flow.t", "Flow", "Me")
            .description("d").version("2.0.0")
            .day("#111111", "#222222", "#333333", "#444444", "#555555", "#666666")
            .night("#111111", "#222222", "#333333", "#444444", "#555555", "#666666")
            .radius(10).build()
        val decoded = PackCodec.decodePack(PackCodec.encodeTheme(pack)) as PackCodec.DecodedTheme
        assertEquals("com.flow.t", decoded.manifest.id)
        assertEquals("2.0.0", decoded.manifest.version)
        assertTrue(PluginValidator.isCompatible(decoded.manifest))
    }

    @Test fun dictionaryFlowThroughBuilder() {
        val pack = PackModels.Builder.dictionary("com.flow.d", "FlowDict", "Me")
            .locale("en").words(listOf("alpha", "beta")).build()
        val decoded = PackCodec.decodePack(PackCodec.encodeDictionary(pack)) as PackCodec.DecodedDictionary
        assertEquals(listOf("alpha", "beta"), decoded.pack.words)
        assertTrue(PluginValidator.isCompatible(decoded.manifest))
    }

    @Test fun promptsFlowThroughBuilder() {
        val pack = PackModels.Builder.prompts("com.flow.p", "FlowP", "Me")
            .prompts(listOf(AiPrompt("fix", "Fix", "Fix: {{text}}"))).build()
        val decoded = PackCodec.decodePack(PackCodec.encodePrompts(pack)) as PackCodec.DecodedPrompts
        assertEquals("Fix: {{text}}", decoded.pack.prompts.single().template)
        assertTrue(PluginValidator.isCompatible(decoded.manifest))
    }

    @Test fun toolsFlowThroughBuilder() {
        val tool = TextTool("up", "Up", "⬆", listOf(TextOp(TextOps.UPPER)))
        val pack = PackModels.Builder.tools("com.flow.tools", "FlowT", "Me")
            .tools(listOf(tool)).build()
        val decoded = PackCodec.decodePack(PackCodec.encodeTools(pack)) as PackCodec.DecodedTools
        assertEquals("⬆", decoded.pack.tools.single().icon)
        assertTrue(PluginValidator.isCompatible(decoded.manifest))
    }

    @Test fun builderDefaultsToCurrentHostAndApi() {
        val pack = PackModels.Builder.theme("com.flow.x", "X", "M").build()
        assertEquals(PluginApi.PLUGIN_API_VERSION, pack.manifest.pluginApiVersion)
        assertEquals(PluginApi.HOST_VERSION, pack.manifest.minHostVersion)
    }

    @Test fun fileContractPinsFormatMarker() {
        val pack = PackModels.Builder.theme("com.flow.y", "Y", "M").build()
        val json = PackCodec.encodeTheme(pack)
        assertTrue("\"format\": \"pkb\"" in json)
        assertTrue("\"formatVersion\": 1" in json)
    }

    @Test fun updateByIdKeepsIdentity() {
        val v1 = PackModels.Builder.theme("com.flow.u", "U", "M").version("1.0.0").build()
        val v2 = PackModels.Builder.theme("com.flow.u", "U", "M").version("1.1.0").build()
        val d1 = PackCodec.decodePack(PackCodec.encodeTheme(v1)).manifest
        val d2 = PackCodec.decodePack(PackCodec.encodeTheme(v2)).manifest
        assertEquals(d1.id, d2.id) // reinstalling same id updates in place
        assertEquals("1.1.0", d2.version)
    }
}
