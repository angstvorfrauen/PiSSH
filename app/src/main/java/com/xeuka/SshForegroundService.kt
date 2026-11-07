package com.xeuka.pissh

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import androidx.core.app.NotificationCompat

class SshForegroundService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var reconnectJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "PiSSH connection", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Shows an active SSH connection"
                }
            )
        }

        // Partial wake lock: keeps the CPU awake so SSH KeepAlive packets
        // are sent on time and are not delayed by Doze mode.
        // This is the main reason SSH connections in the background otherwise
        // nach ein paar Minuten "einfach so" timeouten.
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK, "PiSSH:connectionWakeLock"
        ).apply {
            setReferenceCounted(false)
        }

        // Bei jedem Netzwerkwechsel (WLAN <-> Mobilfunk, neue IP, kurzer Ausfall)
        // sofort reconnecten, statt bis zu 5s auf den Poll-Loop zu warten.
        connectivityManager = getSystemService(ConnectivityManager::class.java)
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                serviceScope.launch {
                    if (!SshManager.isConnected) {
                        try { SshManager.reconnect() } catch (_: Exception) { }
                    }
                }
            }
        }
        try {
            connectivityManager?.registerDefaultNetworkCallback(networkCallback!!)
        } catch (_: Exception) { }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("PiSSH verbunden")
            .setContentText("SSH connection is kept alive in the background")
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
        startForeground(NOTIFICATION_ID, notification)

        if (wakeLock?.isHeld == false) {
            wakeLock?.acquire()
        }

        if (reconnectJob == null) {
            reconnectJob = serviceScope.launch {
                var retrySeconds = 2L
                while (isActive) {
                    delay(5000)
                    val enabled = getSharedPreferences("pissh", MODE_PRIVATE).getBoolean("autoReconnect", true)
                    if (!enabled || SshManager.isConnected) {
                        retrySeconds = 2L
                        continue
                    }
                    try {
                        delay(retrySeconds * 1000)
                        if (SshManager.reconnect()) retrySeconds = 2L
                    } catch (_: Exception) {
                        retrySeconds = (retrySeconds * 2).coerceAtMost(60L)
                    }
                }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        reconnectJob?.cancel()
        serviceScope.cancel()
        try { networkCallback?.let { connectivityManager?.unregisterNetworkCallback(it) } } catch (_: Exception) { }
        if (wakeLock?.isHeld == true) wakeLock?.release()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "pissh_connection"
        private const val NOTIFICATION_ID = 2401
    }
}
