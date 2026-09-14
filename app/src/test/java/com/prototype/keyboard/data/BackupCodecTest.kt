package com.prototype.keyboard.data

import com.prototype.keyboard.data.db.UserWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** UT-38..UT-42 — backup codec incl. sensitive-clip exclusion (docs/TEST_PLAN.md). */
class BackupCodecTest {

    private fun sampleSections() = listOf(
        ClipSection(ClipboardStore.GENERAL_ID, "General", false),
        ClipSection(ClipboardStore.CODES_ID, "Codes", false),
        ClipSection("pw", "Passwords", true),
    )

    private fun sampleClips() = listOf(
        Clip("1", ClipboardStore.GENERAL_ID, "hello"),
        Clip("2", ClipboardStore.CODES_ID, "fun x() {}"),
        Clip("3", "pw", "s3cret-password"),
    )

    @Test
    fun `UT-38 round trip preserves data`() {
        val settings = KeyboardSettings(autocorrectEnabled = true, keyHeightDp = 64)
        val json = BackupCodec.export(
            settings, "es", sampleSections(), sampleClips(),
            listOf(UserWord("yaar", "en", 3, 123L))
        )
        val parsed = BackupCodec.parse(json)
        assertEquals("es", parsed.locale)
        assertTrue(parsed.settings.autocorrectEnabled)
        assertEquals(64, parsed.settings.keyHeightDp)
        assertEquals(3, parsed.sections.size)
        assertEquals(2, parsed.clips.size) // sensitive clip excluded
        assertEquals("yaar", parsed.userWords.single().word)
        assertEquals(3, parsed.userWords.single().frequency)
    }

    @Test
    fun `UT-39 sensitive clips never appear in export bytes`() {
        val json = BackupCodec.export(
            KeyboardSettings(), "en", sampleSections(), sampleClips(), emptyList()
        )
        assertFalse(json.contains("s3cret-password"))
    }

    @Test
    fun `UT-40 corrupt json is rejected`() {
        try {
            BackupCodec.parse("{nope")
            fail("expected BackupException")
        } catch (e: BackupCodec.BackupException) {
            assertTrue(e.message!!.contains("valid"))
        }
    }

    @Test
    fun `UT-41 wrong version is rejected`() {
        try {
            BackupCodec.parse("{\"version\":99}")
            fail("expected BackupException")
        } catch (e: BackupCodec.BackupException) {
            assertTrue(e.message!!.contains("version"))
        }
    }

    @Test
    fun `UT-42 oversize file is rejected`() {
        try {
            BackupCodec.parse("x".repeat(BackupCodec.MAX_BYTES + 1))
            fail("expected BackupException")
        } catch (e: BackupCodec.BackupException) {
            assertTrue(e.message!!.contains("large"))
        }
    }
}
