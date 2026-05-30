package com.flinger.localserver

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.net.Inet4Address
import java.net.NetworkInterface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ServerConfig(
    val port: Int = 8080,
    val folderUri: Uri? = null,
    val folderDisplayPath: String = "",
    val renderFolderPages: Boolean = true,
    val allowModification: Boolean = false,
    val requireAuthorization: Boolean = false,
    val useTls: Boolean = false,
    val autoShutdownByInactivity: Boolean = false,
    val excludeFromBatteryOptimization: Boolean = false,
)

data class ServerUiState(
    val isRunning: Boolean = false,
    val config: ServerConfig = ServerConfig(),
    val localIpAddress: String = "0.0.0.0",
    val logs: List<String> = emptyList(),
)

class ServerViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ServerUiState())
    val uiState: StateFlow<ServerUiState> = _uiState.asStateFlow()

    init {
        refreshIpAddress()
        restorePersistedFolder()
    }

    fun updatePort(port: Int) {
        _uiState.update { it.copy(config = it.config.copy(port = port)) }
    }

    fun updateConfig(updater: (ServerConfig) -> ServerConfig) {
        _uiState.update { it.copy(config = updater(it.config)) }
    }

    fun setFolderUri(uri: Uri, displayPath: String) {
        _uiState.update { it.copy(config = it.config.copy(folderUri = uri, folderDisplayPath = displayPath)) }
        addLog("Carpeta seleccionada: $displayPath")
    }

    fun startServer(context: android.content.Context) {
        val config = _uiState.value.config
        if (config.folderUri == null) {
            addLog("Error: selecciona una carpeta primero.")
            return
        }
        refreshIpAddress()
        val intent = Intent(context, ServerService::class.java).apply {
            action = ServerService.ACTION_START
            putExtra(ServerService.EXTRA_FOLDER_URI, config.folderUri.toString())
            putExtra(ServerService.EXTRA_PORT, config.port)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        _uiState.update { it.copy(isRunning = true) }
        addLog("Servidor iniciado → http://${_uiState.value.localIpAddress}:${config.port}")
    }

    fun stopServer(context: android.content.Context) {
        val intent = Intent(context, ServerService::class.java).apply {
            action = ServerService.ACTION_STOP
        }
        context.startService(intent)
        _uiState.update { it.copy(isRunning = false) }
        addLog("Servidor detenido.")
    }

    fun addLog(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        _uiState.update { it.copy(logs = it.logs + "[$timestamp] $message") }
    }

    fun clearLogs() {
        _uiState.update { it.copy(logs = emptyList()) }
    }

    fun refreshIpAddress() {
        val ip = resolveLocalIpAddress()
        _uiState.update { it.copy(localIpAddress = ip) }
    }

    private fun restorePersistedFolder() {
        val context = getApplication<Application>()
        val persistedUris = context.contentResolver.persistedUriPermissions
        if (persistedUris.isNotEmpty()) {
            val uriPermission = persistedUris.last()
            val uri = uriPermission.uri
            val displayPath = uri.lastPathSegment ?: uri.toString()
            _uiState.update {
                it.copy(config = it.config.copy(folderUri = uri, folderDisplayPath = displayPath))
            }
        }
    }

    private fun resolveLocalIpAddress(): String {
        return try {
            NetworkInterface.getNetworkInterfaces()
                ?.toList()
                ?.filter { iface -> !iface.isLoopback && iface.isUp }
                ?.sortedBy { iface ->
                    when {
                        iface.name.startsWith("wlan") -> 0
                        iface.name.startsWith("eth") -> 1
                        else -> 2
                    }
                }
                ?.flatMap { iface -> iface.inetAddresses.toList() }
                ?.firstOrNull { addr -> !addr.isLoopbackAddress && addr is Inet4Address }
                ?.hostAddress
                ?: "0.0.0.0"
        } catch (e: Exception) {
            "0.0.0.0"
        }
    }
}
