package com.xeuka.pissh

import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.ChannelShell
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

data class RemoteFile(
    val name: String, val path: String, val isDir: Boolean, val size: Long,
    val modifiedSeconds: Long = 0L, val permissions: String = ""
)

fun shellQuote(s: String) = "'" + s.replace("'", "'\\''") + "'"

class TerminalHandle internal constructor(
    private val channel: ChannelShell,
    private val output: OutputStream,
    private val reader: Job
) {
    val isConnected: Boolean get() = channel.isConnected
    suspend fun send(value: String) = withContext(Dispatchers.IO) {
        output.write(value.toByteArray(Charsets.UTF_8))
        output.flush()
    }
    fun close() {
        reader.cancel()
        channel.disconnect()
    }
}

object SshManager {
    private var session: Session? = null
    private data class ConnectionConfig(val host: String, val port: Int, val user: String, val password: String, val keepAlive: Int)
    @Volatile private var lastConnection: ConnectionConfig? = null
    var host = ""
        private set
    var user = ""
        private set

    val isConnected get() = session?.isConnected == true

    suspend fun connect(h: String, port: Int, u: String, password: String, keepAliveSeconds: Int = 15) = withContext(Dispatchers.IO) {
        disconnect(clearCredentials = false)
        val s = JSch().getSession(u, h, port)
        s.setPassword(password)
        // Warning: host keys are not verified (acceptable only on a trusted home network; add known_hosts verification otherwise)
        // For production use, pin the host key / known_hosts instead of accepting arbitrary hosts.
        s.setConfig("StrictHostKeyChecking", "no")
        s.setConfig("PreferredAuthentications", "password,keyboard-interactive")
        s.setConfig("compression.s2c", "zlib@openssh.com,zlib,none")
        s.setConfig("compression.c2s", "zlib@openssh.com,zlib,none")
        s.setServerAliveInterval(keepAliveSeconds.coerceIn(5, 120) * 1000)
        s.setServerAliveCountMax(3)
        s.connect(10000)
        session = s
        host = h
        user = u
        lastConnection = ConnectionConfig(h, port, u, password, keepAliveSeconds)
    }

    suspend fun reconnect(): Boolean {
        if (isConnected) return true
        val config = lastConnection ?: return false
        connect(config.host, config.port, config.user, config.password, config.keepAlive)
        return isConnected
    }

    fun disconnect(clearCredentials: Boolean = true) {
        try { session?.disconnect() } catch (_: Exception) {}
        session = null
        if (clearCredentials) lastConnection = null
    }

    suspend fun openTmuxTerminal(sessionName: String, onOutput: (String) -> Unit): TerminalHandle = withContext(Dispatchers.IO) {
        val active = session ?: error("Nicht verbunden")
        val channel = active.openChannel("shell") as ChannelShell
        channel.setPtyType("xterm-256color")
        channel.setPtySize(100, 32, 0, 0)
        val input = channel.inputStream
        val output = channel.outputStream
        channel.connect(10000)
        val safeName = shellQuote(sessionName)
        val setup = "if ! tmux has-session -t $safeName 2>/dev/null; then tmux new-session -d -s $safeName; tmux send-keys -t $safeName \"alias ls='ls -1 --color=auto'\" C-m; fi; tmux attach-session -t $safeName\r"
        output.write(setup.toByteArray(Charsets.UTF_8))
        output.flush()
        val reader = CoroutineScope(Dispatchers.IO).launch {
            val buffer = ByteArray(4096)
            try {
                while (isActive && channel.isConnected) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (count > 0) onOutput(String(buffer, 0, count, Charsets.UTF_8))
                }
            } catch (_: Exception) { }
        }
        TerminalHandle(channel, output, reader)
    }



    suspend fun killTmuxSession(sessionName: String) {
        exec("tmux kill-session -t ${shellQuote(sessionName)} 2>/dev/null || true")
    }

    /** Runs a command (stdout and stderr combined). 30-second timeout; interactive programs are not supported. */
    suspend fun exec(cmd: String, timeoutMs: Long = 30000): String = withContext(Dispatchers.IO) {
        val ch = session!!.openChannel("exec") as ChannelExec
        val out = ByteArrayOutputStream()
        try {
            ch.setCommand(cmd)
            ch.setOutputStream(out)
            ch.setErrStream(out)
            ch.connect(10000)
            withTimeout(timeoutMs) { while (!ch.isClosed) delay(50) }
            out.toString("UTF-8")
        } finally {
            ch.disconnect()
        }
    }

    private fun <T> sftp(block: (ChannelSftp) -> T): T {
        val c = session!!.openChannel("sftp") as ChannelSftp
        c.connect(10000)
        try { return block(c) } finally { c.disconnect() }
    }

    suspend fun home(): String = withContext(Dispatchers.IO) { sftp { it.home } }

    suspend fun list(path: String): List<RemoteFile> = withContext(Dispatchers.IO) {
        sftp { c ->
            @Suppress("UNCHECKED_CAST")
            val entries = c.ls(path) as java.util.Vector<ChannelSftp.LsEntry>
            val base = if (path == "/") "" else path.trimEnd('/')
            entries.filter { it.filename != "." && it.filename != ".." }.map {
                val full = "$base/${it.filename}"
                val isDir = if (it.attrs.isLink) {
                    try { c.stat(full).isDir } catch (_: Exception) { false }
                } else it.attrs.isDir
                RemoteFile(it.filename, full, isDir, it.attrs.size, it.attrs.mTime.toLong(), it.attrs.permissionsString)
            }.sortedWith(compareByDescending<RemoteFile> { it.isDir }.thenBy { it.name.lowercase() })
        }
    }

    suspend fun download(path: String, out: OutputStream) = withContext(Dispatchers.IO) { sftp { it.get(path, out) } }
    suspend fun upload(input: InputStream, remotePath: String) = withContext(Dispatchers.IO) { sftp { it.put(input, remotePath) } }
    suspend fun mkdir(path: String) = withContext(Dispatchers.IO) { sftp { it.mkdir(path) } }
    suspend fun chmod(path: String, mode: Int) = withContext(Dispatchers.IO) { sftp { it.chmod(mode, path) } }
    suspend fun stat(path: String): RemoteFile = withContext(Dispatchers.IO) {
        sftp { c ->
            val a = c.lstat(path)
            RemoteFile(path.substringAfterLast('/'), path, a.isDir, a.size, a.mTime.toLong(), a.permissionsString)
        }
    }
    suspend fun copy(from: String, to: String) = exec("cp -a -- ${shellQuote(from)} ${shellQuote(to)}")
    suspend fun move(from: String, to: String) = withContext(Dispatchers.IO) { sftp { it.rename(from, to) } }
    suspend fun readText(path: String, maxBytes: Long = 5L * 1024 * 1024): String = withContext(Dispatchers.IO) {
        sftp { c ->
            val attrs = c.lstat(path)
            val extension = path.substringAfterLast('.', "").lowercase()
            require(extension !in setOf("apk", "zip", "png", "jpg", "jpeg", "gif", "webp", "mp4", "mp3", "pdf", "jar", "class", "so", "dex")) {
                "Dieser Filetyp ist keine Textdatei."
            }
            require(!attrs.isDir) { "Folders cannot be opened in the editor." }
            require(attrs.size <= maxBytes) { "File is larger than 5 MB." }
            val bytes = c.get(path).use { it.readBytes() }
            require(bytes.none { it == 0.toByte() }) { "Binary files cannot be opened as text." }
            bytes.toString(Charsets.UTF_8)
        }
    }
    suspend fun writeText(path: String, content: String) = withContext(Dispatchers.IO) {
        sftp { c ->
            val tmp = "$path.pissh-tmp"
            val backup = "$path.pissh-backup"
            val existed = try { c.lstat(path); true } catch (_: Exception) { false }
            val oldMode = if (existed) try { c.lstat(path).permissions } catch (_: Exception) { 420 } else 420
            c.put(content.byteInputStream(Charsets.UTF_8), tmp)
            try {
                if (existed) {
                    c.chmod(oldMode, tmp)
                    try { c.rm(backup) } catch (_: Exception) { }
                    c.rename(path, backup)
                }
                c.rename(tmp, path)
                if (existed) try { c.rm(backup) } catch (_: Exception) { }
            } catch (e: Exception) {
                try { c.rm(tmp) } catch (_: Exception) { }
                if (existed) try { c.rename(backup, path) } catch (_: Exception) { }
                throw e
            }
        }
    }
    suspend fun search(path: String, query: String): List<RemoteFile> = withContext(Dispatchers.IO) {
        val safe = shellQuote(query)
        val output = exec("find ${shellQuote(path)} -maxdepth 5 -iname ${safe} -print 2>/dev/null | head -200")
        val results = mutableListOf<RemoteFile>()
        for (entry in output.lineSequence().filter { it.isNotBlank() }) {
            try { results += stat(entry) } catch (_: Exception) { }
        }
        results
    }
    suspend fun rename(from: String, to: String) = withContext(Dispatchers.IO) { sftp { it.rename(from, to) } }
    suspend fun delete(f: RemoteFile) {
        exec("rm -rf ${shellQuote(f.path)}")
    }
}
