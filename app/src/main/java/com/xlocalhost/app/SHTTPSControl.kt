package com.xlocalhost.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class SHTTPSControl : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val password = intent.getStringExtra("password")
        val path = intent.getStringExtra("path")
        
        // Security check: Verify password if required
        val prefs = context.getSharedPreferences("xlocalhost_prefs", Context.MODE_PRIVATE)
        val savedPassword = prefs.getString("external_control_password", "")
        
        if (!savedPassword.isNullOrEmpty() && password != savedPassword) {
            Log.e("SHTTPSControl", "Unauthorized control attempt: invalid password")
            return
        }

        when (action) {
            "START" -> {
                val serviceIntent = Intent(context, ServerService::class.java).apply {
                    this.action = ServerService.ACTION_START
                    // Use provided path or fallback to saved URI
                    val uri = path ?: prefs.getString("folder_uri", null)
                    putExtra(ServerService.EXTRA_FOLDER_URI, uri)
                    putExtra(ServerService.EXTRA_PORT, prefs.getInt("port", 8080))
                    putExtra(ServerService.EXTRA_ALLOW_MOD, prefs.getBoolean("allow_mod", false))
                }
                context.startService(serviceIntent)
            }
            "STOP" -> {
                val serviceIntent = Intent(context, ServerService::class.java).apply {
                    this.action = ServerService.ACTION_STOP
                }
                context.startService(serviceIntent)
            }
        }
    }
}
