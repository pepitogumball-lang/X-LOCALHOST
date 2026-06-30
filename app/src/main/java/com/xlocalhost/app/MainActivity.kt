package com.xlocalhost.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle

// ── Palette ─────────────────────────────────────────────────────────────────
private val BgMain    = Color(0xFF1C1C1C)
private val BgCard    = Color(0xFF282828)
private val BgBtn     = Color(0xFF303030)
private val ColSection = Color(0xFFFFA040)
private val ColLink   = Color(0xFF64B5F6)
private val ColText   = Color(0xFFDDDDDD)
private val ColDim    = Color(0xFF888888)
private val ColActive = Color(0xFF4CAF50)
private val ColInact  = Color(0xFF888888)

private val AppColorScheme = darkColorScheme(
    primary         = ColLink,
    onPrimary       = Color(0xFF003062),
    secondary       = Color(0xFF89DDFF),
    background      = BgMain,
    surface         = BgMain,
    surfaceVariant  = BgCard,
    onBackground    = ColText,
    onSurface       = ColText,
    onSurfaceVariant= ColDim,
    error           = Color(0xFFFF7B72),
    outline         = Color(0xFF444444),
)

// ── Activity ─────────────────────────────────────────────────────────────────
class MainActivity : ComponentActivity() {

    private val viewModel: ServerViewModel by viewModels()

    private val logReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            if (intent?.action == "com.xlocalhost.app.REQUEST_LOG") {
                val log = LogEntry(
                    method = intent.getStringExtra("method") ?: "",
                    path = intent.getStringExtra("path") ?: "",
                    statusCode = intent.getIntExtra("status", 200),
                    statusDescription = intent.getStringExtra("desc") ?: "",
                    durationMs = intent.getLongExtra("duration", 0),
                    clientIp = intent.getStringExtra("ip") ?: "",
                    requestHeaders = emptyMap(),
                    responseHeaders = emptyMap(),
                    startedAt = intent.getLongExtra("started", 0),
                    endedAt = intent.getLongExtra("ended", 0)
                )
                viewModel.addRequestLog(log)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermission()

        val filter = android.content.IntentFilter("com.xlocalhost.app.REQUEST_LOG")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(logReceiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(logReceiver, filter)
        }

        setContent {
            MaterialTheme(colorScheme = AppColorScheme) {
                Surface(modifier = Modifier.fillMaxSize(), color = BgMain) {
                    XLocalHostApp(viewModel = viewModel)
                }
            }
        }
    }

    override fun onDestroy() {
        unregisterReceiver(logReceiver)
        super.onDestroy()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
            }
        }
    }
}

// ── Root composable ───────────────────────────────────────────────────────────
@Composable
fun XLocalHostApp(viewModel: ServerViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showLogs by rememberSaveable { mutableStateOf(false) }

    if (showLogs) {
        LogsScreen(
            uiState    = uiState,
            onBack     = { showLogs = false },
            onClear    = viewModel::clearLogs
        )
    } else {
        MainScreen(
            uiState     = uiState,
            viewModel   = viewModel,
            onShowLogs  = { showLogs = true }
        )
    }
}

// ── Main screen ───────────────────────────────────────────────────────────────
@Composable
fun MainScreen(
    uiState: ServerUiState,
    viewModel: ServerViewModel,
    onShowLogs: () -> Unit
) {
    val context = LocalContext.current
    val config  = uiState.config

    val folderPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, flags)
            viewModel.setFolderUri(uri, uri.lastPathSegment ?: uri.toString())
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .background(BgMain)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text       = "x-localhost",
                    fontSize   = 26.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color      = ColText
                )
                IconButton(onClick = onShowLogs) {
                    Icon(Icons.Default.Terminal, contentDescription = "Logs", tint = ColDim)
                }
            }
        }

        item { SectionHeader("General") }
        item {
            GeneralSection(
                uiState    = uiState,
                viewModel  = viewModel,
                onShowLogs = onShowLogs,
                onStart    = { viewModel.startServer(context) },
                onStop     = { viewModel.stopServer(context) }
            )
        }

        item { SectionHeader("Files") }
        item {
            FilesSection(
                config       = config,
                viewModel    = viewModel,
                isRunning    = uiState.isRunning,
                onPickFolder = { folderPickerLauncher.launch(null) }
            )
        }

        item { SectionHeader("Database") }
        item {
            DatabaseSection(uiState = uiState, viewModel = viewModel)
        }

        item { SectionHeader("Security") }
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                FlatCheckbox(config.requireAuthorization, "Require authorization") {
                    viewModel.updateConfig { it.copy(requireAuthorization = !it.requireAuthorization) }
                }
                FlatCheckbox(config.restrictNetworkInterfaces, "Restrict network interfaces") {
                    viewModel.updateConfig { it.copy(restrictNetworkInterfaces = !it.restrictNetworkInterfaces) }
                }
                FlatCheckbox(config.whiteListClients, "White list of clients (IPs)") {
                    viewModel.updateConfig { it.copy(whiteListClients = !it.whiteListClients) }
                }
                FlatCheckbox(config.useTls, "Use TLS encryption (HTTPS protocol)") {
                    viewModel.updateConfig { it.copy(useTls = !it.useTls) }
                }
                FlatCheckbox(config.verifyHostHeader, "Verify host http header") {
                    viewModel.updateConfig { it.copy(verifyHostHeader = !it.verifyHostHeader) }
                }
                FlatCheckbox(config.requestRateLimit, "Request rate limit") {
                    viewModel.updateConfig { it.copy(requestRateLimit = !it.requestRateLimit) }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        item { SectionHeader("Misc") }
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                FlatCheckbox(config.autostartOnBoot, "Autostart on boot") {
                    viewModel.updateConfig { it.copy(autostartOnBoot = !it.autostartOnBoot) }
                }
                FlatCheckbox(config.autoShutdownByInactivity, "Auto shutdown by inactivity (15 min)") {
                    viewModel.updateConfig { it.copy(autoShutdownByInactivity = !it.autoShutdownByInactivity) }
                }
                FlatCheckbox(config.excludeFromBatteryOptimization, "Exclude from battery optimization") {
                    viewModel.updateConfig { it.copy(excludeFromBatteryOptimization = !it.excludeFromBatteryOptimization) }
                    if (!config.excludeFromBatteryOptimization && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        try { context.startActivity(intent) } catch (_: Exception) {}
                    }
                }
                FlatCheckbox(config.customResponseHeaders, "Custom response headers") {
                    viewModel.updateConfig { it.copy(customResponseHeaders = !it.customResponseHeaders) }
                }
                FlatCheckbox(config.customCharset, "Custom charset") {
                    viewModel.updateConfig { it.copy(customCharset = !it.customCharset) }
                }
                FlatCheckbox(config.configureCors, "Configure CORS") {
                    viewModel.updateConfig { it.copy(configureCors = !it.configureCors) }
                }
                if (config.configureCors) {
                    Column(modifier = Modifier.padding(start = 32.dp, end = 16.dp)) {
                        CorsField("Allow Origin", config.corsAllowOrigin) { 
                            viewModel.updateConfig { it.copy(corsAllowOrigin = it) }
                        }
                        CorsField("Allow Methods", config.corsAllowMethods) { 
                            viewModel.updateConfig { it.copy(corsAllowMethods = it) }
                        }
                        CorsField("Allow Headers", config.corsAllowHeaders) { 
                            viewModel.updateConfig { it.copy(corsAllowHeaders = it) }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun CorsField(label: String, value: String, onValueChange: (String) -> Unit) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(label, color = ColDim, fontSize = 11.sp)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = ColText),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ColLink,
                unfocusedBorderColor = Color(0xFF444444),
                cursorColor = ColLink
            ),
            singleLine = true
        )
    }
}

// ── General section ───────────────────────────────────────────────────────────
@Composable
fun GeneralSection(
    uiState: ServerUiState,
    viewModel: ServerViewModel,
    onShowLogs: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    val config    = uiState.config
    val clipboard = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current
    var editingPort by rememberSaveable { mutableStateOf(false) }
    var portText    by rememberSaveable { mutableStateOf(config.port.toString()) }
    var ifaceMenuOpen by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(text = "Status: " + if (uiState.isRunning) "running ⚡" else "stopped", 
             color = if (uiState.isRunning) ColActive else ColInact, 
             fontWeight = FontWeight.Bold)
        
        Spacer(Modifier.height(8.dp))
        Text("IP info:", color = ColDim, fontSize = 12.sp)
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("- Interface: ", color = ColText, fontSize = 14.sp)
            Box {
                Row(modifier = Modifier.clickable { ifaceMenuOpen = true }, verticalAlignment = Alignment.CenterVertically) {
                    Text(uiState.selectedInterface, color = ColLink, fontSize = 14.sp)
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = ColLink)
                }
                DropdownMenu(expanded = ifaceMenuOpen, onDismissRequest = { ifaceMenuOpen = false }) {
                    uiState.availableInterfaces.forEach { iface ->
                        DropdownMenuItem(text = { Text(iface) }, onClick = { 
                            viewModel.setSelectedInterface(iface)
                            ifaceMenuOpen = false 
                        })
                    }
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("- IP version: ", color = ColText, fontSize = 14.sp)
            IpVersionToggle(config.preferIpv6) { viewModel.setPreferIpv6(it) }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("- IP address: ", color = ColText, fontSize = 14.sp)
            Text(uiState.displayedIp, color = ColText, fontSize = 14.sp)
            IconButton(onClick = { clipboard.setText(AnnotatedString(uiState.displayedIp)) }, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, tint = ColDim, modifier = Modifier.size(16.dp))
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Port: ", color = ColText, fontSize = 14.sp)
            if (editingPort) {
                OutlinedTextField(
                    value = portText,
                    onValueChange = { portText = it },
                    modifier = Modifier.width(100.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = ColText, unfocusedTextColor = ColText)
                )
                IconButton(onClick = { 
                    portText.toIntOrNull()?.let { viewModel.updatePort(it) }
                    editingPort = false 
                }) { Icon(Icons.Default.PlayArrow, contentDescription = null, tint = ColActive) }
            } else {
                Text(config.port.toString(), color = ColText, fontSize = 14.sp)
                IconButton(onClick = { editingPort = true }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = null, tint = ColDim, modifier = Modifier.size(16.dp))
                }
            }
        }

        Text(
            text = "URL: ${uiState.serverUrl}",
            color = ColLink,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            textDecoration = TextDecoration.Underline,
            modifier = Modifier.clickable { uriHandler.openUri(uiState.serverUrl) }
        )

        Spacer(Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = if (uiState.isRunning) onStop else onStart,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = if (uiState.isRunning) Color(0xFF444444) else BgBtn),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(if (uiState.isRunning) "STOP" else "START", color = ColText)
            }
            Button(
                onClick = onShowLogs,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = BgBtn),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text("LOGS", color = ColText)
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

// ── Database section ──────────────────────────────────────────────────────────
@Composable
fun DatabaseSection(uiState: ServerUiState, viewModel: ServerViewModel) {
    val config = uiState.config
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        FlatCheckbox(config.enableSqlite, "Enable SQLite database") {
            viewModel.updateConfig { it.copy(enableSqlite = !it.enableSqlite) }
        }
        if (config.enableSqlite) {
            Column(modifier = Modifier.padding(start = 32.dp)) {
                Text(
                    text = uiState.dbStatus,
                    color = if (uiState.dbStatus == "Connected") ColActive else ColInact,
                    fontSize = 12.sp
                )
                Text("Size: ${uiState.dbSize}", color = ColDim, fontSize = 12.sp)
                Text("Tables: ${uiState.dbTables}", color = ColDim, fontSize = 12.sp)
                
                Spacer(Modifier.height(8.dp))
                FlatCheckbox(config.enableDbModifyApi, "Enable API to modify tables data") {
                    viewModel.updateConfig { it.copy(enableDbModifyApi = !it.enableDbModifyApi) }
                }
                FlatCheckbox(config.enableDbCustomSqlApi, "Enable API to call custom SQL") {
                    viewModel.updateConfig { it.copy(enableDbCustomSqlApi = !it.enableDbCustomSqlApi) }
                }
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { /* TODO */ },
                        colors = ButtonDefaults.buttonColors(containerColor = BgBtn),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("DELETE DATABASE", fontSize = 10.sp, color = Color(0xFFFF7B72))
                    }
                    Button(
                        onClick = { /* TODO */ },
                        colors = ButtonDefaults.buttonColors(containerColor = BgBtn),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("EXPORT DATABASE", fontSize = 10.sp, color = ColText)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

// ── Files section ─────────────────────────────────────────────────────────────
@Composable
fun FilesSection(
    config: ServerConfig,
    viewModel: ServerViewModel,
    isRunning: Boolean,
    onPickFolder: () -> Unit
) {
    var showVariantsDialog by remember { mutableStateOf(false) }

    if (showVariantsDialog) {
        FileAccessVariantsDialog(
            currentVariant = config.fileAccessVariant,
            onDismiss = { showVariantsDialog = false },
            onSelect = { variant ->
                viewModel.updateConfig { it.copy(fileAccessVariant = variant) }
                showVariantsDialog = false
                if (variant == "SAF") onPickFolder()
            }
        )
    }

    Column {
        Text(text = "Root folder:", color = ColDim, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .background(BgCard, RoundedCornerShape(4.dp))
                .clickable(enabled = !isRunning) { showVariantsDialog = true }
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Folder, contentDescription = null, tint = ColLink, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (config.fileAccessVariant == "File API") "Application's private folder" else config.folderDisplayPath.ifEmpty { "Not selected" },
                color = ColText,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { showVariantsDialog = true }, enabled = !isRunning) {
                Icon(Icons.Default.MoreVert, contentDescription = null, tint = ColDim)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            FlatCheckbox(config.renderFolderPages, "Render folder content pages") {
                viewModel.updateConfig { it.copy(renderFolderPages = !it.renderFolderPages) }
            }
            FlatCheckbox(config.allowModification, "Allow file modification") {
                viewModel.updateConfig { it.copy(allowModification = !it.allowModification) }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
fun FileAccessVariantsDialog(
    currentVariant: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = BgCard,
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("File Access Variants", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = ColText)
                Spacer(Modifier.height(16.dp))
                
                VariantItem("File API (default, recommended for most cases)", 
                            "Direct file access, may require granting additional permissions on newer Android versions.",
                            currentVariant == "File API") { onSelect("File API") }
                
                VariantItem("Storage Access Framework (SAF)", 
                            "Access files using a standard Android file picker. Limited and sometimes slower access to files.",
                            currentVariant == "SAF") { onSelect("SAF") }
                
                VariantItem("Media store", 
                            "Access downloads, documents, photos, videos, and audio files through the system's media library.",
                            currentVariant == "Media store") { onSelect("Media store") }
            }
        }
    }
}

@Composable
fun VariantItem(title: String, description: String, isSelected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 8.dp)
    ) {
        Text(title, color = if (isSelected) ColLink else ColText, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text(description, color = ColDim, fontSize = 11.sp)
    }
}

// ── IP version toggle ─────────────────────────────────────────────────────────
@Composable
fun IpVersionToggle(preferIpv6: Boolean, onToggle: (Boolean) -> Unit) {
    Row {
        listOf(false to "IPV4", true to "IPV6").forEachIndexed { index, (isV6, label) ->
            val selected = preferIpv6 == isV6
            val shape = when (index) {
                0    -> RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp)
                else -> RoundedCornerShape(topEnd   = 4.dp, bottomEnd   = 4.dp)
            }
            Box(
                modifier = Modifier
                    .background(color = if (selected) Color(0xFF3A3A3A) else Color(0xFF222222), shape = shape)
                    .clickable { onToggle(isV6) }
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(label, color = if (selected) ColText else ColDim, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = "- $title",
        color = ColSection,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp)
    )
}

@Composable
fun FlatCheckbox(checked: Boolean, label: String, enabled: Boolean = true, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled) { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors = CheckboxDefaults.colors(checkedColor = ColLink, uncheckedColor = ColDim, checkmarkColor = BgMain)
        )
        Text(label, color = if (enabled) ColText else ColDim, fontSize = 14.sp)
    }
}

// ── Logs screen ───────────────────────────────────────────────────────────────
@Composable
fun LogsScreen(
    uiState: ServerUiState,
    onBack: () -> Unit,
    onClear: () -> Unit
) {
    val logs = uiState.requestLogs
    val listState = rememberLazyListState()
    var selectedLog by remember { mutableStateOf<LogEntry?>(null) }

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) listState.animateScrollToItem(logs.size - 1)
    }

    Column(modifier = Modifier.fillMaxSize().background(BgMain)) {
        // Toolbar
        Row(
            modifier = Modifier.fillMaxWidth().background(BgCard).padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Volver", tint = ColText)
            }
            Text(
                text       = "Filter",
                fontSize   = 18.sp,
                fontWeight = FontWeight.Bold,
                color      = ColText,
                modifier   = Modifier.weight(1f)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("↓0 B/s ↑0 B/s", color = ColDim, fontSize = 10.sp)
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = {}) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Menu", tint = ColText)
                }
            }
        }

        if (logs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No requests yet", color = ColDim, fontFamily = FontFamily.Monospace)
            }
        } else {
            Row(modifier = Modifier.fillMaxSize()) {
                // Left Panel: List
                LazyColumn(
                    state    = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(logs) { entry ->
                        LogItem(
                            entry      = entry,
                            isSelected = selectedLog?.id == entry.id,
                            onClick    = { selectedLog = entry }
                        )
                    }
                }

                // Right Panel: Details
                if (selectedLog != null) {
                    Box(modifier = Modifier.weight(1.2f).fillMaxSize().background(BgCard).padding(8.dp)) {
                        LogDetailView(selectedLog!!)
                    }
                }
            }
        }
    }
}

@Composable
fun LogItem(entry: LogEntry, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isSelected) BgBtn else Color.Transparent)
            .clickable { onClick() }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = entry.method,
            color = if (entry.method == "GET") Color(0xFF4CAF50) else Color(0xFFFFA040),
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            modifier = Modifier.width(40.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(entry.path, color = ColText, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(entry.timeFormatted, color = ColDim, fontSize = 10.sp)
        }
        Text(
            text = "${entry.statusCode} OK",
            color = if (entry.statusCode < 400) Color(0xFF4CAF50) else Color(0xFFFF7B72),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun LogDetailView(log: LogEntry) {
    val detailText = """
        --- Timing ---
        Started: ${log.fullStartedAt} (${log.startedAt} ms)
        Ended: ${log.fullEndedAt} (${log.endedAt} ms)
        Duration: ${log.durationMs} ms
        
        --- Client ---
        Host: ${log.clientIp}
        Connection id: ${log.id.substring(0, 8)}
        
        --- Request ---
        ${log.method} ${log.path}
        ${log.requestHeaders.entries.joinToString("\n") { "${it.key}: ${it.value}" }}
        
        --- Response ---
        ${log.statusCode} ${log.statusDescription}
        ${log.responseHeaders.entries.joinToString("\n") { "${it.key}: ${it.value}" }}
    """.trimIndent()

    androidx.compose.foundation.text.selection.SelectionContainer {
        Text(
            text = detailText,
            color = ColText,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            modifier = Modifier.fillMaxSize()
        )
    }
}
