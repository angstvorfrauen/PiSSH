package com.xeuka.pissh

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

data class EditorDocument(val path: String, val name: String, val initialText: String)

private data class EditorPalette(val background: Color, val foreground: Color, val keyword: Color,
                                 val string: Color, val comment: Color, val number: Color)

private fun palette(theme: String): EditorPalette = when (theme) {
    "Dracula" -> EditorPalette(Color(0xFF282A36), Color(0xFFF8F8F2), Color(0xFFFF79C6), Color(0xFFF1FA8C), Color(0xFF6272A4), Color(0xFFBD93F9))
    "Monokai" -> EditorPalette(Color(0xFF272822), Color(0xFFF8F8F2), Color(0xFFF92672), Color(0xFFE6DB74), Color(0xFF75715E), Color(0xFFAE81FF))
    "GitHub Light" -> EditorPalette(Color(0xFFFFFFFF), Color(0xFF24292F), Color(0xFFCF222E), Color(0xFF0A3069), Color(0xFF6E7781), Color(0xFF0550AE))
    "Nord" -> EditorPalette(Color(0xFF2E3440), Color(0xFFD8DEE9), Color(0xFF81A1C1), Color(0xFFA3BE8C), Color(0xFF616E88), Color(0xFFB48EAD))
    "Solarized Light" -> EditorPalette(Color(0xFFFDF6E3), Color(0xFF586E75), Color(0xFF859900), Color(0xFF2AA198), Color(0xFF93A1A1), Color(0xFF268BD2))
    "Solarized Dark" -> EditorPalette(Color(0xFF002B36), Color(0xFF839496), Color(0xFF859900), Color(0xFF2AA198), Color(0xFF586E75), Color(0xFF268BD2))
    "One Dark" -> EditorPalette(Color(0xFF282C34), Color(0xFFABB2BF), Color(0xFFC678DD), Color(0xFF98C379), Color(0xFF5C6370), Color(0xFFD19A66))
    else -> EditorPalette(Color(0xFF0D1117), Color(0xFFE6EDF3), Color(0xFFFF7B72), Color(0xFFA5D6FF), Color(0xFF8B949E), Color(0xFF79C0FF))
}

private fun highlighted(source: String, p: EditorPalette) = buildAnnotatedString {
    val regex = Regex("""(?s)(//[^\n]*|#[^\n]*|/\*.*?\*/)|("(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*')|\b\d+(?:\.\d+)?\b|\b(?:fun|val|var|class|object|if|else|when|for|while|return|import|package|public|private|static|const|let|function|def|async|await|new|true|false|null|None|try|catch|throw|extends|implements|int|string|void|return|from|as|export|default)\b""")
    var cursor = 0
    regex.findAll(source).forEach { m ->
        append(source.substring(cursor, m.range.first))
        val token = m.value
        val color = when {
            token.startsWith("//") || token.startsWith("/*") || token.startsWith("#") -> p.comment
            token.startsWith("\"") || token.startsWith("'") -> p.string
            token.firstOrNull()?.isDigit() == true -> p.number
            else -> p.keyword
        }
        withStyle(SpanStyle(color = color, fontWeight = FontWeight.Medium)) { append(token) }
        cursor = m.range.last + 1
    }
    append(source.substring(cursor))
}

@Composable
fun EditorScreen(
    documents: List<EditorDocument>,
    onClose: (EditorDocument) -> Unit,
    settings: AppSettings
) {
    val scope = rememberCoroutineScope()
    var active by remember(documents) { mutableIntStateOf((documents.size - 1).coerceAtLeast(0)) }
    var contents by remember { mutableStateOf(emptyMap<String, String>()) }
    var saved by remember { mutableStateOf(emptyMap<String, String>()) }
    LaunchedEffect(documents) {
        val additions = documents.filterNot { contents.containsKey(it.path) }.associate { it.path to it.initialText }
        if (additions.isNotEmpty()) {
            contents = contents + additions
            saved = saved + additions
        }
    }
    var pendingClose by remember { mutableStateOf<EditorDocument?>(null) }
    var showFind by remember { mutableStateOf(false) }
    var find by remember { mutableStateOf("") }
    var replace by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }

    if (documents.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
            Text("No document open", style = MaterialTheme.typography.titleLarge)
            Text("Open a text file in the file manager.")
        }
        return
    }
    val selectedIndex = active.coerceIn(0, documents.lastIndex)
    val doc = documents[selectedIndex]
    val text = contents[doc.path].orEmpty()
    val p = palette(settings.editorTheme)
    // Avoid expensive full-document syntax highlighting for large files.
    val useHighlighting = settings.syntaxHighlighting && text.length <= 20_000
    val highlightedText = remember(text, p, useHighlighting) {
        if (useHighlighting) highlighted(text, p) else buildAnnotatedString { append(text) }
    }

    Column(Modifier.fillMaxSize().background(p.background)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(4.dp)) {
            documents.forEachIndexed { i, d ->
                TextButton(onClick = { active = i }) {
                    Text((if (i == active) "● " else "") + d.name +
                        if (contents[d.path] != saved[d.path]) " •" else "",
                        color = if (i == active) MaterialTheme.colorScheme.primary else p.foreground)
                }
                TextButton(onClick = {
                    if (contents[d.path] != saved[d.path]) pendingClose = d else {
                        contents = contents - d.path
                        saved = saved - d.path
                        onClose(d)
                    }
                }) { Text("×", color = p.comment) }
            }
        }
        HorizontalDivider(color = p.comment)
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
            IconButton(onClick = { showFind = !showFind }) { Text("⌕", color = p.foreground) }
            Spacer(Modifier.weight(1f))
            Text("${text.count { it == '\n' } + 1} lines", color = p.comment, modifier = Modifier.padding(12.dp))
            IconButton(onClick = {
                scope.launch {
                    try {
                        SshManager.writeText(doc.path, text)
                        saved = saved + (doc.path to text)
                        message = "Saved: ${doc.name}"
                    } catch (e: Exception) { message = "Save fehlgeschlagen: ${e.message}" }
                }
            }) { Icon(Icons.Filled.Save, "Save", tint = p.foreground) }
        }
        if (showFind) {
            Column(Modifier.padding(horizontal = 10.dp)) {
                OutlinedTextField(find, { find = it }, label = { Text("Find") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(replace, { replace = it }, label = { Text("Replace with") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                TextButton(onClick = {
                    if (find.isNotEmpty()) contents = contents + (doc.path to text.replace(find, replace))
                }) { Text("Replace all") }
            }
        }
        Row(Modifier.weight(1f).fillMaxWidth()) {
            val lineCount = text.count { it == '\n' } + 1
            if (settings.lineNumbers) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 8.dp, end = 8.dp, top = 8.dp)) {
                    repeat(lineCount) { Text("${it + 1}", color = p.comment, fontFamily = FontFamily.Monospace, fontSize = settings.editorFont.sp) }
                }
            }
            BasicTextField(
                value = text,
                onValueChange = { value ->
                    val autoIndented = if (value.length > text.length && value.endsWith("\n")) {
                        val currentLine = text.substringAfterLast('\n')
                        val indent = currentLine.takeWhile { it == ' ' || it == '\t' }
                        value + indent + if (currentLine.trimEnd().endsWith("{")) "    " else ""
                    } else value
                    contents = contents + (doc.path to autoIndented)
                },
                modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(8.dp),
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = p.foreground, fontFamily = FontFamily.Monospace, fontSize = settings.editorFont.sp, lineHeight = (settings.editorFont * 1.45f).sp
                ),
                visualTransformation = VisualTransformation { value ->
                    TransformedText(if (useHighlighting) highlightedText else buildAnnotatedString { append(value.text) }, OffsetMapping.Identity)
                }
            )
        }
        message?.let {
            Text(it, color = p.foreground, modifier = Modifier.fillMaxWidth().background(p.comment.copy(alpha = .18f)).padding(8.dp))
        }
    }
    pendingClose?.let { d ->
        AlertDialog(
            onDismissRequest = { pendingClose = null },
            title = { Text("Discard changes?") },
            text = { Text("${d.name} contains unsaved changes.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingClose = null
                    contents = contents - d.path
                    saved = saved - d.path
                    onClose(d)
                }) { Text("Discard") }
            },
            dismissButton = {
                TextButton(onClick = { pendingClose = null }) { Text("Cancel") }
            }
        )
    }
}
