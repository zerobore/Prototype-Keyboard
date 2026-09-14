package com.prototype.keyboard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.prototype.keyboard.BuildConfig

@Composable
fun AboutScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("About", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Prototype Keyboard ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("🔒 Privacy", style = MaterialTheme.typography.titleMedium)
                Text(
                    "• 100% offline — no internet permission\n" +
                        "• No accounts, ads, analytics, or crash upload\n" +
                        "• No auto-backup of typing data\n" +
                        "• Password fields get zero assistance or learning",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("🗺 Roadmap", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Phase 1 (this build): reliable tap typing + app\n" +
                        "Phase 3: suggestions, autocorrect, glide typing, emoji, clipboard, more languages, backup/restore\n" +
                        "Then: hardening, your testing, final APK",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
