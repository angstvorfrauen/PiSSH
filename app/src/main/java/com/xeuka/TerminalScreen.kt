package com.xeuka.pissh
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/** Small streaming ANSI/VT parser: keeps SGR colors and discards terminal control sequences. */
private class AnsiParser {
    private var inEscape = false
    private var inCsi = false
    private val sequence = StringBuilder()
    private var foreground = Color.White
    private var background = Color.Transparent
    private var bold = false

    fun parse(input: String): AnnotatedString {
        val out = AnnotatedString.Builder()
        input.forEach { ch ->
            when {
                inEscape -> {
                    if (!inCsi && ch == '[') {
                        inCsi = true
                        sequence.clear()
                    } else if (!inCsi) {
                        inEscape = false // discard non-CSI escape
                    } else if (ch.code in 0x40..0x7E) {
                        if (ch == 'm') applySgr(sequence.toString())
                        // Cursor, bracketed-paste, title and other CSI commands are controls,
                        // not printable terminal output (e.g. ESC[?2004l).
                        inEscape = false
                        inCsi = false
                        sequence.clear()
                    } else if (sequence.length < 128) {
                        sequence.append(ch)
                    } else {
                        inEscape = false
                        inCsi = false
                        sequence.clear()
                    }
                }
                ch == '\u001b' -> {
                    inEscape = true
                    inCsi = false
                    sequence.clear()
                }
                ch == '\u0000' -> Unit
                else -> out.pushStyle(SpanStyle(
                    color = foreground,
                    background = background,
                    fontWeight = if (bold) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal
                )).also { out.append(ch); out.pop() }
            }
        }
        return out.toAnnotatedString()
    }

    private fun applySgr(raw: String) {
        val p = raw.split(';').map { it.toIntOrNull() ?: 0 }
        var i = 0
        while (i < p.size) {
            when (val n = p[i]) {
                0 -> { foreground = Color.White; background = Color.Transparent; bold = false }
                1 -> bold = true
                22 -> bold = false
                39 -> foreground = Color.White
                49 -> background = Color.Transparent
                in 30..37 -> foreground = basic(n - 30, false)
                in 40..47 -> background = basic(n - 40, false)
                in 90..97 -> foreground = basic(n - 90, true)
                in 100..107 -> background = basic(n - 100, true)
                38, 48 -> {
                    val isForeground = n == 38
                    if (i + 2 < p.size && p[i + 1] == 5) {
                        val c = indexed(p[i + 2])
                        if (isForeground) foreground = c else background = c
                        i += 2
                    } else if (i + 4 < p.size && p[i + 1] == 2) {
                        val c = Color(
                            p[i + 2].coerceIn(0,255) / 255f,
                            p[i + 3].coerceIn(0,255) / 255f,
                            p[i + 4].coerceIn(0,255) / 255f
                        )
                        if (isForeground) foreground = c else background = c
                        i += 4
                    }
                }
            }
            i++
        }
    }

    private fun basic(index: Int, bright: Boolean): Color {
        val normal = listOf(Color(0xFF000000), Color(0xFFAA0000), Color(0xFF00AA00),
            Color(0xFFAA5500), Color(0xFF0000AA), Color(0xFFAA00AA),
            Color(0xFF00AAAA), Color(0xFFAAAAAA))
        val light = listOf(Color(0xFF555555), Color(0xFFFF5555), Color(0xFF55FF55),
            Color(0xFFFFFF55), Color(0xFF5555FF), Color(0xFFFF55FF),
            Color(0xFF55FFFF), Color(0xFFFFFFFF))
        return (if (bright) light else normal)[index.coerceIn(0, 7)]
    }

    private fun indexed(value: Int): Color {
        val n = value.coerceIn(0, 255)
        if (n < 8) return basic(n, false)
        if (n < 16) return basic(n - 8, true)
        if (n < 232) {
            val v = n - 16
            fun component(x: Int) = if (x == 0) 0 else 55 + x * 40
            return Color(component(v / 36) / 255f, component((v / 6) % 6) / 255f, component(v % 6) / 255f)
        }
        val gray = (8 + (n - 232) * 10).coerceAtMost(255) / 255f
        return Color(gray, gray, gray)
    }
}

class TerminalState(val sessionName: String = "PiSSH-${java.util.UUID.randomUUID().toString().take(8)}") {
    val lines = mutableStateListOf<AnnotatedString>()
    var busy by mutableStateOf(false)
    var connected by mutableStateOf(false)
    private var handle: TerminalHandle? = null
    private val main = Handler(Looper.getMainLooper())
    private val ansi = AnsiParser()

    suspend fun open() {
        if (handle?.isConnected == true) return
        busy = true
        try {
            handle = SshManager.openTmuxTerminal(sessionName) { chunk ->
                val parsed = ansi.parse(chunk)
                if (parsed.isNotEmpty()) main.post {
                    if (lines.size > 3000) repeat(lines.size - 2500) { lines.removeAt(0) }
                    lines.add(parsed)
                }
            }
            connected = true
        } catch (e: Exception) {
            lines.add(AnnotatedString("Terminal connection failed: ${e.message}"))
            connected = false
        } finally { busy = false }
    }

    suspend fun send(value: String) {
        val h = handle
        if (h == null || !h.isConnected) {
            lines.add(AnnotatedString("Terminal is not connected. Please reconnect."))
            connected = false
            return
        }
        try { h.send(value) } catch (e: Exception) {
            lines.add(AnnotatedString("\\n[Connection lost: ${e.message}]\\n"))
            connected = false
        }
    }

    // Detach only: the tmux session keeps running on the Raspberry Pi.
    fun close() {
        handle?.close()
        handle = null
        connected = false
    }

    // Called only when the user presses the tab's × button.
    fun terminate() {
        close()
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try { SshManager.killTmuxSession(sessionName) } catch (_: Exception) { }
        }
    }
}

@Composable
private fun TerminalSessionView(state: TerminalState, settings: AppSettings) {
    var input by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) { state.open() }
    // Keep the SSH shell alive when switching tabs; MainActivity owns disconnect.
    LaunchedEffect(state.lines.size) {
        if (state.lines.isNotEmpty()) listState.scrollToItem(state.lines.lastIndex)
    }
    fun send() {
        val command = input
        if (command.isEmpty() || state.busy) return
        input = ""
        scope.launch { state.send(command + "\r") }
    }
    Column(Modifier.fillMaxSize().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (state.connected) "SSH session · PiSSH" else "Terminal disconnected",
                color = if (state.connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f))
            TextButton(onClick = { scope.launch { state.close(); state.open() } }) { Text("Reconnect") }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().background(Color.Black).padding(8.dp), state = listState) {
            itemsIndexed(state.lines) { _, line ->
                Text(line, fontFamily = FontFamily.Monospace, fontSize = settings.termFont.sp,
                    lineHeight = (settings.termFont * 1.25f).sp)
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            listOf("ESC" to "\u001b", "TAB" to "\t", "CTRL+C" to "\u0003", "CTRL+D" to "\u0004", "CTRL+Z" to "\u001a").forEach { (label, value) ->
                TextButton(onClick = { scope.launch { state.send(value) } }, contentPadding = PaddingValues(horizontal = 5.dp)) {
                    Text(label, fontSize = 10.sp)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(input, { input = it }, modifier = Modifier.weight(1f), singleLine = true,
                placeholder = { Text("Enter a command…") },
                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send, autoCorrect = false),
                keyboardActions = KeyboardActions(onSend = { send() }))
            Spacer(Modifier.width(8.dp))
            Button({ send() }, enabled = state.connected && !state.busy) { Text("Send") }
        }
    }
}


@Composable
fun TerminalManagerScreen(
    terminals: List<TerminalState>,
    activeIndex: Int,
    onSelect: (Int) -> Unit,
    onAdd: () -> Unit,
    onClose: (Int) -> Unit,
    settings: AppSettings
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().background(Color(0xFF101010)).padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                terminals.forEachIndexed { index, terminal ->
                    Surface(
                        color = if (index == activeIndex) Color(0xFF351313) else Color(0xFF1A1A1A),
                        shape = MaterialTheme.shapes.small,
                        onClick = { onSelect(index) }
                    ) {
                        Row(
                            Modifier.padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Terminal ${index + 1}", color = if (index == activeIndex) Color(0xFFFF7777) else Color.LightGray,
                                fontSize = 12.sp)
                            Spacer(Modifier.width(5.dp))
                            Text("×", color = Color(0xFFAAAAAA), modifier = Modifier.padding(horizontal = 3.dp)
                                .clickable { onClose(index) }, fontSize = 15.sp)
                        }
                    }
                }
            }
            FilledIconButton(
                onClick = onAdd,
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFFFF3030)),
                modifier = Modifier.size(38.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Neues Terminal", tint = Color.White)
            }
        }
        HorizontalDivider(color = Color(0xFF292929))
        key(terminals.getOrNull(activeIndex)?.sessionName) {
            terminals.getOrNull(activeIndex)?.let { TerminalSessionView(it, settings) }
        }
    }
}
