package com.flinger.localserver

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat

class ServerService : Service() {

    private var webServer: LocalWebServer? = null
    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        const val ACTION_START = "com.flinger.localserver.ACTION_START"
        const val ACTION_STOP = "com.flinger.localserver.ACTION_STOP"
        const val EXTRA_FOLDER_URI = "extra_folder_uri"
        const val EXTRA_PORT = "extra_port"
        const val CHANNEL_ID = "xlocalhost_channel"
        const val NOTIFICATION_ID = 2001
        private const val TAG = "ServerService"
        private const val WAKELOCK_TAG = "xLocalhost::ServerWakeLock"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val uriString = intent.getStringExtra(EXTRA_FOLDER_URI)
                val port = intent.getIntExtra(EXTRA_PORT, 8080)
                if (uriString == null) {
                    Log.e(TAG, "No URI recibida.")
                    stopSelf()
                    return START_NOT_STICKY
                }
                val folderUri = Uri.parse(uriString)
                acquireWakeLock()
                startForeground(NOTIFICATION_ID, buildNotification(port))
                startWebServer(folderUri, port)
            }
            ACTION_STOP -> {
                stopWebServer()
                releaseWakeLock()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopWebServer()
        releaseWakeLock()
        super.onDestroy()
    }

    private fun startWebServer(folderUri: Uri, port: Int) {
        try {
            webServer?.stop()
            webServer = LocalWebServer(applicationContext, folderUri, port)
            webServer?.start()
            Log.i(TAG, "Servidor iniciado en puerto $port")
        } catch (e: Exception) {
            Log.e(TAG, "Error al iniciar servidor: ${e.message}", e)
            stopSelf()
        }
    }

    private fun stopWebServer() {
        try {
            webServer?.stop()
            webServer = null
            Log.i(TAG, "Servidor detenido.")
        } catch (e: Exception) {
            Log.e(TAG, "Error al detener servidor: ${e.message}", e)
        }
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG).apply {
            acquire(10L * 60 * 60 * 1000)
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
            wakeLock = null
        } catch (e: Exception) {
            Log.w(TAG, "Error liberando WakeLock: ${e.message}")
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "x-localhost Server",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Servidor HTTP local activo"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(port: Int): Notification {
        val pendingFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
            pendingFlags
        )

        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, ServerService::class.java).apply { action = ACTION_STOP },
            pendingFlags
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("x-localhost corriendo")
            .setContentText("Escuchando en puerto $port")
            .setSmallIcon(R.drawable.ic_server)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .addAction(R.drawable.ic_server, "Detener", stopIntent)
            .build()
    }
}
