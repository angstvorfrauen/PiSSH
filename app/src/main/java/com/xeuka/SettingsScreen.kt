package com.xeuka.pissh

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(settings: AppSettings, onDisconnect: () -> Unit) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var info by remember { mutableStateOf("") }
    val powerManager = remember { ctx.getSystemService(Context.POWER_SERVICE) as PowerManager }
    var batteryUnrestricted by remember { mutableStateOf(powerManager.isIgnoringBatteryOptimizations(ctx.packageName)) }
    var confirm by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxSize().background(Color(0xFF0A0A0A)).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, color = Color(0xFFF2F6FC))
        Text("Customize your workspace and connection.", color = Color(0xFF999999))
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF0F0F0F)), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("ACTIVE CONNECTION", color = Color(0xFFFF3030), style = MaterialTheme.typography.labelSmall)
                Text("${SshManager.user}@${SshManager.host}", color = Color(0xFFF2F6FC), style = MaterialTheme.typography.titleMedium)
                Text("SSH host key verification is not currently enabled. Use this build only on trusted networks.",
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
        SettingsCard("Appearance") {
            SettingChoice("App theme", settings.appTheme, listOf("System", "Dark", "Light")) {
                settings.appTheme = it; settings.save()
            }
        }
        SettingsCard("Editor") {
            SettingChoice("Editor theme", settings.editorTheme,
                listOf("Dracula", "Monokai", "GitHub Light", "GitHub Dark", "One Dark", "Nord", "Solarized Dark", "Solarized Light")) {
                settings.editorTheme = it; settings.save()
            }
            Text("Font size: ${settings.editorFont.toInt()} sp", color = Color(0xFFBDBDBD))
            Slider(settings.editorFont, { settings.editorFont = it }, valueRange = 10f..24f,
                onValueChangeFinished = { settings.save() })
            SettingSwitch("Syntax highlighting", settings.syntaxHighlighting) {
                settings.syntaxHighlighting = it; settings.save()
            }
            SettingSwitch("Line numbers", settings.lineNumbers) {
                settings.lineNumbers = it; settings.save()
            }
        }
        SettingsCard("SSH connection") {
            Text("KeepAlive interval: ${settings.keepAliveSeconds} seconds", color = Color(0xFFBDBDBD))
            Slider(settings.keepAliveSeconds.toFloat(), {
                settings.keepAliveSeconds = it.toInt(); settings.save()
            }, valueRange = 5f..120f, steps = 22)
            SettingSwitch("Automatic reconnection", settings.autoReconnect) {
                settings.autoReconnect = it; settings.save()
            }
            if (!batteryUnrestricted) {
                Text("Battery optimization may stop SSH sessions while PiSSH is in the background. Disable it to improve connection stability.",
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                OutlinedButton({
                    try {
                        ctx.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:${ctx.packageName}")))
                    } catch (_: Exception) {
                        ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    }
                    batteryUnrestricted = powerManager.isIgnoringBatteryOptimizations(ctx.packageName)
                }, Modifier.fillMaxWidth()) { Text("Disable battery optimization") }
            } else {
                Text("Battery optimization is disabled for PiSSH.", color = Color(0xFFFF8585),
                    style = MaterialTheme.typography.bodySmall)
            }
        }
        SettingsCard("File manager & terminal") {
            SettingSwitch("Show hidden files", settings.showHidden) {
                settings.showHidden = it; settings.save()
            }
            Text("Terminal font size: ${settings.termFont.toInt()} sp", color = Color(0xFFBDBDBD))
            Slider(settings.termFont, { settings.termFont = it }, valueRange = 9f..22f,
                onValueChangeFinished = { settings.save() })
        }
        SettingsCard("Raspberry Pi") {
            OutlinedButton({
                scope.launch {
                    info = try { SshManager.exec("uname -sr; uptime -p; vcgencmd measure_temp 2>/dev/null; free -h | sed -n 2p; df -h / | tail -1") }
                    catch (e: Exception) { "Error: ${e.message}" }
                }
            }, Modifier.fillMaxWidth()) { Text("Fetch system information") }
            if (info.isNotBlank()) Text(info.trim(), color = Color(0xFFBDBDBD),
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
            OutlinedButton({ confirm = "reboot" }, Modifier.fillMaxWidth()) { Text("Reboot Raspberry Pi") }
            OutlinedButton({ confirm = "poweroff" }, Modifier.fillMaxWidth()) { Text("Shut down Raspberry Pi") }
        }
        Button(onDisconnect, Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8F3948))) {
            Text("Disconnect")
        }
        Spacer(Modifier.height(8.dp))
    }

    confirm?.let { action ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(if (action == "reboot") "Reboot Raspberry Pi?" else "Shut down Raspberry Pi?") },
            text = { Text("Your SSH connection will be closed.") },
            confirmButton = {
                TextButton({
                    confirm = null
                    scope.launch {
                        try { SshManager.exec("sudo $action", 5000) } catch (_: Exception) {}
                        onDisconnect()
                    }
                }) { Text("Continue") }
            },
            dismissButton = { TextButton({ confirm = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF0F0F0F)), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Color(0xFFF2F6FC))
            content()
        }
    }
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = Color(0xFFBDBDBD))
        Switch(checked, onChange)
    }
}

@Composable
private fun SettingChoice(label: String, value: String, options: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = Color(0xFFBDBDBD))
        Box {
            TextButton(onClick = { expanded = true }) { Text(value, color = Color(0xFFFF3030)) }
            DropdownMenu(expanded, { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(text = { Text(option) }, onClick = {
                        onSelect(option); expanded = false
                    })
                }
            }
        }
    }
}
