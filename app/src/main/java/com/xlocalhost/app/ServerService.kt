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
        var isRunning = false
            private set

        const val ACTION_START       = "com.flinger.localserver.ACTION_START"
        const val ACTION_STOP        = "com.flinger.localserver.ACTION_STOP"
        const val EXTRA_FOLDER_URI   = "extra_folder_uri"
        const val EXTRA_PORT         = "extra_port"
        const val EXTRA_ALLOW_MOD    = "extra_allow_mod"
        const val EXTRA_ENABLE_SQLITE = "extra_enable_sqlite"
        const val EXTRA_DB_MODIFY    = "extra_db_modify"
        const val EXTRA_DB_CUSTOM_SQL = "extra_db_custom_sql"
        const val EXTRA_SERVE_WELCOME_FILE = "extra_serve_welcome_file"
        const val EXTRA_CORS_ORIGIN  = "extra_cors_origin"
        const val EXTRA_CORS_METHODS = "extra_cors_methods"
        const val EXTRA_CORS_HEADERS = "extra_cors_headers"
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
                val enableSqlite = intent.getBooleanExtra(EXTRA_ENABLE_SQLITE, false)
                val dbModify = intent.getBooleanExtra(EXTRA_DB_MODIFY, false)
                val dbCustomSql = intent.getBooleanExtra(EXTRA_DB_CUSTOM_SQL, false)
                val serveWelcomeFile = intent.getBooleanExtra(EXTRA_SERVE_WELCOME_FILE, true)
                val corsOrigin  = intent.getStringExtra(EXTRA_CORS_ORIGIN)
                val corsMethods = intent.getStringExtra(EXTRA_CORS_METHODS)
                val corsHeaders = intent.getStringExtra(EXTRA_CORS_HEADERS)
                
                val folderUri = if (uriString != null && uriString != "null") {
                    Uri.parse(uriString)
                } else {
                    Uri.fromFile(applicationContext.filesDir)
                }
                
                acquireWakeLock()
                startForeground(NOTIFICATION_ID, buildNotification(port))
                
                val cors = if (corsOrigin != null) {
                    LocalWebServer.CorsConfig(corsOrigin, corsMethods ?: "*", corsHeaders ?: "*")
                } else {
                    null
                }
                
                startWebServer(folderUri, port, allowMod, enableSqlite, dbModify, dbCustomSql, cors, serveWelcomeFile)
                isRunning = true
            }
            ACTION_STOP -> {
                stopWebServer()
                releaseWakeLock()
                stopForeground(STOP_FOREGROUND_REMOVE)
                isRunning = false
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

    private fun startWebServer(
        folderUri: Uri,
        port: Int,
        allowMod: Boolean = false,
        enableSqlite: Boolean = false,
        dbModify: Boolean = false,
        dbCustomSql: Boolean = false,
        corsConfig: LocalWebServer.CorsConfig? = null,
        serveWelcomeFile: Boolean = false
    ) {
        try {
            webServer?.stop()
            webServer = LocalWebServer(
                context = applicationContext, 
                folderUri = folderUri, 
                port = port, 
                allowModification = allowMod, 
                allowSqlite = enableSqlite, 
                allowDbModify = dbModify, 
                allowCustomSql = dbCustomSql, 
                corsConfig = corsConfig, 
                serveWelcomeFile = serveWelcomeFile
            ).apply {
                onRequestLog = { log ->
                    val intent = Intent("com.xlocalhost.app.REQUEST_LOG").apply {
                        putExtra("method", log.method)
                        putExtra("path", log.path)
                        putExtra("status", log.statusCode)
                        putExtra("desc", log.statusDescription)
                        putExtra("duration", log.durationMs)
                        putExtra("ip", log.clientIp)
                        putExtra("started", log.startedAt)
                        putExtra("ended", log.endedAt)
                    }
                    sendBroadcast(intent)
                }
            }
            webServer?.start()
            
            // Persist config
            applicationContext.getSharedPreferences("xlocalhost_prefs", MODE_PRIVATE)
                .edit()
                .putString("folder_uri", folderUri.toString())
                .putInt("port", port)
                .putBoolean("allow_mod", allowMod)
                .putBoolean("enable_sqlite", enableSqlite)
                .apply()
                
            Log.i(TAG, "Server started on port $port (SQLite: $enableSqlite)")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting server: ${e.message}", e)
            isRunning = false
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
            CHANNEL_ID, "X-LOCALHOST Server", NotificationManager.IMPORTANCE_LOW
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
            .setContentTitle("X-LOCALHOST Live")
            .setContentText("Listening on port $port")
            .setSmallIcon(android.R.drawable.stat_sys_download_done) // Fallback icon
            .setContentIntent(openIntent)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopIntent)
            .build()
    }
}
