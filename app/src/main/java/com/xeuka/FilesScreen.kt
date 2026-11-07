package com.xeuka.pissh

import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private fun humanSize(b: Long): String = when {
    b < 1024 -> "$b B"
    b < 1024 * 1024 -> "%.1f KB".format(b / 1024.0)
    b < 1024L * 1024 * 1024 -> "%.1f MB".format(b / 1024.0 / 1024)
    else -> "%.2f GB".format(b / 1024.0 / 1024 / 1024)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FilesScreen(settings: AppSettings, onOpenEditor: (EditorDocument) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var path by remember { mutableStateOf("") }
    var files by remember { mutableStateOf(listOf<RemoteFile>()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<RemoteFile?>(null) }
    var renameTarget by remember { mutableStateOf<RemoteFile?>(null) }
    var deleteTarget by remember { mutableStateOf<RemoteFile?>(null) }
    var transferTarget by remember { mutableStateOf<Pair<RemoteFile, Boolean>?>(null) }
    var chmodTarget by remember { mutableStateOf<RemoteFile?>(null) }
    var showMkdir by remember { mutableStateOf(false) }
    var showCreateFile by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf("Name") }
    var pendingDownload by remember { mutableStateOf<RemoteFile?>(null) }

    fun toast(s: String) = Toast.makeText(ctx, s, Toast.LENGTH_SHORT).show()

    fun load(p: String) {
        scope.launch {
            loading = true
            try {
                val target = p.ifEmpty { SshManager.home() }
                files = SshManager.list(target)
                path = target
                error = null
            } catch (e: Exception) { error = e.message }
            loading = false
        }
    }

    fun parent() = path.substringBeforeLast('/').ifEmpty { "/" }

    LaunchedEffect(Unit) { load("") }
    BackHandler(enabled = path.isNotEmpty() && path != "/") { load(parent()) }

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val f = pendingDownload
        if (uri != null && f != null) scope.launch {
            try {
                ctx.contentResolver.openOutputStream(uri)!!.use { SshManager.download(f.path, it) }
                toast("Downloaded: ${f.name}")
            } catch (e: Exception) { toast("Error: ${e.message}") }
        }
    }

    val uploadLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            try {
                var name = "upload"
                ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                    val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (c.moveToFirst() && i >= 0) name = c.getString(i)
                }
                val target = (if (path == "/") "" else path) + "/" + name
                ctx.contentResolver.openInputStream(uri)!!.use { SshManager.upload(it, target) }
                toast("Uploaded: $name")
                load(path)
            } catch (e: Exception) { toast("Error: ${e.message}") }
        }
    }

    val accent = Color(0xFFFF3030)
    val bg = Color(0xFF0A0A0A)
    val panel = Color(0xFF0F0F0F)
    val muted = Color(0xFF8A8A8A)
    val visibleFiles = files
        .filter { (settings.showHidden || !it.name.startsWith(".")) && it.name.contains(search, true) }
        .let { list -> when (sort) {
            "Size" -> list.sortedByDescending { it.size }
            "Date" -> list.sortedByDescending { it.modifiedSeconds }
            else -> list.sortedWith(compareByDescending<RemoteFile> { it.isDir }.thenBy { it.name.lowercase() })
        } }

    Column(Modifier.fillMaxSize().background(bg)) {
        // MT Manager-inspired file toolbar
        Row(
            Modifier.fillMaxWidth().background(panel).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("File manager", color = Color.White, style = MaterialTheme.typography.titleLarge,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                Text("${visibleFiles.size} items", color = muted, style = MaterialTheme.typography.labelSmall)
            }
            TextButton(onClick = { load(path) }) { Text("Refresh", color = accent) }
            TextButton(onClick = { uploadLauncher.launch("*/*") }) { Text("Upload", color = Color.White) }
            TextButton(onClick = { showMkdir = true }) { Text("+ Folder", color = Color.White) }
        }

        // Current directory / breadcrumb-style path
        Row(
            Modifier.fillMaxWidth().background(Color(0xFF141414)).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("PATH", color = accent, style = MaterialTheme.typography.labelSmall,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            Spacer(Modifier.width(10.dp))
            Text(path.ifBlank { "/" }, color = Color(0xFFE6E6E6), style = MaterialTheme.typography.bodySmall,
                maxLines = 1, overflow = TextOverflow.MiddleEllipsis, modifier = Modifier.weight(1f))
            TextButton(onClick = { load(parent()) }, enabled = path.isNotEmpty() && path != "/") {
                Text("UP", color = if (path.isNotEmpty() && path != "/") accent else muted)
            }
        }

        OutlinedTextField(
            value = search, onValueChange = { search = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            singleLine = true, placeholder = { Text("Search in this folder…", color = muted) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = accent, unfocusedBorderColor = Color(0xFF303030),
                focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                cursorColor = accent
            )
        )

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("SORT", color = muted, style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(start = 6.dp, end = 4.dp))
            listOf("Name", "Size", "Date").forEach { option ->
                val active = sort == option
                TextButton(onClick = { sort = option }) {
                    Text(option, color = if (active) accent else muted,
                        fontWeight = if (active) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal)
                }
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { showCreateFile = true }) { Text("+ File", color = Color.White) }
        }
        HorizontalDivider(color = Color(0xFF252525), thickness = 1.dp)

        if (loading) LinearProgressIndicator(
            Modifier.fillMaxWidth(), color = accent, trackColor = Color(0xFF242424)
        )
        error?.let {
            Text(it, Modifier.fillMaxWidth().padding(14.dp), color = MaterialTheme.colorScheme.error)
        }

        if (!loading && error == null && visibleFiles.isEmpty()) {
            Column(
                Modifier.fillMaxWidth().weight(1f).padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("No files found", color = Color.White, style = MaterialTheme.typography.titleMedium)
                Text(if (search.isBlank()) "This directory is empty." else "Try a different search term.",
                    color = muted, style = MaterialTheme.typography.bodySmall)
            }
        } else {
            LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                items(visibleFiles, key = { it.path }) { f ->
                    Row(
                        Modifier.fillMaxWidth()
                            .background(if (selected == f) Color(0xFF241010) else bg)
                            .combinedClickable(
                                onClick = { if (f.isDir) load(f.path) else selected = f },
                                onLongClick = { selected = f }
                            )
                            .padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(42.dp).background(
                                if (f.isDir) Color(0xFF241313) else Color(0xFF171717),
                                MaterialTheme.shapes.medium
                            ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (f.isDir) Icons.Filled.Folder else Icons.Filled.InsertDriveFile,
                                contentDescription = null,
                                tint = if (f.isDir) accent else Color(0xFFB7B7B7),
                                modifier = Modifier.size(23.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(f.name, color = Color(0xFFF2F2F2), maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                            Text(
                                if (f.isDir) "Folder  •  ${f.permissions.ifBlank { "—" }}"
                                else "${humanSize(f.size)}  •  ${f.permissions.ifBlank { "—" }}",
                                color = muted, style = MaterialTheme.typography.labelSmall,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(if (f.isDir) "›" else "⋮", color = if (f.isDir) accent else muted,
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(start = 8.dp))
                    }
                    HorizontalDivider(color = Color(0xFF1D1D1D), thickness = .6.dp)
                }
            }
        }

        // Persistent quick actions, similar to MT Manager's bottom action strip
        Row(
            Modifier.fillMaxWidth().background(panel).padding(horizontal = 4.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { showCreateFile = true }) { Text("New file", color = Color.White) }
            TextButton(onClick = { showMkdir = true }) { Text("New folder", color = Color.White) }
            TextButton(onClick = { uploadLauncher.launch("*/*") }) { Text("Upload", color = Color.White) }
            TextButton(onClick = { load(path) }) { Text("Reload", color = accent) }
        }
    }

    selected?.let { f ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(f.name) },
            text = {
                Column {
                    if (!f.isDir) TextButton({
                        selected = null
                        scope.launch {
                            try {
                                val content = SshManager.readText(f.path)
                                onOpenEditor(EditorDocument(f.path, f.name, content))
                            } catch (e: Exception) { toast("Could not open: ${e.message}") }
                        }
                    }) { Text("Open in editor") }
                    if (!f.isDir) TextButton({ pendingDownload = f; selected = null; saveLauncher.launch(f.name) }) { Text("Download") }
                    Text("Path: ${f.path}", style = MaterialTheme.typography.bodySmall)
                    Text("Permissions: ${f.permissions.ifBlank { "unknown" }}", style = MaterialTheme.typography.bodySmall)
                    TextButton({ renameTarget = f; selected = null }) { Text("Rename") }
                    TextButton({ transferTarget = f to false; selected = null }) { Text("Copy…") }
                    TextButton({ transferTarget = f to true; selected = null }) { Text("Move…") }
                    TextButton({ chmodTarget = f; selected = null }) { Text("Permissions…") }
                    TextButton({ deleteTarget = f; selected = null }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = { TextButton({ selected = null }) { Text("Close") } }
        )
    }

    if (showCreateFile) {
        var filename by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateFile = false },
            title = { Text("New file") },
            text = { OutlinedTextField(filename, { filename = it }, label = { Text("Filename") }, singleLine = true) },
            confirmButton = {
                TextButton(enabled = filename.isNotBlank(), onClick = {
                    val target = (if (path == "/") "" else path) + "/" + filename
                    scope.launch {
                        try { SshManager.writeText(target, ""); toast("File created"); load(path) }
                        catch (e: Exception) { toast("Error: ${e.message}") }
                    }
                    showCreateFile = false
                }) { Text("Create") }
            },
            dismissButton = { TextButton({ showCreateFile = false }) { Text("Cancel") } }
        )
    }

    if (showMkdir || renameTarget != null) {
        val rn = renameTarget
        var text by remember(rn, showMkdir) { mutableStateOf(rn?.name ?: "") }
        AlertDialog(
            onDismissRequest = { showMkdir = false; renameTarget = null },
            title = { Text(if (rn != null) "Rename" else "New folder") },
            text = { OutlinedTextField(text, { text = it }, singleLine = true) },
            confirmButton = {
                TextButton({
                    val base = if (path == "/") "" else path
                    scope.launch {
                        try {
                            if (rn != null) SshManager.rename(rn.path, "$base/$text") else SshManager.mkdir("$base/$text")
                            load(path)
                        } catch (e: Exception) { toast("Error: ${e.message}") }
                    }
                    showMkdir = false; renameTarget = null
                }, enabled = text.isNotBlank()) { Text("OK") }
            },
            dismissButton = { TextButton({ showMkdir = false; renameTarget = null }) { Text("Cancel") } }
        )
    }

    transferTarget?.let { (file, move) ->
        var destination by remember(file, move) { mutableStateOf(file.path + if (file.isDir) "_copy" else ".copy") }
        AlertDialog(
            onDismissRequest = { transferTarget = null },
            title = { Text(if (move) "Move" else "Copy") },
            text = { OutlinedTextField(destination, { destination = it }, label = { Text("Destination path") }, singleLine = true) },
            confirmButton = {
                TextButton(enabled = destination.isNotBlank(), onClick = {
                    transferTarget = null
                    scope.launch {
                        try {
                            if (move) SshManager.move(file.path, destination) else SshManager.copy(file.path, destination)
                            toast(if (move) "Moved" else "Copied")
                            load(path)
                        } catch (e: Exception) { toast("Error: ${e.message}") }
                    }
                }) { Text(if (move) "Move" else "Copy") }
            },
            dismissButton = { TextButton({ transferTarget = null }) { Text("Cancel") } }
        )
    }

    chmodTarget?.let { file ->
        var mode by remember(file) { mutableStateOf("755") }
        AlertDialog(
            onDismissRequest = { chmodTarget = null },
            title = { Text("Change permissions") },
            text = {
                Column {
                    Text("Current: ${file.permissions}")
                    OutlinedTextField(mode, { mode = it.filter(Char::isDigit).take(4) }, label = { Text("Octal mode (e.g. 755)") })
                }
            },
            confirmButton = {
                TextButton(enabled = mode.isNotBlank(), onClick = {
                    chmodTarget = null
                    scope.launch {
                        try { SshManager.chmod(file.path, mode.toInt(8)); toast("Permissions updated"); load(path) }
                        catch (e: Exception) { toast("Error: ${e.message}") }
                    }
                }) { Text("Apply") }
            },
            dismissButton = { TextButton({ chmodTarget = null }) { Text("Cancel") } }
        )
    }

    deleteTarget?.let { f ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete?") },
            text = { Text("${f.name} will be permanently deleted${if (f.isDir) " (including contents)" else ""}.") },
            confirmButton = {
                TextButton({
                    deleteTarget = null
                    scope.launch {
                        try { SshManager.delete(f); load(path) } catch (e: Exception) { toast("Error: ${e.message}") }
                    }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton({ deleteTarget = null }) { Text("Cancel") } }
        )
    }
}
