package com.prototype.keyboard

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.prototype.keyboard.data.UserDictionary
import com.prototype.keyboard.data.db.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * IT-04: learned-words database CRUD on an in-memory Room database.
 * Runs on the CI emulator via :app:connectedDebugAndroidTest.
 */
@RunWith(AndroidJUnit4::class)
class UserDictionaryInstrumentedTest {

    private lateinit var db: AppDatabase
    private lateinit var dict: UserDictionary

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = AppDatabase.inMemory(context)
        dict = UserDictionary(db)
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun learnCountsFrequency() = runBlocking {
        dict.learn("yaar", "en")
        dict.learn("yaar", "en")
        dict.learn("namaste", "en")
        val top = dict.wordsForLocale("en")
        assertEquals(2, top["yaar"])
        assertEquals(1, top["namaste"])
    }

    @Test
    fun invalidWordsIgnored() = runBlocking {
        dict.learn("x", "en")
        dict.learn("", "en")
        dict.learn("ok!", "en")
        assertTrue(dict.wordsForLocale("en").isEmpty())
    }

    @Test
    fun deleteAndClear() = runBlocking {
        dict.learn("hello", "en")
        dict.delete("hello")
        assertTrue(dict.wordsForLocale("en").isEmpty())
        dict.learn("ab", "en")
        dict.clear()
        assertTrue(dict.wordsForLocale("en").isEmpty())
    }
}
