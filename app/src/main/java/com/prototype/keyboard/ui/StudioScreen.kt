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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.prototype.keyboard.plugins.PackCodec
import com.prototype.keyboard.plugins.PackKind
import com.prototype.keyboard.plugins.PluginApi
import com.prototype.keyboard.plugins.PluginManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Studio tab: installed packs (use/enable/export/delete), import, and the
 * pack Builder. Packs are data-only files — safe to share and update anytime.
 */
@Composable
fun StudioScreen(pluginManager: PluginManager) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val state by pluginManager.state.collectAsState(initial = PluginManager.PluginState.EMPTY)
    var building by remember { mutableStateOf<PackKind?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var exportId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { pluginManager.preinstallIfNeeded() }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            try {
                val json = context.contentResolver.openInputStream(uri)?.use {
                    it.readBytes().toString(Charsets.UTF_8)
                } ?: throw IllegalStateException("Could not open file")
                val manifest = pluginManager.importPack(json)
                withContext(Dispatchers.Main) {
                    status = "Installed ${manifest.name} v${manifest.version} ✓"
                }
            } catch (e: PackCodec.PackException) {
                withContext(Dispatchers.Main) { status = "Rejected: ${e.message}" }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { status = "Import failed: ${e.message}" }
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val id = exportId ?: return@rememberLauncherForActivityResult
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            try {
                val json = pluginManager.exportPack(id) ?: throw IllegalStateException("Pack missing")
                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(json.toByteArray(Charsets.UTF_8))
                } ?: throw IllegalStateException("Could not open file")
                withContext(Dispatchers.Main) { status = "Exported $id ✓ — share it anywhere" }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { status = "Export failed: ${e.message}" }
            }
        }
    }

    building?.let { kind ->
        PackBuilderScreen(
            kind = kind,
            pluginManager = pluginManager,
            onDone = { building = null }
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Studio", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Plugin packs: themes, dictionaries, AI prompts, text tools. Build your own, import and update anytime.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("New pack", style = MaterialTheme.typography.titleMedium)
                @Composable
                fun KindButton(kind: PackKind, label: String) {
                    OutlinedButton(onClick = { building = kind }) { Text(label) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KindButton(PackKind.THEME, "🎨 Theme")
                    KindButton(PackKind.DICTIONARY, "📖 Dict")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KindButton(PackKind.PROMPTS, "✨ Prompts")
                    KindButton(PackKind.TOOLS, "🛠 Tools")
                }
                OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json")) }) {
                    Text("Import .json pack")
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Platform", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Host ${PluginApi.HOST_VERSION} · Plugin API v${PluginApi.PLUGIN_API_VERSION} " +
                        "(supports ${PluginApi.SUPPORTED_API}) · ${state.installed.size} packs installed",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        PackKind.entries.forEach { kind ->
            val packs = state.installed.filter { it.manifest.kind == kind }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        when (kind) {
                            PackKind.THEME -> "🎨 Themes (${packs.size})"
                            PackKind.DICTIONARY -> "📖 Dictionaries (${packs.size})"
                            PackKind.PROMPTS -> "✨ AI prompts (${packs.size})"
                            PackKind.TOOLS -> "🛠 Text tools (${packs.size})"
                        },
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (kind == PackKind.THEME) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = state.activeThemeId.isEmpty(),
                                onClick = { scope.launch { pluginManager.setActiveTheme("") } },
                                label = { Text("Default") }
                            )
                            packs.forEach { pack ->
                                FilterChip(
                                    selected = state.activeThemeId == pack.manifest.id,
                                    onClick = {
                                        scope.launch { pluginManager.setActiveTheme(pack.manifest.id) }
                                    },
                                    label = { Text(pack.manifest.name) }
                                )
                            }
                        }
                    }
                    if (packs.isEmpty()) {
                        Text(
                            "None yet — build or import one.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    packs.forEach { pack ->
                        PackRow(
                            pack = pack,
                            enabled = pack.manifest.id !in state.disabledIds,
                            onToggle = { scope.launch { pluginManager.setPackEnabled(pack.manifest.id, it) } },
                            onExport = {
                                exportId = pack.manifest.id
                                exportLauncher.launch("${pack.manifest.id}.json")
                            },
                            onDelete = {
                                scope.launch {
                                    pluginManager.deletePack(pack.manifest.id)
                                    status = "Deleted ${pack.manifest.name}"
                                }
                            }
                        )
                    }
                }
            }
        }
        if (state.installed.any { it.manifest.kind == PackKind.PROMPTS }) {
            Text(
                "Prompt packs install now; the AI runner that executes them arrives in Phase 3b.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun PackRow(
    pack: PluginManager.InstalledPack,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("${pack.manifest.name} v${pack.manifest.version}", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "by ${pack.manifest.author} · API v${pack.manifest.pluginApiVersion}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = enabled, onCheckedChange = onToggle)
            }
            if (pack.manifest.description.isNotEmpty()) {
                Text(pack.manifest.description, style = MaterialTheme.typography.bodySmall)
            }
            Text(
                pack.manifest.id,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onExport) { Text("Export") }
                OutlinedButton(onClick = onDelete) { Text("Delete") }
            }
        }
    }
}
