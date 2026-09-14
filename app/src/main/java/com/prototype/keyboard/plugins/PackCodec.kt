package com.prototype.keyboard.plugins

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * .pkb pack codec (JSON in v1). Strict on import: structural problems,
 * oversize payloads and bad manifests throw [PackException] with a
 * human-readable message (shown in Studio). Pure Kotlin + org.json,
 * unit-tested (PackCodecTest).
 */
object PackCodec {

    const val FORMAT = "pkb"
    const val FORMAT_VERSION = 1

    const val MAX_PACK_BYTES = 512 * 1024
    const val MAX_DICT_WORDS = 20_000
    const val MAX_WORD_LEN = 32
    const val MAX_PROMPTS = 50
    const val MAX_TEMPLATE_LEN = 4000
    const val MAX_TOOLS = 50
    const val MAX_CHAIN = 20
    const val MAX_TITLE = 48
    const val MAX_ID_LEN = 64

    class PackException(message: String) : Exception(message)

    sealed interface DecodedPack {
        val manifest: PluginManifest
    }

    data class DecodedTheme(val pack: ThemePack) : DecodedPack {
        override val manifest: PluginManifest get() = pack.manifest
    }

    data class DecodedDictionary(val pack: DictionaryPack) : DecodedPack {
        override val manifest: PluginManifest get() = pack.manifest
    }

    data class DecodedPrompts(val pack: PromptPack) : DecodedPack {
        override val manifest: PluginManifest get() = pack.manifest
    }

    data class DecodedTools(val pack: TextToolPack) : DecodedPack {
        override val manifest: PluginManifest get() = pack.manifest
    }

    // ---------- decode ----------

    fun decodePack(json: String): DecodedPack {
        if (json.length > MAX_PACK_BYTES) throw PackException("Pack too large (max 512 KB)")
        val root = try {
            JSONObject(json)
        } catch (e: JSONException) {
            throw PackException("Not valid JSON")
        }
        if (root.optString("format") != FORMAT) throw PackException("Not a .pkb pack (bad format tag)")
        if (root.optInt("formatVersion", -1) != FORMAT_VERSION) {
            throw PackException("Unsupported pack format v${root.optInt("formatVersion", -1)} (host reads v$FORMAT_VERSION)")
        }
        val manifest = decodeManifest(root.optJSONObject("manifest") ?: throw PackException("Missing manifest"))
        val payload = root.optJSONObject("payload") ?: throw PackException("Missing payload")
        return when (manifest.kind) {
            PackKind.THEME -> DecodedTheme(decodeTheme(manifest, payload))
            PackKind.DICTIONARY -> DecodedDictionary(decodeDictionary(manifest, payload))
            PackKind.PROMPTS -> DecodedPrompts(decodePrompts(manifest, payload))
            PackKind.TOOLS -> DecodedTools(decodeTools(manifest, payload))
        }
    }

    private fun decodeManifest(o: JSONObject): PluginManifest {
        val kind = try {
            PackKind.valueOf(o.optString("kind").uppercase())
        } catch (e: IllegalArgumentException) {
            throw PackException("Unknown kind '${o.optString("kind")}' (theme/dictionary/prompts/tools)")
        }
        val manifest = PluginManifest(
            id = o.optString("id"),
            name = o.optString("name"),
            author = o.optString("author"),
            version = o.optString("version"),
            pluginApiVersion = o.optInt("pluginApiVersion", -1),
            minHostVersion = o.optString("minHostVersion"),
            kind = kind,
            description = o.optString("description", "")
        )
        val errors = PluginValidator.validateManifest(manifest)
        if (errors.isNotEmpty()) throw PackException(errors.joinToString("; "))
        return manifest
    }

    private fun decodeTheme(m: PluginManifest, p: JSONObject): ThemePack {
        fun colors(key: String): ThemeColors {
            val o = p.optJSONObject(key) ?: throw PackException("Theme missing '$key' colors")
            fun color(field: String): String {
                val value = o.optString(field)
                if (!isColor(value)) throw PackException("Theme $key.$field must be #RRGGBB (got '$value')")
                return value.uppercase()
            }
            return ThemeColors(
                background = color("background"), key = color("key"),
                funcKey = color("funcKey"), pressedKey = color("pressedKey"),
                text = color("text"), accent = color("accent")
            )
        }
        val radius = p.optInt("keyRadiusDp", 6)
        if (radius !in 0..16) throw PackException("keyRadiusDp must be 0..16")
        return ThemePack(m, colors("day"), colors("night"), radius)
    }

    private fun decodeDictionary(m: PluginManifest, p: JSONObject): DictionaryPack {
        val locale = p.optString("locale").lowercase()
        if (locale.length !in 2..8) throw PackException("Dictionary needs a locale (e.g. en)")
        val array = p.optJSONArray("words") ?: throw PackException("Dictionary needs a words array")
        if (array.length() > MAX_DICT_WORDS) throw PackException("Too many words (max $MAX_DICT_WORDS)")
        val words = LinkedHashSet<String>()
        for (i in 0 until array.length()) {
            val raw = array.optString(i, "").trim().lowercase()
            if (raw.length in 2..MAX_WORD_LEN && raw.all { it.isLetter() || it == '\'' }) {
                words.add(raw)
            }
        }
        if (words.isEmpty()) throw PackException("Dictionary has no valid words (2..32 letters each)")
        return DictionaryPack(m, locale, words.toList())
    }

    private fun decodePrompts(m: PluginManifest, p: JSONObject): PromptPack {
        val array = p.optJSONArray("prompts") ?: throw PackException("Prompt pack needs a prompts array")
        if (array.length() == 0 || array.length() > MAX_PROMPTS) {
            throw PackException("Prompt count must be 1..$MAX_PROMPTS")
        }
        val prompts = ArrayList<AiPrompt>()
        val ids = HashSet<String>()
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: throw PackException("Prompt #$i is malformed")
            val id = o.optString("id")
            if (id.isBlank() || id.length > MAX_ID_LEN || !ids.add(id)) {
                throw PackException("Prompt #$i needs a unique id (max $MAX_ID_LEN)")
            }
            val title = o.optString("title")
            if (title.isBlank() || title.length > MAX_TITLE) throw PackException("Prompt '$id' needs a title (1..$MAX_TITLE)")
            val template = o.optString("template")
            if (template.isBlank() || template.length > MAX_TEMPLATE_LEN) {
                throw PackException("Prompt '$id' needs a template (1..$MAX_TEMPLATE_LEN)")
            }
            prompts.add(AiPrompt(id, title, template, o.optString("description", "").take(280)))
        }
        return PromptPack(m, prompts)
    }

    private fun decodeTools(m: PluginManifest, p: JSONObject): TextToolPack {
        val array = p.optJSONArray("tools") ?: throw PackException("Tool pack needs a tools array")
        if (array.length() == 0 || array.length() > MAX_TOOLS) {
            throw PackException("Tool count must be 1..$MAX_TOOLS")
        }
        val tools = ArrayList<TextTool>()
        val ids = HashSet<String>()
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: throw PackException("Tool #$i is malformed")
            val id = o.optString("id")
            if (id.isBlank() || id.length > MAX_ID_LEN || !ids.add(id)) {
                throw PackException("Tool #$i needs a unique id (max $MAX_ID_LEN)")
            }
            val title = o.optString("title")
            if (title.isBlank() || title.length > MAX_TITLE) throw PackException("Tool '$id' needs a title (1..$MAX_TITLE)")
            val chainArray = o.optJSONArray("chain") ?: throw PackException("Tool '$id' needs an op chain")
            if (chainArray.length() == 0 || chainArray.length() > MAX_CHAIN) {
                throw PackException("Tool '$id' chain must be 1..$MAX_CHAIN ops")
            }
            val chain = ArrayList<TextOp>()
            for (j in 0 until chainArray.length()) {
                val opObj = chainArray.optJSONObject(j) ?: throw PackException("Tool '$id' op #$j malformed")
                val op = opObj.optString("op")
                if (op !in TextOps.ALL) throw PackException("Tool '$id' has unknown op '$op'")
                chain.add(TextOp(op, opObj.optString("arg1", "").take(200), opObj.optString("arg2", "").take(200)))
            }
            tools.add(TextTool(id, title, o.optString("icon", "\uD83D\uDEE0").take(8), chain))
        }
        return TextToolPack(m, tools)
    }

    // ---------- encode ----------

    private fun encodeManifest(m: PluginManifest): JSONObject = JSONObject()
        .put("id", m.id)
        .put("name", m.name)
        .put("author", m.author)
        .put("version", m.version)
        .put("pluginApiVersion", m.pluginApiVersion)
        .put("minHostVersion", m.minHostVersion)
        .put("kind", m.kind.name.lowercase())
        .put("description", m.description)

    private fun envelope(manifest: PluginManifest, payload: JSONObject): String =
        JSONObject()
            .put("format", FORMAT)
            .put("formatVersion", FORMAT_VERSION)
            .put("manifest", encodeManifest(manifest))
            .put("payload", payload)
            .toString(2)

    private fun encodeColors(c: ThemeColors): JSONObject = JSONObject()
        .put("background", c.background).put("key", c.key).put("funcKey", c.funcKey)
        .put("pressedKey", c.pressedKey).put("text", c.text).put("accent", c.accent)

    fun encodeTheme(p: ThemePack): String = envelope(
        p.manifest,
        JSONObject()
            .put("day", encodeColors(p.day))
            .put("night", encodeColors(p.night))
            .put("keyRadiusDp", p.keyRadiusDp)
    )

    fun encodeDictionary(p: DictionaryPack): String = envelope(
        p.manifest,
        JSONObject()
            .put("locale", p.locale)
            .put("words", JSONArray(p.words))
    )

    fun encodePrompts(p: PromptPack): String = envelope(
        p.manifest,
        JSONObject().put(
            "prompts",
            JSONArray().apply {
                p.prompts.forEach {
                    put(
                        JSONObject().put("id", it.id).put("title", it.title)
                            .put("template", it.template).put("description", it.description)
                    )
                }
            }
        )
    )

    fun encodeTools(p: TextToolPack): String = envelope(
        p.manifest,
        JSONObject().put(
            "tools",
            JSONArray().apply {
                p.tools.forEach { tool ->
                    put(
                        JSONObject().put("id", tool.id).put("title", tool.title).put("icon", tool.icon)
                            .put(
                                "chain",
                                JSONArray().apply {
                                    tool.chain.forEach { op ->
                                        put(JSONObject().put("op", op.op).put("arg1", op.arg1).put("arg2", op.arg2))
                                    }
                                }
                            )
                    )
                }
            }
        )
    )

    private fun isColor(value: String): Boolean =
        value.length == 7 && value[0] == '#' &&
            value.drop(1).all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
}
