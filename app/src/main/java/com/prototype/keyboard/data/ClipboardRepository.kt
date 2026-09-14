package com.prototype.keyboard.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.clipboardStore by preferencesDataStore(name = "proto_keyboard_clipboard")

/**
 * Persists [ClipboardStore] as versioned JSON in a private DataStore file.
 * Corrupt payloads fall back to a fresh store instead of crashing.
 */
class ClipboardRepository(private val context: Context) {

    private object Keys {
        val PAYLOAD = stringPreferencesKey("clipboard_json_v1")
    }

    private fun store(): DataStore<Preferences> = context.clipboardStore

    suspend fun load(): ClipboardStore {
        val json = store().data.map { it[Keys.PAYLOAD] }.first()
        val parsed = runCatching { if (json.isNullOrEmpty()) null else parse(json) }.getOrNull()
        val result = parsed ?: ClipboardStore()
        // Opportunistic hygiene on every load.
        if (result.purgeExpiredSensitive(System.currentTimeMillis()) > 0) {
            save(result)
        }
        return result
    }

    suspend fun save(store: ClipboardStore) {
        store().edit { it[Keys.PAYLOAD] = serialize(store) }
    }

    /** Load → mutate → save in one call; returns the updated store. */
    suspend fun mutate(block: (ClipboardStore) -> Unit): ClipboardStore {
        val current = load()
        block(current)
        save(current)
        return current
    }

    suspend fun clear() {
        store().edit { it.remove(Keys.PAYLOAD) }
    }

    // ---- JSON codec (v1) ----

    internal fun serialize(store: ClipboardStore): String {
        val root = JSONObject()
        root.put("version", 1)
        val sections = JSONArray()
        store.sections.forEach { s ->
            sections.put(
                JSONObject()
                    .put("id", s.id)
                    .put("name", s.name)
                    .put("sensitive", s.sensitive)
                    .put("createdAt", s.createdAt)
            )
        }
        root.put("sections", sections)
        val clips = JSONArray()
        store.clips.forEach { c ->
            clips.put(
                JSONObject()
                    .put("id", c.id)
                    .put("sectionId", c.sectionId)
                    .put("text", c.text)
                    .put("pinned", c.pinned)
                    .put("createdAt", c.createdAt)
            )
        }
        root.put("clips", clips)
        return root.toString()
    }

    internal fun parse(json: String): ClipboardStore {
        val root = JSONObject(json)
        if (root.optInt("version", -1) != 1) throw IllegalArgumentException("version")
        val sections = ArrayList<ClipSection>()
        val sectionArray = root.optJSONArray("sections") ?: JSONArray()
        for (i in 0 until minOf(sectionArray.length(), ClipboardStore.MAX_SECTIONS)) {
            val o = sectionArray.optJSONObject(i) ?: continue
            val id = o.optString("id").takeIf { it.isNotEmpty() } ?: continue
            val name = o.optString("name", "Section").take(ClipboardStore.MAX_SECTION_NAME)
            sections.add(ClipSection(id, name, o.optBoolean("sensitive", false), o.optLong("createdAt", 0L)))
        }
        if (sections.none { it.id == ClipboardStore.GENERAL_ID }) {
            sections.add(0, ClipSection(ClipboardStore.GENERAL_ID, "General", false))
        }
        val clips = ArrayList<Clip>()
        val clipArray = root.optJSONArray("clips") ?: JSONArray()
        for (i in 0 until minOf(clipArray.length(), ClipboardStore.MAX_CLIPS)) {
            val o = clipArray.optJSONObject(i) ?: continue
            val id = o.optString("id").takeIf { it.isNotEmpty() } ?: continue
            val text = o.optString("text", "").take(ClipboardStore.MAX_CLIP_CHARS)
            if (text.isEmpty()) continue
            var sectionId = o.optString("sectionId", ClipboardStore.GENERAL_ID)
            if (sections.none { it.id == sectionId }) sectionId = ClipboardStore.GENERAL_ID
            clips.add(
                Clip(id, sectionId, text, o.optBoolean("pinned", false), o.optLong("createdAt", 0L))
            )
        }
        return ClipboardStore(sections, clips)
    }
}
