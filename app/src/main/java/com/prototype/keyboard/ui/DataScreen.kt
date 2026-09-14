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
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.prototype.keyboard.data.ClipSection
import com.prototype.keyboard.data.ClipboardRepository
import com.prototype.keyboard.data.UserDictionary
import com.prototype.keyboard.data.db.UserWord
import kotlinx.coroutines.launch

/**
 * Data tab: learned words + clipboard sections management.
 * Everything here lives on-device; sensitive sections are never exported.
 */
@Composable
fun DataScreen(userDict: UserDictionary, clipboardRepo: ClipboardRepository) {
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    var words by remember { mutableStateOf<List<UserWord>>(emptyList()) }
    var sections by remember { mutableStateOf<List<ClipSection>>(emptyList()) }
    var counts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var newWord by remember { mutableStateOf("") }
    var newSection by remember { mutableStateOf("") }
    var newSensitive by remember { mutableStateOf(false) }

    LaunchedEffect(tick) {
        words = userDict.recent(100)
        val store = clipboardRepo.load()
        sections = store.sections
        counts = store.sections.associate { it.id to store.clipsIn(it.id).size }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Your data", style = MaterialTheme.typography.headlineMedium)
        Text(
            "On this device only. Nothing uploads anywhere.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Learned words (${words.size})", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newWord,
                        onValueChange = { newWord = it },
                        label = { Text("Add a word") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(onClick = {
                        val word = newWord.trim()
                        if (word.isNotEmpty()) {
                            scope.launch {
                                userDict.learn(word, "en")
                                userDict.learn(word, "es")
                                userDict.learn(word, "de")
                                userDict.learn(word, "fr")
                                newWord = ""
                                tick++
                            }
                        }
                    }) { Text("Add") }
                }
                if (words.isEmpty()) {
                    Text(
                        "No learned words yet. Type with learning on, or add some here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                words.take(30).forEach { entry ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${entry.word}  ·  ×${entry.frequency}")
                        OutlinedButton(onClick = {
                            scope.launch { userDict.delete(entry.word); tick++ }
                        }) { Text("🗑") }
                    }
                }
                if (words.isNotEmpty()) {
                    OutlinedButton(onClick = { scope.launch { userDict.clear(); tick++ } }) {
                        Text("Clear all learned words")
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Clipboard sections", style = MaterialTheme.typography.titleMedium)
                sections.forEach { section ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            (if (section.sensitive) "🔒 " else "") +
                                "${section.name} (${counts[section.id] ?: 0})"
                        )
                        if (section.id != "general") {
                            OutlinedButton(onClick = {
                                scope.launch {
                                    clipboardRepo.mutate { it.removeSection(section.id) }
                                    tick++
                                }
                            }) { Text("🗑") }
                        }
                    }
                }
                OutlinedTextField(
                    value = newSection,
                    onValueChange = { newSection = it },
                    label = { Text("New section (e.g. Passwords)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = newSensitive, onCheckedChange = { newSensitive = it })
                    Text("Sensitive (masked · auto-expires · never exported)")
                }
                OutlinedButton(onClick = {
                    val name = newSection.trim()
                    if (name.isNotEmpty()) {
                        scope.launch {
                            clipboardRepo.mutate { it.addSection(name, newSensitive) }
                            newSection = ""
                            newSensitive = false
                            tick++
                        }
                    }
                }) { Text("Add section") }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
