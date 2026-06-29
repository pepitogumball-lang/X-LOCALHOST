package com.xlocalhost.app

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
        const val ACTION_START       = "com.flinger.localserver.ACTION_START"
        const val ACTION_STOP        = "com.flinger.localserver.ACTION_STOP"
        const val EXTRA_FOLDER_URI   = "extra_folder_uri"
        const val EXTRA_PORT         = "extra_port"
        const val EXTRA_ALLOW_MOD    = "extra_allow_mod"
        const val CHANNEL_ID         = "xlocalhost_channel"
        const val NOTIFICATION_ID    = 2001
        private const val TAG        = "ServerService"
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
                val port      = intent.getIntExtra(EXTRA_PORT, 8080)
                val allowMod  = intent.getBooleanExtra(EXTRA_ALLOW_MOD, false)
                if (uriString == null) {
                    Log.e(TAG, "No folder URI received.")
                    stopSelf()
                    return START_NOT_STICKY
                }
                val folderUri = Uri.parse(uriString)
                acquireWakeLock()
                startForeground(NOTIFICATION_ID, buildNotification(port))
                startWebServer(folderUri, port, allowMod)
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

    private fun startWebServer(folderUri: Uri, port: Int, allowMod: Boolean = false) {
        try {
            webServer?.stop()
            webServer = LocalWebServer(applicationContext, folderUri, port, allowMod)
            webServer?.start()
            // Persist config so BootReceiver can restart the server after reboot
            applicationContext.getSharedPreferences("xlocalhost_prefs", MODE_PRIVATE)
                .edit()
                .putString("folder_uri", folderUri.toString())
                .putInt("port", port)
                .putBoolean("allow_mod", allowMod)
                .apply()
            Log.i(TAG, "Server started on port $port")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting server: ${e.message}", e)
            stopSelf()
        }
    }

    private fun stopWebServer() {
        try {
            webServer?.stop()
            webServer = null
            Log.i(TAG, "Server stopped.")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping server: ${e.message}", e)
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
            if (wakeLock?.isHeld == true) wakeLock?.release()
            wakeLock = null
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing WakeLock: ${e.message}")
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "x-localhost Server", NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Local HTTP server running"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(port: Int): Notification {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        else PendingIntent.FLAG_UPDATE_CURRENT

        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply { this.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
            flags
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, ServerService::class.java).apply { action = ACTION_STOP },
            flags
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("x-localhost running")
            .setContentText("Listening on port $port")
            .setSmallIcon(R.drawable.ic_server)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .addAction(R.drawable.ic_server, "Stop", stopIntent)
            .build()
    }
}
