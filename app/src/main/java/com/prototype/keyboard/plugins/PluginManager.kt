package com.prototype.keyboard.plugins

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.prototype.keyboard.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

private val Context.pluginPrefs by preferencesDataStore(name = "proto_keyboard_plugins")

/**
 * Installed-pack storage (private files dir) + enable/active selections.
 * Re-installing the same id updates the pack in place ("update anytime").
 */
class PluginManager(private val context: Context) {

    data class InstalledPack(val manifest: PluginManifest, val fileName: String)

    data class PluginState(
        val installed: List<InstalledPack>,
        val activeThemeId: String,
        val disabledIds: Set<String>,
    ) {
        companion object {
            val EMPTY = PluginState(emptyList(), "", emptySet())
        }
    }

    private object Keys {
        val ACTIVE_THEME = stringPreferencesKey("active_theme")
        val DISABLED = stringSetPreferencesKey("disabled_packs")
    }

    private val packsDir: File get() = File(context.filesDir, "plugin_packs")

    val state: Flow<PluginState> = combine(
        snapshots(),
        context.pluginPrefs.data
    ) { installed, prefs ->
        PluginState(
            installed = installed.sortedBy { it.manifest.name.lowercase() },
            activeThemeId = prefs[Keys.ACTIVE_THEME].orEmpty(),
            disabledIds = prefs[Keys.DISABLED].orEmpty()
        )
    }

    // Packs list re-read from disk on each collection trigger.
    private fun snapshots(): Flow<List<InstalledPack>> =
        context.pluginPrefs.data.map { scan() }

    /** First run: copy bundled first-party packs. Idempotent. */
    suspend fun preinstallIfNeeded(): Unit = withContext(Dispatchers.IO) {
        packsDir.mkdirs()
        val marker = File(packsDir, ".preinstalled_v1")
        if (marker.exists()) return@withContext
        FIRST_PARTY.forEach { (resId, fileName) ->
            runCatching {
                context.resources.openRawResource(resId).use { input ->
                    File(packsDir, fileName).outputStream().use { input.copyTo(it) }
                }
            }
        }
        runCatching { marker.writeText("1") }
        bump()
    }

    /** Install or update a pack from its JSON. Returns the manifest. */
    suspend fun importPack(json: String): PluginManifest = withContext(Dispatchers.IO) {
        val decoded = PackCodec.decodePack(json) // throws PackException
        packsDir.mkdirs()
        val fileName = fileNameFor(decoded.manifest.id)
        File(packsDir, fileName).writeText(json)
        bump()
        decoded.manifest
    }

    suspend fun deletePack(id: String): Boolean = withContext(Dispatchers.IO) {
        val removed = File(packsDir, fileNameFor(id)).delete()
        if (context.pluginPrefs.data.map { it[Keys.ACTIVE_THEME] }.first() == id) {
            context.pluginPrefs.edit { it[Keys.ACTIVE_THEME] = "" }
        }
        context.pluginPrefs.edit { prefs ->
            prefs[Keys.DISABLED] = prefs[Keys.DISABLED].orEmpty() - id
        }
        bump()
        removed
    }

    suspend fun setPackEnabled(id: String, enabled: Boolean) {
        context.pluginPrefs.edit { prefs ->
            val current = prefs[Keys.DISABLED].orEmpty().toMutableSet()
            if (enabled) current.remove(id) else current.add(id)
            prefs[Keys.DISABLED] = current
        }
    }

    suspend fun setActiveTheme(id: String) {
        context.pluginPrefs.edit { it[Keys.ACTIVE_THEME] = id }
    }

    suspend fun exportPack(id: String): String? = withContext(Dispatchers.IO) {
        val file = File(packsDir, fileNameFor(id))
        if (!file.exists()) null else runCatching { file.readText() }.getOrNull()
    }

    suspend fun readTheme(id: String): ThemePack? = withContext(Dispatchers.IO) {
        if (id.isEmpty()) return@withContext null
        val text = File(packsDir, fileNameFor(id)).takeIf { it.exists() }
            ?.let { runCatching { it.readText() }.getOrNull() } ?: return@withContext null
        (runCatching { PackCodec.decodePack(text) }.getOrNull() as? PackCodec.DecodedTheme)?.pack
    }

    /** All tools from enabled TOOLS packs. Corrupt packs are skipped, never crash. */
    suspend fun enabledTools(): List<TextTool> = withContext(Dispatchers.IO) {
        val disabled = context.pluginPrefs.data.map { it[Keys.DISABLED].orEmpty() }.first()
        scan()
            .filter { it.manifest.kind == PackKind.TOOLS && it.manifest.id !in disabled }
            .flatMap { installed ->
                val text = runCatching { File(packsDir, installed.fileName).readText() }.getOrNull()
                    ?: return@flatMap emptyList()
                (runCatching { PackCodec.decodePack(text) }.getOrNull() as? PackCodec.DecodedTools)
                    ?.pack?.tools.orEmpty()
            }
    }

    /** Extra dictionary words for [locale] from enabled DICTIONARY packs. */
    suspend fun dictionariesFor(locale: String): List<String> = withContext(Dispatchers.IO) {
        val disabled = context.pluginPrefs.data.map { it[Keys.DISABLED].orEmpty() }.first()
        scan()
            .filter { it.manifest.kind == PackKind.DICTIONARY && it.manifest.id !in disabled }
            .flatMap { installed ->
                val text = runCatching { File(packsDir, installed.fileName).readText() }.getOrNull()
                    ?: return@flatMap emptyList()
                val pack = (runCatching { PackCodec.decodePack(text) }.getOrNull()
                    as? PackCodec.DecodedDictionary)?.pack ?: return@flatMap emptyList()
                if (pack.locale.equals(locale, ignoreCase = true)) pack.words else emptyList()
            }
            .distinct()
    }

    suspend fun promptPacks(): List<PromptPack> = withContext(Dispatchers.IO) {
        val disabled = context.pluginPrefs.data.map { it[Keys.DISABLED].orEmpty() }.first()
        scan()
            .filter { it.manifest.kind == PackKind.PROMPTS && it.manifest.id !in disabled }
            .mapNotNull { installed ->
                val text = runCatching { File(packsDir, installed.fileName).readText() }.getOrNull()
                    ?: return@mapNotNull null
                (runCatching { PackCodec.decodePack(text) }.getOrNull()
                    as? PackCodec.DecodedPrompts)?.pack
            }
    }

    // ---------- internals ----------

    private fun scan(): List<InstalledPack> {
        val dir = packsDir
        if (!dir.exists()) return emptyList()
        return dir.listFiles { f -> f.isFile && f.extension == "json" }.orEmpty().mapNotNull { file ->
            val text = runCatching { file.readText() }.getOrNull() ?: return@mapNotNull null
            val manifest = runCatching { PackCodec.decodePack(text).manifest }.getOrNull()
                ?: return@mapNotNull null
            InstalledPack(manifest, file.name)
        }
    }

    /** Touch prefs so [state] re-emits (disk scan is driven by prefs changes). */
    private suspend fun bump() {
        context.pluginPrefs.edit { prefs ->
            prefs[Keys.ACTIVE_THEME] = prefs[Keys.ACTIVE_THEME].orEmpty()
        }
    }

    private fun fileNameFor(id: String): String =
        id.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80) + ".json"

    companion object {
        private val FIRST_PARTY = listOf(
            R.raw.pack_neon to "proto_neon.json",
            R.raw.pack_midnight to "proto_midnight.json",
            R.raw.pack_hinglish to "proto_hinglish.json",
            R.raw.pack_writer to "proto_writer.json",
            R.raw.pack_starter_tools to "proto_starter_tools.json",
        )
    }
}
