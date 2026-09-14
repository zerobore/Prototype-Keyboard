package com.prototype.keyboard.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun SetupScreen(onOpenTest: () -> Unit) {
    val context = LocalContext.current
    var refreshTick by remember { mutableIntStateOf(0) }
    var imeEnabled by remember { mutableStateOf(false) }
    var imeSelected by remember { mutableStateOf(false) }

    LaunchedEffect(refreshTick) {
        imeEnabled = isImeEnabled(context)
        imeSelected = isImeSelected(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Prototype Keyboard", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Two quick steps to start typing.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        SetupStepCard(
            step = "1",
            title = "Enable Prototype Keyboard",
            description = "Turn it on in system Language & input settings.",
            done = imeEnabled,
            buttonText = "Open input settings",
            onButton = {
                context.startActivity(
                    Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        )

        SetupStepCard(
            step = "2",
            title = "Switch to it",
            description = "Choose Prototype Keyboard as the active input method.",
            done = imeSelected,
            buttonText = "Choose keyboard",
            onButton = {
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showInputMethodPicker()
            }
        )

        if (imeEnabled && imeSelected) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("✅ Ready to type", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Open the test area and try letters, shift, symbols (?123), long-press accents, and delete repeat.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Button(onClick = onOpenTest) { Text("Open test area") }
                }
            }
        }

        OutlinedButton(
            onClick = { refreshTick++ },
            modifier = Modifier.align(Alignment.End)
        ) { Text("Refresh status") }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("🔒 Private by design", style = MaterialTheme.typography.titleSmall)
                Text(
                    "100% offline. No internet permission, no accounts, no analytics. " +
                        "What you type never leaves your device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SetupStepCard(
    step: String,
    title: String,
    description: String,
    done: Boolean,
    buttonText: String,
    onButton: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (done) "✅" else "○", style = MaterialTheme.typography.titleMedium)
                Text("Step $step · $title", style = MaterialTheme.typography.titleMedium)
            }
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = onButton, enabled = !done) { Text(if (done) "Done" else buttonText) }
        }
    }
}

private fun isImeEnabled(context: Context): Boolean {
    val enabled = Settings.Secure.getString(
        context.contentResolver, Settings.Secure.ENABLED_INPUT_METHODS
    ) ?: return false
    return enabled.split(":").any { it.startsWith(context.packageName + "/") }
}

private fun isImeSelected(context: Context): Boolean {
    val def = Settings.Secure.getString(
        context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD
    ) ?: return false
    return def.startsWith(context.packageName + "/")
}
