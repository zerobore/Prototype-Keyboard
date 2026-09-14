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
import com.prototype.keyboard.data.ClipboardRepository
import com.prototype.keyboard.data.KeyboardSettings
import com.prototype.keyboard.data.SettingsRepository
import com.prototype.keyboard.data.UserDictionary
import com.prototype.keyboard.data.db.AppDatabase

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = applicationContext
        val repo = SettingsRepository(app)
        val userDict = UserDictionary(AppDatabase.get(app))
        val clips = ClipboardRepository(app)
        setContent {
            val settings by repo.settings.collectAsState(initial = KeyboardSettings())
            ProtoTheme(mode = settings.themeMode) {
                AppRoot(repo = repo, userDict = userDict, clips = clips, settings = settings)
            }
        }
    }
}

private enum class Tab(val title: String, val glyph: String) {
    Setup("Setup", "🏠"),
    Data("Data", "📚"),
    Settings("Settings", "⚙"),
    Test("Test", "⌨"),
    About("About", "ℹ"),
}

@Composable
private fun AppRoot(
    repo: SettingsRepository,
    userDict: UserDictionary,
    clips: ClipboardRepository,
    settings: KeyboardSettings,
) {
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
                Tab.Data -> DataScreen(userDict = userDict, clipboardRepo = clips)
                Tab.Settings -> SettingsScreen(
                    settings = settings,
                    repo = repo,
                    userDict = userDict,
                    clipboardRepo = clips
                )
                Tab.Test -> TestDriveScreen()
                Tab.About -> AboutScreen()
            }
        }
    }
}
