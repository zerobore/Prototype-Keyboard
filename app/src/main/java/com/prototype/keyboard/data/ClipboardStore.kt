package com.prototype.keyboard.data

import java.util.UUID

/**
 * Clipboard sections + clips. Pure Kotlin (unit-tested in ClipboardStoreTest);
 * [ClipboardRepository] handles JSON persistence.
 *
 * Sections marked [ClipSection.sensitive] (e.g. Passwords) are auto-expired,
 * masked in the UI until explicitly revealed, and NEVER included in exports.
 */
data class ClipSection(
    val id: String,
    val name: String,
    val sensitive: Boolean,
    val createdAt: Long = System.currentTimeMillis(),
)

data class Clip(
    val id: String,
    val sectionId: String,
    val text: String,
    val pinned: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

class ClipboardStore(
    sections: List<ClipSection> = defaultSections(),
    clips: List<Clip> = emptyList(),
) {
    private val sectionList = sections.toMutableList()
    private val clipList = clips.toMutableList()

    init {
        sortClips()
    }

    val sections: List<ClipSection> get() = sectionList.toList()
    val clips: List<Clip> get() = clipList.toList()

    fun section(id: String): ClipSection? = sectionList.firstOrNull { it.id == id }

    fun isSensitiveSection(sectionId: String): Boolean =
        sectionList.firstOrNull { it.id == sectionId }?.sensitive == true

    fun clipsIn(sectionId: String): List<Clip> = clipList.filter { it.sectionId == sectionId }

    /** Most recent clip (quick-paste source). Explicit tap pastes; never auto-pastes. */
    fun latestPasteable(): Clip? = clipList.firstOrNull()

    fun addSection(name: String, sensitive: Boolean): ClipSection? {
        val clean = name.trim().take(MAX_SECTION_NAME)
        if (clean.isEmpty() || sectionList.size >= MAX_SECTIONS) return null
        if (sectionList.any { it.name.equals(clean, ignoreCase = true) }) return null
        val section = ClipSection(UUID.randomUUID().toString(), clean, sensitive)
        sectionList.add(section)
        return section
    }

    /** Deletes a section and its clips. The General section is protected. */
    fun removeSection(id: String): Boolean {
        if (id == GENERAL_ID) return false
        if (!sectionList.removeAll { it.id == id }) return false
        clipList.removeAll { it.sectionId == id }
        return true
    }

    fun addClip(text: String, sectionId: String? = null): Clip? {
        val clean = text.take(MAX_CLIP_CHARS)
        if (clean.isBlank()) return null
        // Dedupe: re-adding bumps the existing clip to the top.
        existing(clean)?.let {
            clipList.remove(it)
            val bumped = it.copy(createdAt = System.currentTimeMillis())
            clipList.add(bumped)
            sortClips()
            return bumped
        }
        val target = sectionId?.takeIf { sid -> sectionList.any { it.id == sid } }
            ?: autoSection(clean)
        val clip = Clip(UUID.randomUUID().toString(), target, clean)
        clipList.add(clip)
        trimOverflow()
        sortClips()
        return clip
    }

    /** Move a clip to another section (organizing). */
    fun moveClip(clipId: String, toSectionId: String): Boolean {
        if (sectionList.none { it.id == toSectionId }) return false
        val index = clipList.indexOfFirst { it.id == clipId }
        if (index < 0) return false
        clipList[index] = clipList[index].copy(sectionId = toSectionId)
        return true
    }

    fun togglePin(clipId: String): Boolean {
        val index = clipList.indexOfFirst { it.id == clipId }
        if (index < 0) return false
        clipList[index] = clipList[index].copy(pinned = !clipList[index].pinned)
        sortClips()
        return true
    }

    fun deleteClip(clipId: String): Boolean = clipList.removeAll { it.id == clipId }

    /** Pasted clips float back to the top (quick-paste stays useful). */
    fun touch(clipId: String) {
        val index = clipList.indexOfFirst { it.id == clipId }
        if (index < 0) return
        clipList[index] = clipList[index].copy(createdAt = System.currentTimeMillis())
        sortClips()
    }

    fun clearSection(sectionId: String, keepPinned: Boolean = true): Int {
        val before = clipList.size
        clipList.removeAll { it.sectionId == sectionId && (!keepPinned || !it.pinned) }
        return before - clipList.size
    }

    /**
     * Purge unpinned clips in sensitive sections older than [ttlMs].
     * Returns the number removed.
     */
    fun purgeExpiredSensitive(nowMs: Long, ttlMs: Long = SENSITIVE_TTL_MS): Int {
        val before = clipList.size
        clipList.removeAll { clip ->
            !clip.pinned && isSensitiveSection(clip.sectionId) && nowMs - clip.createdAt > ttlMs
        }
        return before - clipList.size
    }

    private fun existing(text: String): Clip? = clipList.firstOrNull { it.text == text }

    private fun autoSection(text: String): String {
        val trimmed = text.trim()
        if (looksLikeLink(trimmed) && sectionList.any { it.id == LINKS_ID }) return LINKS_ID
        if (looksLikeCode(trimmed) && sectionList.any { it.id == CODES_ID }) return CODES_ID
        return GENERAL_ID
    }

    private fun trimOverflow() {
        while (clipList.size > MAX_CLIPS) {
            // Evict the oldest unpinned clip.
            val victim = clipList.filterNot { it.pinned }.minByOrNull { it.createdAt }
                ?: break
            clipList.remove(victim)
        }
    }

    private fun sortClips() {
        clipList.sortWith(compareByDescending<Clip> { it.pinned }.thenByDescending { it.createdAt })
    }

    companion object {
        const val GENERAL_ID = "general"
        const val CODES_ID = "codes"
        const val LINKS_ID = "links"

        const val MAX_CLIPS = 100
        const val MAX_SECTIONS = 12
        const val MAX_CLIP_CHARS = 20_000
        const val MAX_SECTION_NAME = 24

        /** Sensitive clips auto-expire after 10 minutes (unpinned). */
        const val SENSITIVE_TTL_MS = 10L * 60L * 1000L

        fun defaultSections(): List<ClipSection> = listOf(
            ClipSection(GENERAL_ID, "General", false),
            ClipSection(CODES_ID, "Codes", false),
            ClipSection(LINKS_ID, "Links", false),
        )

        fun looksLikeLink(text: String): Boolean {
            val t = text.trim()
            return t.startsWith("http://") || t.startsWith("https://") || t.startsWith("www.")
        }

        fun looksLikeCode(text: String): Boolean {
            if (!text.contains('\n')) return false
            return text.contains('{') || text.contains(';') || text.contains("==") ||
                text.contains("fun ") || text.contains("def ") || text.contains("class ") ||
                text.contains("import ") || text.contains("=>") || text.contains("&&")
        }
    }
}
