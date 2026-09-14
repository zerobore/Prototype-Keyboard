package com.prototype.keyboard.ui

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
import androidx.compose.material3.Button
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.prototype.keyboard.data.KeyboardSettings
import com.prototype.keyboard.data.SettingsRepository
import com.prototype.keyboard.data.ThemeMode
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(settings: KeyboardSettings, repo: SettingsRepository) {
    val scope = rememberCoroutineScope()
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
                title = "Suggestions strip",
                description = "Show the strip above the keys.",
                checked = settings.suggestionsEnabled,
                onChecked = { scope.launch { repo.setSuggestionsEnabled(it) } }
            )
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
            SettingSwitch(
                title = "Autocorrect",
                description = "Saved now · takes effect with the Phase 3 smart engine.",
                checked = settings.autocorrectEnabled,
                onChecked = { scope.launch { repo.setAutocorrectEnabled(it) } }
            )
            SettingSwitch(
                title = "Glide typing",
                description = "Saved now · takes effect with the Phase 3 smart engine.",
                checked = settings.glideEnabled,
                onChecked = { scope.launch { repo.setGlideEnabled(it) } }
            )
        }

        Section("Data") {
            Text(
                "Settings stay on this device. Backup / restore arrives in Phase 3.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(onClick = { scope.launch { repo.resetToDefaults() } }) {
                Text("Reset to defaults")
            }
        }

        Spacer(Modifier.height(8.dp))
    }
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
