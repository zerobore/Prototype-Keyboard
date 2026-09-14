package com.prototype.keyboard.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.prototype.keyboard.data.KeyboardSettings
import com.prototype.keyboard.data.SettingsRepository

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repo = SettingsRepository(applicationContext)
        setContent {
            val settings by repo.settings.collectAsState(initial = KeyboardSettings())
            ProtoTheme(mode = settings.themeMode) {
                AppRoot(repo = repo, settings = settings)
            }
        }
    }
}

private enum class Tab(val title: String, val glyph: String) {
    Setup("Setup", "🏠"),
    Settings("Settings", "⚙"),
    Test("Test", "⌨"),
    About("About", "ℹ"),
}

@Composable
private fun AppRoot(repo: SettingsRepository, settings: KeyboardSettings) {
    var tab by remember { mutableStateOf(Tab.Setup) }
    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { entry ->
                    NavigationBarItem(
                        selected = tab == entry,
                        onClick = { tab = entry },
                        icon = { Text(entry.glyph) },
                        label = { Text(entry.title) }
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (tab) {
                Tab.Setup -> SetupScreen(onOpenTest = { tab = Tab.Test })
                Tab.Settings -> SettingsScreen(settings = settings, repo = repo)
                Tab.Test -> TestDriveScreen()
                Tab.About -> AboutScreen()
            }
        }
    }
}
