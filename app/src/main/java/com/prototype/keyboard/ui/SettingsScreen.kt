package com.prototype.keyboard.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.prototype.keyboard.data.BackupCodec
import com.prototype.keyboard.data.ClipboardRepository
import com.prototype.keyboard.data.ClipboardStore
import com.prototype.keyboard.data.KeyboardSettings
import com.prototype.keyboard.data.SettingsRepository
import com.prototype.keyboard.data.ThemeMode
import com.prototype.keyboard.data.UserDictionary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

@Composable
fun SettingsScreen(
    settings: KeyboardSettings,
    repo: SettingsRepository,
    userDict: UserDictionary,
    clipboardRepo: ClipboardRepository,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var backupStatus by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val snapshot = settings
        scope.launch(Dispatchers.IO) {
            try {
                val store = clipboardRepo.load()
                val words = userDict.recent(UserDictionary.MAX_WORDS)
                val json = BackupCodec.export(
                    snapshot, snapshot.currentLocale,
                    store.sections, store.clips, words
                )
                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(json.toByteArray(Charsets.UTF_8))
                } ?: throw IOException("Could not open file")
                withContext(Dispatchers.Main) {
                    backupStatus = "Exported ✓ (sensitive clips never included)"
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    backupStatus = "Export failed: ${e.message}"
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            try {
                val json = context.contentResolver.openInputStream(uri)?.use {
                    it.readBytes().toString(Charsets.UTF_8)
                } ?: throw IOException("Could not open file")
                val backup = BackupCodec.parse(json)
                applyBackup(backup, repo, userDict, clipboardRepo)
                withContext(Dispatchers.Main) {
                    backupStatus = "Imported ✓ (settings replaced · words merged · clips replaced)"
                }
            } catch (e: BackupCodec.BackupException) {
                withContext(Dispatchers.Main) { backupStatus = "Import rejected: ${e.message}" }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { backupStatus = "Import failed: ${e.message}" }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)

        Section("Appearance") {
            Text("Theme", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = settings.themeMode == mode,
                        onClick = { scope.launch { repo.setThemeMode(mode) } },
                        label = {
                            Text(
                                when (mode) {
                                    ThemeMode.SYSTEM -> "System"
                                    ThemeMode.LIGHT -> "Light"
                                    ThemeMode.DARK -> "Dark"
                                }
                            )
                        }
                    )
                }
            }

            var height by remember(settings.keyHeightDp) {
                mutableFloatStateOf(settings.keyHeightDp.toFloat())
            }
            Text(
                "Key height · ${height.toInt()} dp",
                style = MaterialTheme.typography.titleSmall
            )
            Slider(
                value = height,
                onValueChange = { height = it },
                valueRange = 48f..72f,
                steps = 11,
                onValueChangeFinished = { scope.launch { repo.setKeyHeightDp(height.toInt()) } }
            )

            SettingSwitch(
                title = "Key borders",
                description = "Rounded key outlines (off = flat look).",
                checked = settings.keyBorders,
                onChecked = { scope.launch { repo.setKeyBorders(it) } }
            )
        }

        Section("Feedback") {
            SettingSwitch(
                title = "Haptic feedback",
                description = "Vibrate lightly on each keypress.",
                checked = settings.hapticFeedback,
                onChecked = { scope.launch { repo.setHapticFeedback(it) } }
            )
            var strength by remember(settings.hapticStrength) {
                mutableFloatStateOf(settings.hapticStrength.toFloat())
            }
            Text(
                "Vibration strength · ${strength.toInt()}",
                style = MaterialTheme.typography.titleSmall
            )
            Slider(
                value = strength,
                onValueChange = { strength = it },
                valueRange = 0f..100f,
                steps = 9,
                enabled = settings.hapticFeedback,
                onValueChangeFinished = { scope.launch { repo.setHapticStrength(strength.toInt()) } }
            )
            SettingSwitch(
                title = "Keypress sound",
                description = "System click sound on each key.",
                checked = settings.keypressSound,
                onChecked = { scope.launch { repo.setKeypressSound(it) } }
            )
        }

        Section("Typing") {
            SettingSwitch(
                title = "Auto-capitalization",
                description = "Capitalize the start of sentences.",
                checked = settings.autoCapsEnabled,
                onChecked = { scope.launch { repo.setAutoCapsEnabled(it) } }
            )
            SettingSwitch(
                title = "Double-space period",
                description = "Two spaces insert “. ”.",
                checked = settings.doubleSpacePeriodEnabled,
                onChecked = { scope.launch { repo.setDoubleSpacePeriodEnabled(it) } }
            )
        }

        Section("Smart features (all on-device)") {
            SettingSwitch(
                title = "Suggestions",
                description = "Completions + next-word predictions.",
                checked = settings.suggestionsEnabled,
                onChecked = { scope.launch { repo.setSuggestionsEnabled(it) } }
            )
            SettingSwitch(
                title = "Autocorrect",
                description = "Fix on space. One backspace reverts.",
                checked = settings.autocorrectEnabled,
                onChecked = { scope.launch { repo.setAutocorrectEnabled(it) } }
            )
            SettingSwitch(
                title = "Glide typing",
                description = "Slide across letters (English).",
                checked = settings.glideEnabled,
                onChecked = { scope.launch { repo.setGlideEnabled(it) } }
            )
            SettingSwitch(
                title = "Learn new words",
                description = "Private on-device dictionary. Never in password fields.",
                checked = settings.learningEnabled,
                onChecked = { scope.launch { repo.setLearningEnabled(it) } }
            )
        }

        Section("Clipboard") {
            SettingSwitch(
                title = "Auto-save copies",
                description = "Copied text lands in sections automatically.",
                checked = settings.clipboardCaptureEnabled,
                onChecked = { scope.launch { repo.setClipboardCaptureEnabled(it) } }
            )
            SettingSwitch(
                title = "Quick-paste chip",
                description = "Persistent paste button in the strip.",
                checked = settings.quickPasteEnabled,
                onChecked = { scope.launch { repo.setQuickPasteEnabled(it) } }
            )
            Text(
                "Sensitive sections (e.g. Passwords) mask clips, auto-expire in 10 min, and are never exported.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Section("Backup & data") {
            Text(
                "Everything stays on this device. Export creates one file you control — import any time.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { exportLauncher.launch("prototype-keyboard-backup.json") }) {
                    Text("Export")
                }
                OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json")) }) {
                    Text("Import")
                }
            }
            backupStatus?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
            OutlinedButton(onClick = { scope.launch { repo.resetToDefaults() } }) {
                Text("Reset settings to defaults")
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

private suspend fun applyBackup(
    backup: BackupCodec.ParsedBackup,
    repo: SettingsRepository,
    userDict: UserDictionary,
    clipboardRepo: ClipboardRepository,
) {
    val s = backup.settings
    repo.setThemeMode(s.themeMode)
    repo.setHapticFeedback(s.hapticFeedback)
    repo.setHapticStrength(s.hapticStrength)
    repo.setKeypressSound(s.keypressSound)
    repo.setKeyHeightDp(s.keyHeightDp)
    repo.setKeyBorders(s.keyBorders)
    repo.setSuggestionsEnabled(s.suggestionsEnabled)
    repo.setAutocorrectEnabled(s.autocorrectEnabled)
    repo.setGlideEnabled(s.glideEnabled)
    repo.setAutoCapsEnabled(s.autoCapsEnabled)
    repo.setDoubleSpacePeriodEnabled(s.doubleSpacePeriodEnabled)
    repo.setQuickPasteEnabled(s.quickPasteEnabled)
    repo.setClipboardCaptureEnabled(s.clipboardCaptureEnabled)
    repo.setLearningEnabled(s.learningEnabled)
    repo.setCurrentLocale(backup.locale)
    userDict.restore(backup.userWords)
    clipboardRepo.save(ClipboardStore(backup.sections, backup.clips))
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    description: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}
