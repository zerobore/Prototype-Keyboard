package com.prototype.keyboard.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** UT-43..UT-50 — clipboard sections store (docs/TEST_PLAN.md). */
class ClipboardStoreTest {

    @Test
    fun `UT-43 add and latest pasteable`() {
        val store = ClipboardStore()
        assertNull(store.latestPasteable())
        store.addClip("hello")
        assertEquals("hello", store.latestPasteable()?.text)
    }

    @Test
    fun `UT-44 re-adding bumps to top without duplicates`() {
        val store = ClipboardStore()
        store.addClip("a")
        store.addClip("b")
        store.addClip("a")
        assertEquals("a", store.latestPasteable()?.text)
        assertEquals(2, store.clips.size)
    }

    @Test
    fun `UT-45 pinned clips sort first`() {
        val store = ClipboardStore()
        store.addClip("a")
        val b = store.addClip("b")!!
        store.togglePin(b.id)
        assertEquals("b", store.clips.first().text)
        assertTrue(store.clips.first().pinned)
    }

    @Test
    fun `UT-46 links and code auto-route to sections`() {
        val store = ClipboardStore()
        val link = store.addClip("https://example.com/x")!!
        val code = store.addClip("fun main() {\n println()\n}")!!
        val plain = store.addClip("hello")!!
        assertEquals(ClipboardStore.LINKS_ID, link.sectionId)
        assertEquals(ClipboardStore.CODES_ID, code.sectionId)
        assertEquals(ClipboardStore.GENERAL_ID, plain.sectionId)
        assertFalse(ClipboardStore.looksLikeCode("plain"))
        assertTrue(ClipboardStore.looksLikeCode("if (x) {\n}"))
    }

    @Test
    fun `UT-47 sections add, dedupe and protect general`() {
        val store = ClipboardStore()
        val created = store.addSection("Passwords", true)
        assertTrue(created != null && created.sensitive)
        assertNull(store.addSection("passwords", false)) // duplicate name
        assertFalse(store.removeSection(ClipboardStore.GENERAL_ID))
        assertTrue(store.removeSection(created!!.id))
        // Removing a section removes its clips.
        val temp = store.addSection("Temp", false)!!
        store.addClip("x", temp.id)
        assertTrue(store.clips.any { it.sectionId == temp.id })
        store.removeSection(temp.id)
        assertFalse(store.clips.any { it.sectionId == temp.id })
    }

    @Test
    fun `UT-48 move clip between sections`() {
        val store = ClipboardStore()
        val clip = store.addClip("secret", ClipboardStore.GENERAL_ID)!!
        val vault = store.addSection("Vault", true)!!
        assertTrue(store.moveClip(clip.id, vault.id))
        assertEquals(vault.id, store.clips.first { it.id == clip.id }.sectionId)
        assertFalse(store.moveClip(clip.id, "missing"))
    }

    @Test
    fun `UT-49 sensitive unpinned clips expire`() {
        val vault = ClipSection("v", "Vault", true)
        val old = Clip("old", "v", "old-secret", pinned = false, createdAt = 0L)
        val pinned = Clip("pin", "v", "kept", pinned = true, createdAt = 0L)
        val fresh = Clip("fresh", ClipboardStore.GENERAL_ID, "hi", createdAt = 1000L)
        val store = ClipboardStore(
            ClipboardStore.defaultSections() + vault,
            listOf(old, pinned, fresh)
        )
        val removed = store.purgeExpiredSensitive(
            ClipboardStore.SENSITIVE_TTL_MS + 100L
        )
        assertEquals(1, removed)
        assertTrue(store.clips.any { it.id == "pin" })
        assertTrue(store.clips.any { it.id == "fresh" })
    }

    @Test
    fun `UT-50 clear section keeps pinned by default`() {
        val store = ClipboardStore()
        store.addClip("a")
        val b = store.addClip("b")!!
        store.togglePin(b.id)
        store.clearSection(ClipboardStore.GENERAL_ID)
        assertEquals(listOf("b"), store.clips.map { it.text })
    }
}
