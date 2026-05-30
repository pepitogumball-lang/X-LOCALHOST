package com.flinger.localserver

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.flinger.localserver.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var selectedFolderUri: Uri? = null
    private var isServerRunning = false

    private val openDocumentTree = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            contentResolver.takePersistableUriPermission(uri, flags)
            selectedFolderUri = uri
            val path = uri.lastPathSegment ?: uri.toString()
            binding.tvFolderPath.text = path
            binding.btnStartServer.isEnabled = true
            Toast.makeText(this, "Carpeta seleccionada", Toast.LENGTH_SHORT).show()
        }
    }

    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(this, getString(R.string.permission_required), Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        requestNotificationPermissionIfNeeded()
        requestIgnoreBatteryOptimizations()
        restorePersistedFolder()

        binding.btnSelectFolder.setOnClickListener {
            openDocumentTree.launch(null)
        }

        binding.btnStartServer.setOnClickListener {
            val uri = selectedFolderUri ?: return@setOnClickListener
            startLocalServer(uri)
        }

        binding.btnStopServer.setOnClickListener {
            stopLocalServer()
        }
    }

    private fun restorePersistedFolder() {
        val persistedUris = contentResolver.persistedUriPermissions
        if (persistedUris.isNotEmpty()) {
            val uriPermission = persistedUris.last()
            selectedFolderUri = uriPermission.uri
            val path = uriPermission.uri.lastPathSegment ?: uriPermission.uri.toString()
            binding.tvFolderPath.text = path
            binding.btnStartServer.isEnabled = true
        }
    }

    private fun startLocalServer(uri: Uri) {
        val intent = Intent(this, ServerService::class.java).apply {
            action = ServerService.ACTION_START
            putExtra(ServerService.EXTRA_FOLDER_URI, uri.toString())
        }
        ContextCompat.startForegroundService(this, intent)
        isServerRunning = true
        updateUiRunning()
    }

    private fun stopLocalServer() {
        val intent = Intent(this, ServerService::class.java).apply {
            action = ServerService.ACTION_STOP
        }
        startService(intent)
        isServerRunning = false
        updateUiStopped()
    }

    private fun updateUiRunning() {
        binding.tvStatus.text = getString(R.string.status_running)
        binding.tvStatus.setTextColor(ContextCompat.getColor(this, R.color.server_running))
        binding.btnStartServer.isEnabled = false
        binding.btnStopServer.isEnabled = true
        binding.btnSelectFolder.isEnabled = false

        val ip = getLocalIpAddress()
        binding.tvAddress.text = "${getString(R.string.server_address_prefix)}$ip:8080"
        binding.tvIpInfo.text = "Accede desde tu PC o browser a:\nhttp://$ip:8080\n\nAsegúrate de estar en la misma red Wi-Fi."
    }

    private fun updateUiStopped() {
        binding.tvStatus.text = getString(R.string.status_idle)
        binding.tvStatus.setTextColor(ContextCompat.getColor(this, R.color.server_stopped))
        binding.btnStartServer.isEnabled = selectedFolderUri != null
        binding.btnStopServer.isEnabled = false
        binding.btnSelectFolder.isEnabled = true
        binding.tvAddress.text = ""
        binding.tvIpInfo.text = "Tu IP local aparecerá aquí cuando el servidor esté activo."
    }

    private fun getLocalIpAddress(): String {
        return try {
            val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
            val wifiInfo = wifiManager.connectionInfo
            val ipInt = wifiInfo.ipAddress
            if (ipInt == 0) {
                "0.0.0.0"
            } else {
                String.format(
                    "%d.%d.%d.%d",
                    ipInt and 0xff,
                    ipInt shr 8 and 0xff,
                    ipInt shr 16 and 0xff,
                    ipInt shr 24 and 0xff
                )
            }
        } catch (e: Exception) {
            "0.0.0.0"
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun requestIgnoreBatteryOptimizations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(android.os.PowerManager::class.java)
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                try {
                    startActivity(intent)
                } catch (e: Exception) {
                    // Dispositivo no soporta esta acción, se ignora
                }
            }
        }
    }
}
