package com.prototype.keyboard.data

import com.prototype.keyboard.data.db.AppDatabase
import com.prototype.keyboard.data.db.UserWord

/**
 * Learned-words repository (on-device Room database).
 * The IME calls [learn] for typed words; suggestions merge these above the
 * bundled dictionary. Password fields never reach here (gated in ProtoIME).
 */
class UserDictionary(private val db: AppDatabase) {

    suspend fun learn(raw: String, locale: String) {
        val word = raw.trim().lowercase()
        if (word.length < MIN_LEN || word.length > MAX_LEN) return
        if (!word.all { it.isLetter() || it == '\'' }) return
        val dao = db.userWords()
        val frequency = (dao.frequencyOf(word) ?: 0) + 1
        dao.upsert(UserWord(word, locale, frequency, System.currentTimeMillis()))
        if (dao.count() > MAX_WORDS) dao.trimTo(MAX_WORDS)
    }

    suspend fun wordsForLocale(locale: String, limit: Int = 500): Map<String, Int> =
        db.userWords().topForLocale(locale, limit).associate { it.word to it.frequency }

    suspend fun recent(limit: Int = 100): List<UserWord> = db.userWords().recent(limit)

    suspend fun delete(word: String) = db.userWords().delete(word)

    suspend fun clear() = db.userWords().clear()

    /** Backup import: merge (upsert), capped. */
    suspend fun restore(words: List<UserWord>) {
        val dao = db.userWords()
        words.take(MAX_WORDS).forEach { dao.upsert(it) }
        if (dao.count() > MAX_WORDS) dao.trimTo(MAX_WORDS)
    }

    companion object {
        const val MAX_WORDS = 2000
        private const val MIN_LEN = 2
        private const val MAX_LEN = 32
    }
}
