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
import android.util.Log
import androidx.core.app.NotificationCompat

class ServerService : Service() {

    private var webServer: LocalWebServer? = null

    companion object {
        const val ACTION_START = "com.flinger.localserver.ACTION_START"
        const val ACTION_STOP = "com.flinger.localserver.ACTION_STOP"
        const val EXTRA_FOLDER_URI = "extra_folder_uri"
        const val CHANNEL_ID = "flinger_server_channel"
        const val NOTIFICATION_ID = 1001
        private const val TAG = "ServerService"
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
                if (uriString == null) {
                    Log.e(TAG, "No se recibió URI de carpeta.")
                    stopSelf()
                    return START_NOT_STICKY
                }
                val folderUri = Uri.parse(uriString)
                startForeground(NOTIFICATION_ID, buildNotification())
                startWebServer(folderUri)
            }
            ACTION_STOP -> {
                stopWebServer()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopWebServer()
        super.onDestroy()
    }

    private fun startWebServer(folderUri: Uri) {
        try {
            webServer?.stop()
            webServer = LocalWebServer(applicationContext, folderUri)
            webServer?.start()
            Log.i(TAG, "Servidor iniciado en puerto 8080")
        } catch (e: Exception) {
            Log.e(TAG, "Error al iniciar el servidor: ${e.message}", e)
            stopSelf()
        }
    }

    private fun stopWebServer() {
        try {
            webServer?.stop()
            webServer = null
            Log.i(TAG, "Servidor detenido.")
        } catch (e: Exception) {
            Log.e(TAG, "Error al detener el servidor: ${e.message}", e)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_desc)
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, openAppIntent, pendingFlags)

        val stopIntent = Intent(this, ServerService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(this, 1, stopIntent, pendingFlags)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(R.drawable.ic_server)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .addAction(R.drawable.ic_server, getString(R.string.btn_stop_server), stopPending)
            .build()
    }
}
