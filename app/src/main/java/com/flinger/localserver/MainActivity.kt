package com.flinger.localserver

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermission()
        setContent {
            MaterialTheme(colorScheme = AppColorScheme) {
                Surface(modifier = Modifier.fillMaxSize(), color = BgMain) {
                    XLocalHostApp(viewModel = viewModel)
                }
            }
        }
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
            logs       = uiState.logs,
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
        // ── App title bar ────────────────────────────────────────────────────
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

        // ── GENERAL ──────────────────────────────────────────────────────────
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

        // ── FILES ─────────────────────────────────────────────────────────────
        item { SectionHeader("Files") }
        item {
            FilesSection(
                config       = config,
                viewModel    = viewModel,
                isRunning    = uiState.isRunning,
                onPickFolder = { folderPickerLauncher.launch(null) }
            )
        }

        // ── DATABASE ──────────────────────────────────────────────────────────
        item { SectionHeader("Database") }
        item {
            FlatCheckbox(
                checked       = false,
                label         = "Enable SQLite database",
                enabled       = false,
                onCheckedChange = {}
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        // ── SECURITY ──────────────────────────────────────────────────────────
        item { SectionHeader("Security") }
        item {
            Column {
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

        // ── MISC ──────────────────────────────────────────────────────────────
        item { SectionHeader("Misc") }
        item {
            Column {
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
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
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

        // Status
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Status:  ", color = ColText, fontSize = 15.sp)
            Text(
                text      = if (uiState.isRunning) "running" else "not running 💤",
                color     = if (uiState.isRunning) ColActive else ColInact,
                fontSize  = 15.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(Modifier.height(10.dp))

        // IP Info header
        Text("IP info:", color = ColText, fontSize = 15.sp)

        // Interface row
        Row(
            modifier = Modifier.padding(start = 8.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("  - Interface: ", color = ColText, fontSize = 14.sp)
            Box {
                Row(
                    modifier = Modifier
                        .clickable { ifaceMenuOpen = true }
                        .background(BgCard, RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(uiState.selectedInterface, color = ColLink, fontSize = 14.sp)
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = ColDim, modifier = Modifier.size(18.dp))
                }
                DropdownMenu(
                    expanded         = ifaceMenuOpen,
                    onDismissRequest = { ifaceMenuOpen = false }
                ) {
                    val ifaces = if (uiState.availableInterfaces.isEmpty())
                        listOf(uiState.selectedInterface) else uiState.availableInterfaces
                    for (iface in ifaces) {
                        DropdownMenuItem(
                            text    = { Text(iface, color = ColText) },
                            onClick = {
                                viewModel.setSelectedInterface(iface)
                                ifaceMenuOpen = false
                            }
                        )
                    }
                }
            }
        }

        // IP version toggle
        Row(
            modifier = Modifier.padding(start = 8.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("  - IP version: ", color = ColText, fontSize = 14.sp)
            IpVersionToggle(
                preferIpv6 = config.preferIpv6,
                onToggle   = viewModel::setPreferIpv6
            )
        }

        // IP address
        Row(
            modifier = Modifier.padding(start = 8.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("  - IP address: ", color = ColText, fontSize = 14.sp)
            val displayedIp = uiState.displayedIp
            Text(
                text     = displayedIp,
                color    = ColLink,
                fontSize = 14.sp,
                modifier = Modifier.clickable { clipboard.setText(AnnotatedString(displayedIp)) }
            )
            Spacer(Modifier.width(6.dp))
            Icon(
                imageVector    = Icons.Default.ContentCopy,
                contentDescription = "Copiar IP",
                tint           = ColDim,
                modifier       = Modifier
                    .size(16.dp)
                    .clickable { clipboard.setText(AnnotatedString(displayedIp)) }
            )
        }

        Spacer(Modifier.height(8.dp))

        // Port row
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Port:  ", color = ColText, fontSize = 15.sp)
            if (editingPort) {
                OutlinedTextField(
                    value         = portText,
                    onValueChange = { v ->
                        portText = v
                        v.toIntOrNull()?.takeIf { it in 1024..65535 }?.let { viewModel.updatePort(it) }
                    },
                    singleLine    = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier      = Modifier.width(100.dp).height(48.dp),
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = ColLink,
                        unfocusedBorderColor = Color(0xFF444444),
                        focusedTextColor     = ColText,
                        unfocusedTextColor   = ColText,
                    )
                )
                Spacer(Modifier.width(6.dp))
                Button(
                    onClick  = { editingPort = false },
                    modifier = Modifier.height(36.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = BgBtn)
                ) { Text("OK", color = ColText, fontSize = 13.sp) }
            } else {
                Text(
                    text      = config.port.toString(),
                    color     = ColLink,
                    fontSize  = 15.sp,
                    textDecoration = TextDecoration.Underline,
                    modifier  = Modifier.clickable { if (!uiState.isRunning) editingPort = true }
                )
                Spacer(Modifier.width(6.dp))
                Icon(
                    Icons.Default.Edit, contentDescription = "Editar puerto",
                    tint = ColDim, modifier = Modifier.size(16.dp).clickable { if (!uiState.isRunning) editingPort = true }
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        // URL row
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("URL:  ", color = ColText, fontSize = 15.sp)
            Text(
                text      = uiState.serverUrl,
                color     = ColLink,
                fontSize  = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines  = 1,
                overflow  = TextOverflow.Ellipsis,
                modifier  = Modifier.clickable { if (uiState.isRunning) uriHandler.openUri(uiState.serverUrl) }
            )
        }

        Spacer(Modifier.height(12.dp))

        // START / LOGS buttons
        Row(
            modifier  = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Button(
                onClick  = if (uiState.isRunning) onStop else onStart,
                enabled  = if (uiState.isRunning) true else config.folderUri != null,
                modifier = Modifier.weight(1f).height(46.dp),
                shape    = RoundedCornerShape(6.dp),
                colors   = ButtonDefaults.buttonColors(
                    containerColor         = BgBtn,
                    contentColor           = ColText,
                    disabledContainerColor = Color(0xFF252525),
                    disabledContentColor   = Color(0xFF555555),
                )
            ) {
                Icon(
                    imageVector     = if (uiState.isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier        = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    if (uiState.isRunning) "STOP" else "START",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize   = 14.sp
                )
            }
            Button(
                onClick  = onShowLogs,
                modifier = Modifier.weight(1f).height(46.dp),
                shape    = RoundedCornerShape(6.dp),
                colors   = ButtonDefaults.buttonColors(
                    containerColor = BgBtn,
                    contentColor   = ColText
                )
            ) {
                Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("LOGS", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }

        Spacer(Modifier.height(12.dp))
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
    var folderMenuOpen by remember { mutableStateOf(false) }

    Column {
        // Root folder card
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .background(BgCard, RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Root folder:", color = ColText, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(
                    text     = config.folderDisplayPath.ifEmpty { "Ninguna carpeta seleccionada" },
                    color    = if (config.folderDisplayPath.isEmpty()) ColDim else ColLink,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row {
                IconButton(onClick = { if (!isRunning) onPickFolder() }) {
                    Icon(Icons.Default.Folder, contentDescription = "Seleccionar", tint = ColDim)
                }
                Box {
                    IconButton(onClick = { folderMenuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Más opciones", tint = ColDim)
                    }
                    DropdownMenu(
                        expanded         = folderMenuOpen,
                        onDismissRequest = { folderMenuOpen = false }
                    ) {
                        DropdownMenuItem(
                            text    = { Text("Cambiar carpeta", color = ColText) },
                            onClick = { folderMenuOpen = false; if (!isRunning) onPickFolder() }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        FlatCheckbox(config.renderFolderPages, "Render folder content pages") {
            viewModel.updateConfig { it.copy(renderFolderPages = !it.renderFolderPages) }
        }
        FlatCheckbox(config.allowModification, "Allow file modification") {
            viewModel.updateConfig { it.copy(allowModification = !it.allowModification) }
        }
        Spacer(Modifier.height(8.dp))
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
                    .background(
                        color = if (selected) Color(0xFF3A3A3A) else Color(0xFF222222),
                        shape = shape
                    )
                    .clickable { onToggle(isV6) }
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text      = label,
                    color     = if (selected) ColText else ColDim,
                    fontSize  = 12.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

// ── Reusable flat checkbox row ────────────────────────────────────────────────
@Composable
fun FlatCheckbox(
    checked: Boolean,
    label: String,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked         = checked,
            onCheckedChange = onCheckedChange,
            enabled         = enabled,
            colors          = CheckboxDefaults.colors(
                checkedColor   = ColLink,
                uncheckedColor = ColDim,
                disabledCheckedColor   = Color(0xFF555555),
                disabledUncheckedColor = Color(0xFF444444),
            )
        )
        Text(
            text     = label,
            color    = if (enabled) ColText else ColDim,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

// ── Section header ────────────────────────────────────────────────────────────
@Composable
fun SectionHeader(title: String) {
    Text(
        text     = "- $title",
        color    = ColSection,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp)
    )
}

// ── Logs screen ───────────────────────────────────────────────────────────────
@Composable
fun LogsScreen(
    logs: List<String>,
    onBack: () -> Unit,
    onClear: () -> Unit
) {
    val listState = rememberLazyListState()
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) listState.animateScrollToItem(logs.size - 1)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .background(BgMain)
    ) {
        // Custom top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(BgCard)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Volver", tint = ColText)
            }
            Text(
                text       = "Logs",
                color      = ColText,
                fontSize   = 18.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier   = Modifier.weight(1f)
            )
            IconButton(onClick = onClear) {
                Icon(Icons.Default.Delete, contentDescription = "Limpiar", tint = Color(0xFFFF7B72))
            }
        }

        if (logs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Sin logs aún.", color = ColDim, fontFamily = FontFamily.Monospace)
            }
        } else {
            LazyColumn(
                state    = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(logs) { entry ->
                    Text(
                        text       = entry,
                        fontFamily = FontFamily.Monospace,
                        fontSize   = 12.sp,
                        color      = when {
                            entry.contains("Error", ignoreCase = true) -> Color(0xFFFF7B72)
                            entry.contains("iniciado")                 -> Color(0xFF4CAF50)
                            entry.contains("detenido")                 -> Color(0xFFFF9800)
                            else                                       -> ColText
                        }
                    )
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}
