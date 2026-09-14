package com.prototype.keyboard.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private const val STORE_NAME = "proto_keyboard_settings"
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = STORE_NAME)

enum class ThemeMode {
    SYSTEM, LIGHT, DARK;

    companion object {
        fun fromName(name: String?): ThemeMode =
            values().firstOrNull { it.name == name } ?: SYSTEM
    }
}

/** All user-tunable keyboard settings. Persisted via DataStore (private to the app). */
data class KeyboardSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val hapticFeedback: Boolean = true,
    /** 0..100 vibration strength. */
    val hapticStrength: Int = 50,
    val keypressSound: Boolean = false,
    /** Per-row key height in dp (clamped 48..72 by UI). */
    val keyHeightDp: Int = 56,
    val keyBorders: Boolean = true,
    val suggestionsEnabled: Boolean = true,
    /** Persisted now, enforced when the smart engine lands (Phase 3). */
    val autocorrectEnabled: Boolean = false,
    /** Persisted now, enforced when the smart engine lands (Phase 3). */
    val glideEnabled: Boolean = false,
    val autoCapsEnabled: Boolean = true,
    val doubleSpacePeriodEnabled: Boolean = true,
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val HAPTIC_FEEDBACK = booleanPreferencesKey("haptic_feedback")
        val HAPTIC_STRENGTH = intPreferencesKey("haptic_strength")
        val KEYPRESS_SOUND = booleanPreferencesKey("keypress_sound")
        val KEY_HEIGHT_DP = intPreferencesKey("key_height_dp")
        val KEY_BORDERS = booleanPreferencesKey("key_borders")
        val SUGGESTIONS = booleanPreferencesKey("suggestions")
        val AUTOCORRECT = booleanPreferencesKey("autocorrect")
        val GLIDE = booleanPreferencesKey("glide")
        val AUTO_CAPS = booleanPreferencesKey("auto_caps")
        val DOUBLE_SPACE_PERIOD = booleanPreferencesKey("double_space_period")
    }

    val settings: Flow<KeyboardSettings> =
        context.dataStore.data
            .catch { e ->
                // Corrupt / first-run store -> fall back to defaults instead of crashing.
                if (e is IOException) emit(androidx.datastore.preferences.core.emptyPreferences())
                else throw e
            }
            .map { prefs ->
                val defaults = KeyboardSettings()
                KeyboardSettings(
                    themeMode = ThemeMode.fromName(prefs[Keys.THEME_MODE]),
                    hapticFeedback = prefs[Keys.HAPTIC_FEEDBACK] ?: defaults.hapticFeedback,
                    hapticStrength = (prefs[Keys.HAPTIC_STRENGTH] ?: defaults.hapticStrength)
                        .coerceIn(0, 100),
                    keypressSound = prefs[Keys.KEYPRESS_SOUND] ?: defaults.keypressSound,
                    keyHeightDp = (prefs[Keys.KEY_HEIGHT_DP] ?: defaults.keyHeightDp)
                        .coerceIn(40, 80),
                    keyBorders = prefs[Keys.KEY_BORDERS] ?: defaults.keyBorders,
                    suggestionsEnabled = prefs[Keys.SUGGESTIONS] ?: defaults.suggestionsEnabled,
                    autocorrectEnabled = prefs[Keys.AUTOCORRECT] ?: defaults.autocorrectEnabled,
                    glideEnabled = prefs[Keys.GLIDE] ?: defaults.glideEnabled,
                    autoCapsEnabled = prefs[Keys.AUTO_CAPS] ?: defaults.autoCapsEnabled,
                    doubleSpacePeriodEnabled = prefs[Keys.DOUBLE_SPACE_PERIOD]
                        ?: defaults.doubleSpacePeriodEnabled,
                )
            }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setHapticFeedback(enabled: Boolean) {
        context.dataStore.edit { it[Keys.HAPTIC_FEEDBACK] = enabled }
    }

    suspend fun setHapticStrength(strength: Int) {
        context.dataStore.edit { it[Keys.HAPTIC_STRENGTH] = strength.coerceIn(0, 100) }
    }

    suspend fun setKeypressSound(enabled: Boolean) {
        context.dataStore.edit { it[Keys.KEYPRESS_SOUND] = enabled }
    }

    suspend fun setKeyHeightDp(dp: Int) {
        context.dataStore.edit { it[Keys.KEY_HEIGHT_DP] = dp.coerceIn(40, 80) }
    }

    suspend fun setKeyBorders(enabled: Boolean) {
        context.dataStore.edit { it[Keys.KEY_BORDERS] = enabled }
    }

    suspend fun setSuggestionsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SUGGESTIONS] = enabled }
    }

    suspend fun setAutocorrectEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTOCORRECT] = enabled }
    }

    suspend fun setGlideEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.GLIDE] = enabled }
    }

    suspend fun setAutoCapsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_CAPS] = enabled }
    }

    suspend fun setDoubleSpacePeriodEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DOUBLE_SPACE_PERIOD] = enabled }
    }

    suspend fun resetToDefaults() {
        context.dataStore.edit { it.clear() }
    }
}
