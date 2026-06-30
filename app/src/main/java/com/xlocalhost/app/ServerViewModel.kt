package com.xlocalhost.app

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
import java.net.Inet6Address
import java.net.NetworkInterface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ServerConfig(
    val port: Int = 8080,
    val folderUri: Uri? = null,
    val folderDisplayPath: String = "",
    val fileAccessVariant: String = "File API", // "File API", "SAF", "Media store"
    val preferIpv6: Boolean = false,
    val renderFolderPages: Boolean = true,
    val allowModification: Boolean = false,
    val requireAuthorization: Boolean = false,
    val restrictNetworkInterfaces: Boolean = false,
    val whiteListClients: Boolean = false,
    val useTls: Boolean = false,
    val verifyHostHeader: Boolean = false,
    val requestRateLimit: Boolean = false,
    val autostartOnBoot: Boolean = false,
    val autoShutdownByInactivity: Boolean = false,
    val excludeFromBatteryOptimization: Boolean = false,
    val customResponseHeaders: Boolean = false,
    val customCharset: Boolean = false,
    val configureCors: Boolean = false,
    val corsAllowOrigin: String = "*",
    val corsAllowMethods: String = "GET,POST,PUT,DELETE,OPTIONS",
    val corsAllowHeaders: String = "Content-Type,Authorization",
    val enableSqlite: Boolean = false,
    val enableDbModifyApi: Boolean = false,
    val enableDbCustomSqlApi: Boolean = false,
    val serveWelcomeFile: Boolean = true,
)

data class ServerUiState(
    val isRunning: Boolean = false,
    val config: ServerConfig = ServerConfig(),
    val localIpV4: String = "0.0.0.0",
    val localIpV6: String = "",
    val availableInterfaces: List<String> = emptyList(),
    val selectedInterface: String = "wlan0",
    val logs: List<String> = emptyList(),
    val requestLogs: List<LogEntry> = emptyList(),
    val dbStatus: String = "Disconnected",
    val dbSize: String = "0.0 KB",
    val dbTables: Int = 0,
) {
    val displayedIp: String get() = if (config.preferIpv6 && localIpV6.isNotEmpty()) localIpV6 else localIpV4
    val serverUrl: String get() {
        val ip = displayedIp
        val wrapped = if (config.preferIpv6 && ip.contains(":")) "[$ip]" else ip
        return "http://$wrapped:${config.port}"
    }
}

class ServerViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ServerUiState())
    val uiState: StateFlow<ServerUiState> = _uiState.asStateFlow()

    init {
        refreshNetworkInfo()
        restorePersistedFolder()
    }

    fun updatePort(port: Int) {
        _uiState.update { it.copy(config = it.config.copy(port = port)) }
    }

    fun updateConfig(updater: (ServerConfig) -> ServerConfig) {
        _uiState.update { it.copy(config = updater(it.config)) }
    }

    fun updateServeWelcomeFile(serve: Boolean) {
        _uiState.update { it.copy(config = it.config.copy(serveWelcomeFile = serve)) }
    }

    fun setPreferIpv6(prefer: Boolean) {
        _uiState.update { it.copy(config = it.config.copy(preferIpv6 = prefer)) }
    }

    fun setSelectedInterface(iface: String) {
        _uiState.update { it.copy(selectedInterface = iface) }
        refreshNetworkInfo()
    }

    fun setFolderUri(uri: Uri, displayPath: String) {
        _uiState.update { it.copy(config = it.config.copy(folderUri = uri, folderDisplayPath = displayPath)) }
        addLog("Carpeta seleccionada: $displayPath")
    }

    fun startServer(context: android.content.Context) {
        val config = _uiState.value.config
        if (config.folderUri == null) {
            addLog("Error: select a folder first.")
            return
        }
        refreshNetworkInfo()

        // Persist autostart preference so BootReceiver can read it
        context.getSharedPreferences("xlocalhost_prefs", android.content.Context.MODE_PRIVATE)
            .edit()
            .putBoolean("autostart_on_boot", config.autostartOnBoot)
            .putBoolean("serve_welcome_file", config.serveWelcomeFile)
            .apply()

        val intent = Intent(context, ServerService::class.java).apply {
            action = ServerService.ACTION_START
            putExtra(ServerService.EXTRA_FOLDER_URI, config.folderUri.toString())
            putExtra(ServerService.EXTRA_PORT, config.port)
            putExtra(ServerService.EXTRA_ALLOW_MOD, config.allowModification)
            putExtra(ServerService.EXTRA_ENABLE_SQLITE, config.enableSqlite)
            putExtra(ServerService.EXTRA_DB_MODIFY, config.enableDbModifyApi)
            putExtra(ServerService.EXTRA_DB_CUSTOM_SQL, config.enableDbCustomSqlApi)
            putExtra(ServerService.EXTRA_SERVE_WELCOME_FILE, config.serveWelcomeFile)
            if (config.configureCors) {
                putExtra(ServerService.EXTRA_CORS_ORIGIN, config.corsAllowOrigin)
                putExtra(ServerService.EXTRA_CORS_METHODS, config.corsAllowMethods)
                putExtra(ServerService.EXTRA_CORS_HEADERS, config.corsAllowHeaders)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        _uiState.update { it.copy(isRunning = true) }
        addLog("Server started → ${_uiState.value.serverUrl}")
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
        val ts = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        _uiState.update { it.copy(logs = it.logs + "[$ts] $message") }
    }

    fun addRequestLog(log: LogEntry) {
        _uiState.update { it.copy(requestLogs = it.requestLogs + log) }
    }

    fun clearLogs() {
        _uiState.update { it.copy(logs = emptyList(), requestLogs = emptyList()) }
    }

    fun refreshNetworkInfo() {
        val interfaces = mutableListOf<String>()
        var ipv4 = "0.0.0.0"
        var ipv6 = ""
        val preferred = _uiState.value.selectedInterface

        try {
            val allIfaces = NetworkInterface.getNetworkInterfaces()?.toList() ?: emptyList()
            for (iface in allIfaces) {
                if (!iface.isLoopback && iface.isUp) {
                    interfaces.add(iface.name)
                }
            }
            val target = allIfaces.firstOrNull { it.name == preferred }
                ?: allIfaces.firstOrNull { it.name.startsWith("wlan") && !it.isLoopback && it.isUp }
                ?: allIfaces.firstOrNull { it.name.startsWith("eth") && !it.isLoopback && it.isUp }

            target?.inetAddresses?.toList()?.forEach { addr ->
                when {
                    addr is Inet4Address && !addr.isLoopbackAddress -> ipv4 = addr.hostAddress ?: ipv4
                    addr is Inet6Address && !addr.isLoopbackAddress -> {
                        val raw = addr.hostAddress ?: return@forEach
                        ipv6 = raw.substringBefore('%')
                    }
                }
            }
        } catch (_: Exception) {}

        val selectedIface = if (interfaces.contains(preferred)) preferred
        else interfaces.firstOrNull { it.startsWith("wlan") } ?: preferred

        _uiState.update {
            it.copy(
                localIpV4 = ipv4,
                localIpV6 = ipv6,
                availableInterfaces = interfaces,
                selectedInterface = selectedIface
            )
        }
    }

    private fun restorePersistedFolder() {
        val context = getApplication<Application>()
        val persisted = context.contentResolver.persistedUriPermissions
        if (persisted.isNotEmpty()) {
            val uri = persisted.last().uri
            val displayPath = uri.lastPathSegment ?: uri.toString()
            _uiState.update {
                it.copy(config = it.config.copy(folderUri = uri, folderDisplayPath = displayPath))
            }
        }
    }
}
