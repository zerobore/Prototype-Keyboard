package com.prototype.keyboard.data

import com.prototype.keyboard.data.db.UserWord
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Versioned JSON backup codec for explicit user export/import (SAF).
 * Pure Kotlin + org.json; unit-tested in BackupCodecTest.
 *
 * Security: clips in sensitive sections are NEVER exported. Import is strict
 * about the version envelope and lenient about individual entries (skips bad
 * ones, caps sizes) so a hand-edited file cannot corrupt the app.
 */
object BackupCodec {

    const val VERSION = 1
    const val MAX_BYTES = 512 * 1024
    private const val MAX_WORDS = 2000
    private const val MAX_CLIPS = 200
    private const val MAX_SECTIONS = 12

    class BackupException(message: String) : Exception(message)

    data class ParsedBackup(
        val settings: KeyboardSettings,
        val locale: String,
        val sections: List<ClipSection>,
        val clips: List<Clip>,
        val userWords: List<UserWord>,
    )

    fun export(
        settings: KeyboardSettings,
        locale: String,
        sections: List<ClipSection>,
        clips: List<Clip>,
        userWords: List<UserWord>,
    ): String {
        val root = JSONObject()
        root.put("version", VERSION)
        root.put("app", "com.prototype.keyboard")
        root.put("exportedAt", System.currentTimeMillis())

        val s = JSONObject()
        s.put("themeMode", settings.themeMode.name)
        s.put("hapticFeedback", settings.hapticFeedback)
        s.put("hapticStrength", settings.hapticStrength)
        s.put("keypressSound", settings.keypressSound)
        s.put("keyHeightDp", settings.keyHeightDp)
        s.put("keyBorders", settings.keyBorders)
        s.put("suggestionsEnabled", settings.suggestionsEnabled)
        s.put("autocorrectEnabled", settings.autocorrectEnabled)
        s.put("glideEnabled", settings.glideEnabled)
        s.put("autoCapsEnabled", settings.autoCapsEnabled)
        s.put("doubleSpacePeriodEnabled", settings.doubleSpacePeriodEnabled)
        s.put("quickPasteEnabled", settings.quickPasteEnabled)
        s.put("clipboardCaptureEnabled", settings.clipboardCaptureEnabled)
        s.put("learningEnabled", settings.learningEnabled)
        root.put("settings", s)
        root.put("locale", locale)

        val sectionArray = JSONArray()
        sections.take(MAX_SECTIONS).forEach { section ->
            sectionArray.put(
                JSONObject()
                    .put("id", section.id)
                    .put("name", section.name)
                    .put("sensitive", section.sensitive)
            )
        }
        root.put("sections", sectionArray)

        val sensitiveIds = sections.filter { it.sensitive }.map { it.id }.toSet()
        val clipArray = JSONArray()
        clips.filterNot { it.sectionId in sensitiveIds }.take(MAX_CLIPS).forEach { clip ->
            clipArray.put(
                JSONObject()
                    .put("sectionId", clip.sectionId)
                    .put("text", clip.text.take(ClipboardStore.MAX_CLIP_CHARS))
                    .put("pinned", clip.pinned)
            )
        }
        root.put("clips", clipArray)

        val wordArray = JSONArray()
        userWords.take(MAX_WORDS).forEach { w ->
            wordArray.put(
                JSONObject()
                    .put("word", w.word)
                    .put("locale", w.locale)
                    .put("frequency", w.frequency)
            )
        }
        root.put("userWords", wordArray)
        return root.toString()
    }

    fun parse(json: String): ParsedBackup {
        if (json.length > MAX_BYTES) throw BackupException("Backup file too large")
        val root = try {
            JSONObject(json)
        } catch (e: JSONException) {
            throw BackupException("Not a valid backup file")
        }
        if (root.optInt("version", -1) != VERSION) {
            throw BackupException("Unsupported backup version (expected $VERSION)")
        }
        val defaults = KeyboardSettings()
        val s = root.optJSONObject("settings") ?: JSONObject()
        val settings = KeyboardSettings(
            themeMode = ThemeMode.fromName(s.optString("themeMode", defaults.themeMode.name)),
            hapticFeedback = s.optBoolean("hapticFeedback", defaults.hapticFeedback),
            hapticStrength = s.optInt("hapticStrength", defaults.hapticStrength).coerceIn(0, 100),
            keypressSound = s.optBoolean("keypressSound", defaults.keypressSound),
            keyHeightDp = s.optInt("keyHeightDp", defaults.keyHeightDp).coerceIn(40, 80),
            keyBorders = s.optBoolean("keyBorders", defaults.keyBorders),
            suggestionsEnabled = s.optBoolean("suggestionsEnabled", defaults.suggestionsEnabled),
            autocorrectEnabled = s.optBoolean("autocorrectEnabled", defaults.autocorrectEnabled),
            glideEnabled = s.optBoolean("glideEnabled", defaults.glideEnabled),
            autoCapsEnabled = s.optBoolean("autoCapsEnabled", defaults.autoCapsEnabled),
            doubleSpacePeriodEnabled = s.optBoolean(
                "doubleSpacePeriodEnabled",
                defaults.doubleSpacePeriodEnabled
            ),
            quickPasteEnabled = s.optBoolean("quickPasteEnabled", defaults.quickPasteEnabled),
            clipboardCaptureEnabled = s.optBoolean(
                "clipboardCaptureEnabled",
                defaults.clipboardCaptureEnabled
            ),
            learningEnabled = s.optBoolean("learningEnabled", defaults.learningEnabled),
        )
        val locale = root.optString("locale", "en").takeIf { it.length in 2..8 } ?: "en"

        val sections = ArrayList<ClipSection>()
        val sectionArray = root.optJSONArray("sections") ?: JSONArray()
        for (i in 0 until minOf(sectionArray.length(), MAX_SECTIONS)) {
            val o = sectionArray.optJSONObject(i) ?: continue
            val id = o.optString("id").takeIf { it.isNotEmpty() && it.length <= 64 } ?: continue
            val name = o.optString("name", "Section").take(ClipboardStore.MAX_SECTION_NAME)
            sections.add(ClipSection(id, name, o.optBoolean("sensitive", false)))
        }
        if (sections.none { it.id == ClipboardStore.GENERAL_ID }) {
            sections.add(0, ClipSection(ClipboardStore.GENERAL_ID, "General", false))
        }

        val clips = ArrayList<Clip>()
        val clipArray = root.optJSONArray("clips") ?: JSONArray()
        for (i in 0 until minOf(clipArray.length(), MAX_CLIPS)) {
            val o = clipArray.optJSONObject(i) ?: continue
            val text = o.optString("text", "").take(ClipboardStore.MAX_CLIP_CHARS)
            if (text.isEmpty()) continue
            var sectionId = o.optString("sectionId", ClipboardStore.GENERAL_ID)
            if (sections.none { it.id == sectionId }) sectionId = ClipboardStore.GENERAL_ID
            // Belt & braces: never import into a sensitive section.
            if (sections.firstOrNull { it.id == sectionId }?.sensitive == true) {
                sectionId = ClipboardStore.GENERAL_ID
            }
            clips.add(
                Clip(
                    id = "import-$i-${text.hashCode()}",
                    sectionId = sectionId,
                    text = text,
                    pinned = o.optBoolean("pinned", false),
                    createdAt = System.currentTimeMillis()
                )
            )
        }

        val words = ArrayList<UserWord>()
        val wordArray = root.optJSONArray("userWords") ?: JSONArray()
        for (i in 0 until minOf(wordArray.length(), MAX_WORDS)) {
            val o = wordArray.optJSONObject(i) ?: continue
            val word = o.optString("word", "").trim().lowercase()
            if (word.length < 2 || word.length > 32) continue
            if (!word.all { it.isLetter() || it == '\'' }) continue
            words.add(
                UserWord(
                    word = word,
                    locale = o.optString("locale", "en").take(8),
                    frequency = o.optInt("frequency", 1).coerceIn(1, 1_000_000),
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
        return ParsedBackup(settings, locale, sections, clips, words)
    }
}
