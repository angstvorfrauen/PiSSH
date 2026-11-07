package com.xeuka.pissh

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

class AppSettings(private val prefs: SharedPreferences) {
    var showHidden by mutableStateOf(prefs.getBoolean("showHidden", false))
    var termFont by mutableStateOf(prefs.getFloat("termFont", 9f))
    var editorFont by mutableStateOf(prefs.getFloat("editorFont", 14f))
    var editorTheme by mutableStateOf(prefs.getString("editorTheme", "One Dark") ?: "One Dark")
    var appTheme by mutableStateOf(prefs.getString("appTheme", "System") ?: "System")
    var syntaxHighlighting by mutableStateOf(prefs.getBoolean("syntaxHighlighting", true))
    var lineNumbers by mutableStateOf(prefs.getBoolean("lineNumbers", true))
    var autoReconnect by mutableStateOf(prefs.getBoolean("autoReconnect", true))
    var keepAliveSeconds by mutableStateOf(prefs.getInt("keepAliveSeconds", 15))
    fun save() = prefs.edit()
        .putBoolean("showHidden", showHidden).putFloat("termFont", termFont)
        .putFloat("editorFont", editorFont).putString("editorTheme", editorTheme)
        .putString("appTheme", appTheme).putBoolean("syntaxHighlighting", syntaxHighlighting)
        .putBoolean("lineNumbers", lineNumbers).putBoolean("autoReconnect", autoReconnect)
        .putInt("keepAliveSeconds", keepAliveSeconds).apply()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val prefs = remember { getSharedPreferences("pissh", Context.MODE_PRIVATE) }
            val settings = remember { AppSettings(prefs) }
            val dark = when (settings.appTheme) {
                "Dark" -> true
                "Light" -> false
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }
            MaterialTheme(colorScheme = if (dark) darkColorScheme(
                primary = Color(0xFFFF3030), secondary = Color(0xFFFF7777),
                background = Color(0xFF0A0A0A), surface = Color(0xFF0F0F0F),
                onPrimary = Color(0xFFFFFFFF), onBackground = Color(0xFFF2F6FC),
                onSurface = Color(0xFFF2F6FC)
            ) else lightColorScheme(primary = Color(0xFFCC2020))) {
                Surface(Modifier.fillMaxSize()) { App(settings) }
            }
        }
    }
}

@Composable
fun App(settings: AppSettings) {
    val context = LocalContext.current
    var connected by remember { mutableStateOf(SshManager.isConnected) }
    if (!connected) LoginScreen(settings) { connected = true }
    else MainScreen(settings) { SshManager.disconnect(); context.stopService(android.content.Intent(context, SshForegroundService::class.java)); connected = false }
}

@Composable
fun LoginScreen(settings: AppSettings, onConnected: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("pissh", Context.MODE_PRIVATE) }
    var host by remember { mutableStateOf(prefs.getString("host", "raspberrypi.local")!!) }
    var port by remember { mutableStateOf(prefs.getString("port", "22")!!) }
    var user by remember { mutableStateOf(prefs.getString("user", "pi")!!) }
    var pass by remember { mutableStateOf("") }
    var showPass by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        Modifier.fillMaxSize().background(Color(0xFF0A0A0A)).verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(shape = MaterialTheme.shapes.large, color = Color(0xFF171010), modifier = Modifier.size(72.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Terminal, null, tint = Color(0xFFFF3030), modifier = Modifier.size(36.dp))
            }
        }
        Spacer(Modifier.height(20.dp))
        Text("Raspberry PI · SSH", style = MaterialTheme.typography.headlineLarge, color = Color(0xFFF2F6FC))
        Text("EASY REMOTE ACCESS", style = MaterialTheme.typography.labelMedium,
            color = Color(0xFFFF3030), letterSpacing = 2.sp)
        Spacer(Modifier.height(32.dp))
        Card(
            modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F0F0F)),
            shape = MaterialTheme.shapes.extraLarge
        ) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Connect with Raspberry PI", style = MaterialTheme.typography.headlineSmall, color = Color(0xFFF2F6FC))
                Text("Enter your SHH credentials", color = Color(0xFFAAAAAA),
                    style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(host, { host = it }, label = { Text("IP address") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(port, { port = it.filter(Char::isDigit) }, label = { Text("Port") },
                        singleLine = true, modifier = Modifier.weight(.7f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    OutlinedTextField(user, { user = it }, label = { Text("Username") },
                        singleLine = true, modifier = Modifier.weight(1.3f))
                }
                OutlinedTextField(pass, { pass = it }, label = { Text("Password") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = { TextButton({ showPass = !showPass }) { Text(if (showPass) "Hide" else "Show") } })
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Button(
                    enabled = !loading && host.isNotBlank() && user.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3030)),
                    onClick = {
                        loading = true; error = null
                        scope.launch {
                            try {
                                SshManager.connect(host.trim(), port.toIntOrNull() ?: 22, user.trim(), pass, settings.keepAliveSeconds)
                                androidx.core.content.ContextCompat.startForegroundService(
                                    ctx, android.content.Intent(ctx, SshForegroundService::class.java)
                                )
                                prefs.edit().putString("host", host.trim()).putString("port", port)
                                    .putString("user", user.trim()).apply()
                                onConnected()
                            } catch (e: Exception) {
                                error = "Connection failed: ${e.message}"
                            }
                            loading = false
                        }
                    }
                ) {
                    if (loading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Color.White)
                    else Text("Connect", color = Color(0xFFFFFFFF), fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Text("MADE BY XEUKA", color = Color(0xFF777777), style = MaterialTheme.typography.labelSmall,
            letterSpacing = 1.2.sp)
    }
}

@Composable
fun MainScreen(settings: AppSettings, onDisconnect: () -> Unit) {
    val context = LocalContext.current
    val terminalPrefs = remember { context.getSharedPreferences("pissh", Context.MODE_PRIVATE) }
    val terminals = remember {
        mutableStateListOf<TerminalState>().apply {
            val saved = terminalPrefs.getString("terminalSessions", null)
                ?.split("|")?.filter { it.isNotBlank() }.orEmpty()
            if (saved.isEmpty()) {
                val first = TerminalState("Raspberry PI · SSH")
                add(first)
                terminalPrefs.edit().putString("terminalSessions", first.sessionName).apply()
            } else saved.forEach { add(TerminalState(it)) }
        }
    }
    fun persistTerminalSessions() {
        terminalPrefs.edit().putString("terminalSessions", terminals.joinToString("|") { it.sessionName }).apply()
    }
    var activeTerminal by rememberSaveable { mutableIntStateOf(0) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var documents by remember { mutableStateOf(listOf<EditorDocument>()) }
    val titles = listOf("Files", "Editor", "Terminal", "Settings")

    Scaffold(
        containerColor = Color(0xFF0A0A0A),
        topBar = {
            Surface(color = Color(0xFF0F0F0F), shadowElevation = 2.dp) {
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(shape = MaterialTheme.shapes.medium, color = Color(0xFF241010), modifier = Modifier.size(38.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Terminal, null, tint = Color(0xFFFF3030))
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Raspberry PI · SSH", color = Color(0xFFF2F6FC), style = MaterialTheme.typography.titleMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        Text(titles[tab], color = Color(0xFF929292), style = MaterialTheme.typography.labelSmall)
                    }
                    Surface(shape = MaterialTheme.shapes.small, color = Color(0xFF351313)) {
                        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(7.dp).background(Color(0xFFFF4545), androidx.compose.foundation.shape.CircleShape))
                            Spacer(Modifier.width(7.dp))
                            Text("Connected", color = Color(0xFFFF8585), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF0F0F0F), contentColor = Color(0xFFAAAAAA)) {
                NavigationBarItem(tab == 0, { tab = 0 }, { Icon(Icons.Filled.Folder, null) },
                    label = { Text("Files") }, colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFFFF3030), selectedTextColor = Color(0xFFFF3030),
                        indicatorColor = Color(0xFF351313), unselectedIconColor = Color(0xFF858585),
                        unselectedTextColor = Color(0xFF858585)))
                NavigationBarItem(tab == 1, { tab = 1 }, { Icon(Icons.Filled.Edit, null) },
                    label = { Text("Editor") }, colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFFFF3030), selectedTextColor = Color(0xFFFF3030),
                        indicatorColor = Color(0xFF351313), unselectedIconColor = Color(0xFF858585),
                        unselectedTextColor = Color(0xFF858585)))
                NavigationBarItem(tab == 2, { tab = 2 }, { Icon(Icons.Filled.Terminal, null) },
                    label = { Text("Terminal") }, colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFFFF3030), selectedTextColor = Color(0xFFFF3030),
                        indicatorColor = Color(0xFF351313), unselectedIconColor = Color(0xFF858585),
                        unselectedTextColor = Color(0xFF858585)))
                NavigationBarItem(tab == 3, { tab = 3 }, { Icon(Icons.Filled.Settings, null) },
                    label = { Text("Settings") }, colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFFFF3030), selectedTextColor = Color(0xFFFF3030),
                        indicatorColor = Color(0xFF351313), unselectedIconColor = Color(0xFF858585),
                        unselectedTextColor = Color(0xFF858585)))
            }
        }
    ) { inner ->
        Box(Modifier.padding(inner).consumeWindowInsets(inner).fillMaxSize()) {
            when (tab) {
                0 -> FilesScreen(settings) { file ->
                    documents = (documents.filterNot { it.path == file.path } + file)
                    tab = 1
                }
                1 -> EditorScreen(documents, { doc ->
                    documents = documents.filterNot { it.path == doc.path }
                }, settings)
                2 -> TerminalManagerScreen(
                    terminals = terminals,
                    activeIndex = activeTerminal,
                    onSelect = { activeTerminal = it },
                    onAdd = {
                        terminals.add(TerminalState())
                        persistTerminalSessions()
                        activeTerminal = terminals.lastIndex
                    },
                    onClose = { index ->
                        if (terminals.size > 1) {
                            terminals[index].terminate()
                            terminals.removeAt(index)
                            persistTerminalSessions()
                            activeTerminal = when {
                                activeTerminal > index -> activeTerminal - 1
                                activeTerminal == index -> (index - 1).coerceAtLeast(0)
                                else -> activeTerminal
                            }
                        }
                    },
                    settings = settings
                )
                else -> SettingsScreen(settings, onDisconnect)
            }
        }
    }
}
