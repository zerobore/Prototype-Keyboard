package com.prototype.keyboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.prototype.keyboard.plugins.AiPrompt
import com.prototype.keyboard.plugins.DictionaryPack
import com.prototype.keyboard.plugins.PackCodec
import com.prototype.keyboard.plugins.PackKind
import com.prototype.keyboard.plugins.PluginApi
import com.prototype.keyboard.plugins.PluginManager
import com.prototype.keyboard.plugins.PluginManifest
import com.prototype.keyboard.plugins.PromptPack
import com.prototype.keyboard.plugins.TextOp
import com.prototype.keyboard.plugins.TextOps
import com.prototype.keyboard.plugins.TextTool
import com.prototype.keyboard.plugins.TextToolPack
import com.prototype.keyboard.plugins.ThemeColors
import com.prototype.keyboard.plugins.ThemePack
import kotlinx.coroutines.launch

/** Shared manifest form state for all builders. */
private class ManifestFormState(kind: PackKind) {
    var id by mutableStateOf("com.you.${kind.name.lowercase()}.my-pack")
    var name by mutableStateOf("")
    var author by mutableStateOf("")
    var version by mutableStateOf("1.0.0")
    var description by mutableStateOf("")

    fun build(kind: PackKind) = PluginManifest(
        id = id.trim(), name = name.trim(), author = author.trim(),
        version = version.trim(), pluginApiVersion = PluginApi.PLUGIN_API_VERSION,
        minHostVersion = PluginApi.HOST_VERSION, kind = kind,
        description = description.trim()
    )
}

@Composable
fun PackBuilderScreen(
    kind: PackKind,
    pluginManager: PluginManager,
    onDone: () -> Unit,
) {
    val manifest = remember(kind) { ManifestFormState(kind) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onDone) { Text("← Packs") }
            Text(
                when (kind) {
                    PackKind.THEME -> "New theme"
                    PackKind.DICTIONARY -> "New dictionary"
                    PackKind.PROMPTS -> "New prompt pack"
                    PackKind.TOOLS -> "New tool pack"
                },
                style = MaterialTheme.typography.headlineSmall
            )
        }
        ManifestFields(manifest)
        when (kind) {
            PackKind.THEME -> ThemeBuilder(manifest, pluginManager, onDone)
            PackKind.DICTIONARY -> DictionaryBuilder(manifest, pluginManager, onDone)
            PackKind.PROMPTS -> PromptsBuilder(manifest, pluginManager, onDone)
            PackKind.TOOLS -> ToolsBuilder(manifest, pluginManager, onDone)
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ManifestFields(state: ManifestFormState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("About this pack", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(value = state.name, onValueChange = { state.name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = state.id, onValueChange = { state.id = it }, label = { Text("ID (reverse-dns)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = state.author, onValueChange = { state.author = it }, label = { Text("Author") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(value = state.version, onValueChange = { state.version = it }, label = { Text("Version") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            OutlinedTextField(value = state.description, onValueChange = { state.description = it }, label = { Text("Description (optional)") }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun SaveStatus(status: String?) {
    status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
}

// ---------------- Theme ----------------

private class ThemeFormState {
    val day = mutableStateListOf("#D8DCE3", "#FFFFFF", "#B9C0CB", "#9FB4D8", "#1F1F1F", "#0B57D0")
    val night = mutableStateListOf("#28292A", "#3E3F42", "#2F3033", "#5F6368", "#E8EAED", "#A8C7FA")

    fun colors(list: List<String>) = ThemeColors(
        background = list[0].trim(), key = list[1].trim(), funcKey = list[2].trim(),
        pressedKey = list[3].trim(), text = list[4].trim(), accent = list[5].trim()
    )
}

private val COLOR_LABELS = listOf("Background", "Key", "Function key", "Pressed", "Text", "Accent")

@Composable
private fun ThemeBuilder(manifest: ManifestFormState, pluginManager: PluginManager, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    val form = remember { ThemeFormState() }
    var radius by remember { mutableFloatStateOf(6f) }
    var showNight by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    @Composable
    fun ColorSection(title: String, values: List<String>, onChange: (Int, String) -> Unit) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                values.forEachIndexed { i, value ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        val parsed = runCatching { Color(android.graphics.Color.parseColor(value.trim())) }.getOrNull()
                        Box(
                            Modifier.size(28.dp).background(
                                parsed ?: Color.Transparent, RoundedCornerShape(6.dp)
                            )
                        )
                        OutlinedTextField(
                            value = value, onValueChange = { onChange(i, it) },
                            label = { Text(COLOR_LABELS[i]) }, singleLine = true,
                            modifier = Modifier.weight(1f),
                            isError = parsed == null
                        )
                    }
                }
            }
        }
    }

    ColorSection("Day colors", form.day) { i, v -> form.day[i] = v }
    ColorSection("Night colors", form.night) { i, v -> form.night[i] = v }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Shape", style = MaterialTheme.typography.titleMedium)
            Text("Corner radius · ${radius.toInt()} dp", style = MaterialTheme.typography.bodySmall)
            Slider(value = radius, onValueChange = { radius = it }, valueRange = 0f..16f, steps = 15)
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !showNight, onClick = { showNight = false }, label = { Text("Day") })
                FilterChip(selected = showNight, onClick = { showNight = true }, label = { Text("Night") })
            }
            ThemePreview(form.colors(form.day), form.colors(form.night), showNight)
        }
    }

    SaveStatus(status)
    Button(onClick = {
        scope.launch {
            try {
                val pack = ThemePack(manifest.build(PackKind.THEME), form.colors(form.day), form.colors(form.night), radius.toInt())
                val saved = pluginManager.importPack(PackCodec.encodeTheme(pack))
                status = "Installed ${saved.name} ✓ — pick it in Studio → Themes"
                onDone()
            } catch (e: PackCodec.PackException) {
                status = "Fix: ${e.message}"
            } catch (e: Exception) {
                status = "Save failed: ${e.message}"
            }
        }
    }) { Text("Save theme pack") }
}

@Composable
private fun ThemePreview(day: ThemeColors, night: ThemeColors, showNight: Boolean) {
    val c = if (showNight) night else day
    fun col(hex: String): Color =
        runCatching { Color(android.graphics.Color.parseColor(hex.trim())) }.getOrNull() ?: Color.Gray
    Box(Modifier.fillMaxWidth().background(col(c.background), RoundedCornerShape(8.dp)).padding(8.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(2) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(5) {
                        Box(Modifier.weight(1f).height(26.dp).background(col(c.key), RoundedCornerShape(6.dp)))
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(26.dp).background(col(c.accent), RoundedCornerShape(6.dp)))
        }
    }
}

// ---------------- Dictionary ----------------

@Composable
private fun DictionaryBuilder(manifest: ManifestFormState, pluginManager: PluginManager, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var locale by remember { mutableStateOf("en") }
    var text by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }

    val parsed = remember(text) {
        text.lines().map { it.trim().lowercase() }.filter { it.isNotEmpty() && !it.startsWith("#") }
    }
    val valid = remember(parsed) {
        parsed.filter { it.length in 2..PackCodec.MAX_WORD_LEN && it.all { c -> c.isLetter() || c == '\'' } }.distinct()
    }
    val invalid = remember(parsed) { (parsed - valid.toSet()).distinct().take(5) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Language", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("en", "es", "de", "fr", "hi").forEach { code ->
                    FilterChip(selected = locale == code, onClick = { locale = code }, label = { Text(code) })
                }
            }
        }
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Words (one per line)", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(value = text, onValueChange = { text = it }, minLines = 6, modifier = Modifier.fillMaxWidth())
            Text("${valid.size} valid · ${parsed.size - valid.size} skipped", style = MaterialTheme.typography.bodySmall)
            if (invalid.isNotEmpty()) {
                Text("Skipped: ${invalid.joinToString(", ")}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    SaveStatus(status)
    Button(onClick = {
        scope.launch {
            try {
                if (valid.isEmpty()) throw PackCodec.PackException("Add at least one valid word")
                val pack = DictionaryPack(manifest.build(PackKind.DICTIONARY), locale, valid)
                val saved = pluginManager.importPack(PackCodec.encodeDictionary(pack))
                status = "Installed ${saved.name} ✓"
                onDone()
            } catch (e: PackCodec.PackException) {
                status = "Fix: ${e.message}"
            } catch (e: Exception) {
                status = "Save failed: ${e.message}"
            }
        }
    }) { Text("Save dictionary pack") }
}

// ---------------- Prompts ----------------

private class PromptDraft {
    var title by mutableStateOf("")
    var template by mutableStateOf("")
    var description by mutableStateOf("")
}

@Composable
private fun PromptsBuilder(manifest: ManifestFormState, pluginManager: PluginManager, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    val drafts = remember { mutableStateListOf(PromptDraft()) }
    var status by remember { mutableStateOf<String?>(null) }

    Text(
        "Use {{text}} for the selected text and {{locale}} for the keyboard language. The AI runner arrives in Phase 3b.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    drafts.forEachIndexed { index, draft ->
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Prompt ${index + 1}", style = MaterialTheme.typography.titleMedium)
                    if (drafts.size > 1) {
                        OutlinedButton(onClick = { drafts.removeAt(index) }) { Text("Remove") }
                    }
                }
                OutlinedTextField(value = draft.title, onValueChange = { draft.title = it }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = draft.template, onValueChange = { draft.template = it }, label = { Text("Template") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = draft.description, onValueChange = { draft.description = it }, label = { Text("Description (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        }
    }
    OutlinedButton(onClick = { if (drafts.size < 20) drafts.add(PromptDraft()) }) { Text("+ Add prompt") }
    SaveStatus(status)
    Button(onClick = {
        scope.launch {
            try {
                val prompts = drafts.mapIndexed { i, d ->
                    val slug = d.title.trim().lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
                    AiPrompt(
                        id = (slug.ifEmpty { "prompt" }) + "-${i + 1}",
                        title = d.title.trim(), template = d.template.trim(),
                        description = d.description.trim()
                    )
                }
                val pack = PromptPack(manifest.build(PackKind.PROMPTS), prompts)
                val saved = pluginManager.importPack(PackCodec.encodePrompts(pack))
                status = "Installed ${saved.name} ✓"
                onDone()
            } catch (e: PackCodec.PackException) {
                status = "Fix: ${e.message}"
            } catch (e: Exception) {
                status = "Save failed: ${e.message}"
            }
        }
    }) { Text("Save prompt pack") }
}

// ---------------- Tools ----------------

private class ToolDraft {
    var title by mutableStateOf("")
    var icon by mutableStateOf("🛠")
    val chain = mutableStateListOf<TextOp>()
}

@Composable
private fun ToolsBuilder(manifest: ManifestFormState, pluginManager: PluginManager, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    val drafts = remember { mutableStateListOf(ToolDraft()) }
    var status by remember { mutableStateOf<String?>(null) }
    var tryInput by remember { mutableStateOf("Hello World") }
    var tryIndex by remember { mutableStateOf(0) }

    drafts.forEachIndexed { index, draft ->
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Tool ${index + 1}", style = MaterialTheme.typography.titleMedium)
                    if (drafts.size > 1) {
                        OutlinedButton(onClick = { drafts.removeAt(index) }) { Text("Remove") }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = draft.icon, onValueChange = { draft.icon = it.take(8) }, label = { Text("Icon") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = draft.title, onValueChange = { draft.title = it }, label = { Text("Title") }, singleLine = true, modifier = Modifier.weight(2f))
                }
                Text("Steps (run in order):", style = MaterialTheme.typography.bodySmall)
                draft.chain.forEachIndexed { step, op ->
                    OpRow(op = op, onChange = { draft.chain[step] = it }, onRemove = { draft.chain.removeAt(step) })
                }
                AddOpRow(onAdd = { draft.chain.add(TextOp(it)) })
            }
        }
    }
    OutlinedButton(onClick = { if (drafts.size < 20) drafts.add(ToolDraft()) }) { Text("+ Add tool") }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Try it", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(value = tryInput, onValueChange = { tryInput = it }, label = { Text("Input") }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                drafts.forEachIndexed { i, d ->
                    FilterChip(selected = tryIndex == i, onClick = { tryIndex = i }, label = { Text(d.title.ifEmpty { "Tool ${i + 1}" }) })
                }
            }
            val output = remember(tryInput, tryIndex, drafts.size) {
                val chain = drafts.getOrNull(tryIndex)?.chain?.toList().orEmpty()
                if (chain.isEmpty()) "(add steps to preview)" else TextOps.runChain(tryInput, chain)
            }
            Text("→ $output", style = MaterialTheme.typography.bodyLarge)
        }
    }

    SaveStatus(status)
    Button(onClick = {
        scope.launch {
            try {
                val tools = drafts.mapIndexed { i, d ->
                    val slug = d.title.trim().lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
                    TextTool(
                        id = (slug.ifEmpty { "tool" }) + "-${i + 1}",
                        title = d.title.trim(), icon = d.icon.trim().ifEmpty { "🛠" },
                        chain = d.chain.toList()
                    )
                }
                val pack = TextToolPack(manifest.build(PackKind.TOOLS), tools)
                val saved = pluginManager.importPack(PackCodec.encodeTools(pack))
                status = "Installed ${saved.name} ✓ — find it on the 🛠 board"
                onDone()
            } catch (e: PackCodec.PackException) {
                status = "Fix: ${e.message}"
            } catch (e: Exception) {
                status = "Save failed: ${e.message}"
            }
        }
    }) { Text("Save tool pack") }
}

@Composable
private fun OpRow(op: TextOp, onChange: (TextOp) -> Unit, onRemove: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(op.op, style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = onRemove) { Text("✕") }
            }
            if (op.op == TextOps.PREFIX || op.op == TextOps.SUFFIX) {
                OutlinedTextField(value = op.arg1, onValueChange = { onChange(op.copy(arg1 = it)) }, label = { Text("Text") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            if (op.op == TextOps.REPLACE) {
                OutlinedTextField(value = op.arg1, onValueChange = { onChange(op.copy(arg1 = it)) }, label = { Text("Find") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = op.arg2, onValueChange = { onChange(op.copy(arg2 = it)) }, label = { Text("Replace with") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun AddOpRow(onAdd: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }) { Text("+ Add step") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            TextOps.ALL.forEach { op ->
                DropdownMenuItem(text = { Text(op) }, onClick = { open = false; onAdd(op) })
            }
        }
    }
}
