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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle

// ── Corporate Premium Palette ───────────────────────────────────────────────
private val BgMain      = Color(0xFF0D0F12)
private val BgCard      = Color(0xFF161B22)
private val BgSurface   = Color(0xFF21262D)
private val AccPrimary  = Color(0xFF2F81F7) // GitHub Blue
private val AccSuccess  = Color(0xFF3FB950) // GitHub Green
private val AccWarning  = Color(0xFFD29922)
private val AccError    = Color(0xFFF85149)
private val TextMain    = Color(0xFFE6EDF3)
private val TextDim     = Color(0xFF8B949E)
private val BorderColor = Color(0xFF30363D)

private val PremiumColorScheme = darkColorScheme(
    primary = AccPrimary,
    background = BgMain,
    surface = BgCard,
    onBackground = TextMain,
    onSurface = TextMain,
    outline = BorderColor
)

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
            MaterialTheme(colorScheme = PremiumColorScheme) {
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

@Composable
fun XLocalHostApp(viewModel: ServerViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showLogs by rememberSaveable { mutableStateOf(false) }

    AnimatedVisibility(visible = true) {
        if (showLogs) {
            LogsScreen(
                uiState = uiState,
                onBack = { showLogs = false },
                onClear = viewModel::clearLogs
            )
        } else {
            MainDashboard(
                uiState = uiState,
                viewModel = viewModel,
                onShowLogs = { showLogs = true }
            )
        }
    }
}

@Composable
fun MainDashboard(
    uiState: ServerUiState,
    viewModel: ServerViewModel,
    onShowLogs: () -> Unit
) {
    val context = LocalContext.current
    val config = uiState.config

    val folderPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, flags)
            } catch (e: Exception) {}
            viewModel.setFolderUri(uri, uri.lastPathSegment ?: uri.toString())
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        // --- Premium Header ---
        DashboardHeader(uiState.isRunning, onShowLogs)

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                StatusCard(uiState, onStart = { viewModel.startServer(context) }, onStop = { viewModel.stopServer(context) })
            }

            item {
                QuickActionsRow(uiState, viewModel, onPickFolder = { folderPickerLauncher.launch(null) })
            }

            item {
                SectionLabel("Network Configuration")
                NetworkCard(uiState, viewModel)
            }

            item {
                SectionLabel("Security & Access Control")
                SecurityCard(config, viewModel)
            }

            item {
                SectionLabel("Advanced Settings")
                AdvancedCard(config, viewModel, context)
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@Composable
fun DashboardHeader(isRunning: Boolean, onShowLogs: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "X-LOCALHOST",
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                color = TextMain
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isRunning) AccSuccess else TextDim)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (isRunning) "SERVER LIVE" else "SERVER STANDBY",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isRunning) AccSuccess else TextDim
                )
            }
        }
        
        IconButton(
            onClick = onShowLogs,
            modifier = Modifier
                .clip(CircleShape)
                .background(BgSurface)
        ) {
            Icon(Icons.Default.Terminal, contentDescription = "Logs", tint = AccPrimary)
        }
    }
}

@Composable
fun StatusCard(uiState: ServerUiState, onStart: () -> Unit, onStop: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = BgCard,
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Server Status", color = TextDim, fontSize = 12.sp)
                    Text(
                        if (uiState.isRunning) "Running" else "Stopped",
                        color = if (uiState.isRunning) AccSuccess else AccError,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Button(
                    onClick = if (uiState.isRunning) onStop else onStart,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (uiState.isRunning) AccError.copy(alpha = 0.1f) else AccSuccess.copy(alpha = 0.1f),
                        contentColor = if (uiState.isRunning) AccError else AccSuccess
                    ),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, if (uiState.isRunning) AccError else AccSuccess)
                ) {
                    Icon(if (uiState.isRunning) Icons.Default.Stop else Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (uiState.isRunning) "STOP" else "START", fontWeight = FontWeight.Black)
                }
            }
            
            if (uiState.isRunning) {
                Spacer(Modifier.height(16.dp))
                Divider(color = BorderColor)
                Spacer(Modifier.height(16.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    InfoMetric("Requests", "${uiState.requestLogs.size}", Icons.Default.SwapVert)
                    InfoMetric("Uptime", "100%", Icons.Default.Timer)
                    InfoMetric("Database", if (uiState.config.enableSqlite) "Active" else "Off", Icons.Default.Storage)
                }
            }
        }
    }
}

@Composable
fun InfoMetric(label: String, value: String, icon: ImageVector) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = AccPrimary, modifier = Modifier.size(18.dp))
        Text(value, color = TextMain, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(label, color = TextDim, fontSize = 10.sp)
    }
}

@Composable
fun QuickActionsRow(uiState: ServerUiState, viewModel: ServerViewModel, onPickFolder: () -> Unit) {
    var showVariants by remember { mutableStateOf(false) }
    
    if (showVariants) {
        FileAccessVariantsDialog(
            currentVariant = uiState.config.fileAccessVariant,
            onDismiss = { showVariants = false },
            onSelect = { 
                viewModel.updateConfig { it.copy(fileAccessVariant = it) }
                showVariants = false
            }
        )
    }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ActionTile(
            title = "Root Folder",
            subtitle = if (uiState.config.fileAccessVariant == "File API") "Internal" else uiState.config.folderDisplayPath.ifEmpty { "Select" },
            icon = Icons.Default.Folder,
            modifier = Modifier.weight(1f),
            onClick = { if (uiState.config.fileAccessVariant == "SAF") onPickFolder() else showVariants = true }
        )
        ActionTile(
            title = "Access",
            subtitle = uiState.config.fileAccessVariant,
            icon = Icons.Default.SettingsInputComponent,
            modifier = Modifier.weight(1f),
            onClick = { showVariants = true }
        )
    }
}

@Composable
fun ActionTile(title: String, subtitle: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        color = BgCard,
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = AccPrimary, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, color = TextDim, fontSize = 10.sp)
                Text(subtitle, color = TextMain, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun NetworkCard(uiState: ServerUiState, viewModel: ServerViewModel) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = BgCard,
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Local IP Address", color = TextDim, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Text(uiState.localIp, color = AccPrimary, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Default.ContentCopy, contentDescription = null, tint = AccPrimary, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.height(12.dp))
            Divider(color = BorderColor)
            Spacer(Modifier.height(12.dp))
            PremiumToggle("Prefer IPv6", uiState.config.preferIpv6) {
                viewModel.updateConfig { it.copy(preferIpv6 = !it.preferIpv6) }
            }
        }
    }
}

@Composable
fun SecurityCard(config: ServerConfig, viewModel: ServerViewModel) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = BgCard,
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            PremiumToggle("Require Authorization", config.requireAuthorization) {
                viewModel.updateConfig { it.copy(requireAuthorization = !it.requireAuthorization) }
            }
            PremiumToggle("Use TLS (HTTPS)", config.useTls) {
                viewModel.updateConfig { it.copy(useTls = !it.useTls) }
            }
            PremiumToggle("Rate Limiting", config.requestRateLimit) {
                viewModel.updateConfig { it.copy(requestRateLimit = !it.requestRateLimit) }
            }
        }
    }
}

@Composable
fun AdvancedCard(config: ServerConfig, viewModel: ServerViewModel, context: android.content.Context) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = BgCard,
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            PremiumToggle("Welcome Page", config.serveWelcomeFile) {
                viewModel.updateServeWelcomeFile(!config.serveWelcomeFile)
            }
            PremiumToggle("Auto-start on Boot", config.autostartOnBoot) {
                viewModel.updateConfig { it.copy(autostartOnBoot = !it.autostartOnBoot) }
            }
        }
    }
}

@Composable
fun PremiumToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TextMain, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = AccPrimary,
                uncheckedThumbColor = TextDim,
                uncheckedTrackColor = BgSurface
            )
        )
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        color = TextDim,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

// ── Re-using existing components with premium styling ────────────────────────
@Composable
fun LogsScreen(uiState: ServerUiState, onBack: () -> Unit, onClear: () -> Unit) {
    val logs = uiState.requestLogs
    val listState = rememberLazyListState()
    var selectedLog by remember { mutableStateOf<LogEntry?>(null) }

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) listState.animateScrollToItem(logs.size - 1)
    }

    Column(modifier = Modifier.fillMaxSize().background(BgMain)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = null, tint = TextMain)
            }
            Text("Network Logs", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextMain, modifier = Modifier.weight(1f))
            IconButton(onClick = onClear) {
                Icon(Icons.Default.Delete, contentDescription = null, tint = AccError)
            }
        }

        if (logs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Listening for requests...", color = TextDim, fontFamily = FontFamily.Monospace)
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(logs) { log ->
                    LogCard(log)
                }
            }
        }
    }
}

@Composable
fun LogCard(log: LogEntry) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = BgCard,
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = log.method,
                color = if (log.method == "GET") AccSuccess else AccWarning,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                modifier = Modifier.width(45.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(log.path, color = TextMain, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${log.clientIp} • ${log.durationMs}ms", color = TextDim, fontSize = 10.sp)
            }
            Text(
                text = "${log.statusCode}",
                color = if (log.statusCode < 400) AccSuccess else AccError,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
fun FileAccessVariantsDialog(currentVariant: String, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgCard,
        title = { Text("Storage Protocol", color = TextMain) },
        text = {
            Column {
                VariantOption("File API", "Direct system access (Recommended)", currentVariant == "File API") { onSelect("File API") }
                VariantOption("SAF", "Storage Access Framework (Picker)", currentVariant == "SAF") { onSelect("SAF") }
                VariantOption("Media", "Media Store (Gallery/Docs)", currentVariant == "Media store") { onSelect("Media store") }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("CLOSE", color = AccPrimary) }
        }
    )
}

@Composable
fun VariantOption(title: String, desc: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick, colors = RadioButtonDefaults.colors(selectedColor = AccPrimary))
        Column {
            Text(title, color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(desc, color = TextDim, fontSize = 11.sp)
        }
    }
}
