package com.xlocalhost.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != "android.intent.action.QUICKBOOT_POWERON"
        ) return

        val prefs = context.getSharedPreferences("xlocalhost_prefs", Context.MODE_PRIVATE)
        val autostart = prefs.getBoolean("autostart_on_boot", false)
        if (!autostart) {
            Log.d(TAG, "Autostart on boot is disabled — skipping.")
            return
        }

        val uriString = prefs.getString("folder_uri", null)
        val port = prefs.getInt("port", 8080)

        if (uriString == null) {
            Log.w(TAG, "No folder URI saved — cannot autostart server.")
            return
        }

        Log.i(TAG, "Boot completed — starting server on port $port")

        val allowMod = prefs.getBoolean("allow_mod", false)

        val serviceIntent = Intent(context, ServerService::class.java).apply {
            action = ServerService.ACTION_START
            putExtra(ServerService.EXTRA_FOLDER_URI, uriString)
            putExtra(ServerService.EXTRA_PORT, port)
            putExtra(ServerService.EXTRA_ALLOW_MOD, allowMod)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}
