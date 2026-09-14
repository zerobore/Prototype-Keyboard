package com.prototype.keyboard.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

/** A word the keyboard learned from the user. Stays on-device. */
@Entity(tableName = "user_words")
data class UserWord(
    @PrimaryKey val word: String,
    val locale: String,
    val frequency: Int,
    val updatedAt: Long,
)

@Dao
interface UserWordDao {
    @Query("SELECT * FROM user_words WHERE locale = :locale ORDER BY frequency DESC, updatedAt DESC LIMIT :limit")
    suspend fun topForLocale(locale: String, limit: Int): List<UserWord>

    @Query("SELECT * FROM user_words ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<UserWord>

    @Query("SELECT frequency FROM user_words WHERE word = :word LIMIT 1")
    suspend fun frequencyOf(word: String): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(word: UserWord)

    @Query("DELETE FROM user_words WHERE word = :word")
    suspend fun delete(word: String)

    @Query("DELETE FROM user_words")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM user_words")
    suspend fun count(): Int

    @Query("DELETE FROM user_words WHERE word NOT IN (SELECT word FROM user_words ORDER BY frequency DESC, updatedAt DESC LIMIT :keep)")
    suspend fun trimTo(keep: Int)
}

@Database(entities = [UserWord::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userWords(): UserWordDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "proto_keyboard.db"
                ).build().also { instance = it }
            }

        /** In-memory DB for instrumented tests (IT-04). */
        fun inMemory(context: Context): AppDatabase =
            Room.inMemoryDatabaseBuilder(
                context.applicationContext,
                AppDatabase::class.java
            ).allowMainThreadQueries().build()
    }
}
