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
import androidx.compose.animation.*
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle

// ── Corporate Elite Palette (Inspirada en la imagen de referencia) ──────────
private val BgDeep      = Color(0xFF090B0D)
private val BgPanel     = Color(0xFF121417)
private val BgSurface   = Color(0xFF1A1D21)
private val AccNeon     = Color(0xFF00D4FF) // Cyan brillante
private val AccSuccess  = Color(0xFF00FF85) // Verde neón
private val AccError    = Color(0xFFFF3B30)
private val TextPrimary = Color(0xFFF0F2F5)
private val TextSec     = Color(0xFF9BA3AF)
private val BorderElite = Color(0xFF2D333B)

private val EliteColorScheme = darkColorScheme(
    primary = AccNeon,
    background = BgDeep,
    surface = BgPanel,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    outline = BorderElite
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
            MaterialTheme(colorScheme = EliteColorScheme) {
                Surface(modifier = Modifier.fillMaxSize(), color = BgDeep) {
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

    Crossfade(targetState = showLogs, animationSpec = tween(500)) { logsVisible ->
        if (logsVisible) {
            LogsScreen(
                uiState = uiState,
                onBack = { showLogs = false },
                onClear = viewModel::clearLogs
            )
        } else {
            EliteDashboard(
                uiState = uiState,
                viewModel = viewModel,
                onShowLogs = { showLogs = true }
            )
        }
    }
}

@Composable
fun EliteDashboard(
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
            .background(BgDeep)
    ) {
        // --- Header (Inspirado en la imagen) ---
        HeaderSection(uiState.isRunning, onShowLogs)

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                ServerStatusCard(uiState, onStart = { viewModel.startServer(context) }, onStop = { viewModel.stopServer(context) })
            }

            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetricCard("MEMORY USAGE", "45%", 0.45f, AccNeon, Modifier.weight(1f))
                    MetricCard("STORAGE", "62%", 0.62f, AccSuccess, Modifier.weight(1f))
                }
            }

            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatsBox("REQUESTS", "${uiState.requestLogs.size}", Icons.Default.TrendingUp, Modifier.weight(1f))
                    StatsBox("DATABASE", if (config.enableSqlite) "ACTIVE" else "OFF", Icons.Default.Storage, Modifier.weight(1f))
                }
            }

            item {
                SectionLabel("Directory Control")
                DirectoryPanel(config, onPickFolder = { folderPickerLauncher.launch(null) }, onShowVariants = { /* TODO */ })
            }

            item {
                SectionLabel("Elite Configurations")
                EliteSettingsPanel(config, viewModel)
            }

            item { Spacer(Modifier.height(40.dp)) }
        }
    }
}

@Composable
fun HeaderSection(isRunning: Boolean, onShowLogs: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 32.dp, start = 24.dp, end = 24.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "X-LOCALHOST",
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.SansSerif,
                letterSpacing = 2.sp,
                color = TextPrimary
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                val infiniteTransition = rememberInfiniteTransition()
                val alpha by infiniteTransition.animateFloat(
                    initialValue = 0.4f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Reverse)
                )
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isRunning) AccSuccess.copy(alpha = alpha) else TextSec)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (isRunning) "SERVER LIVE" else "STANDBY",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    color = if (isRunning) AccSuccess else TextSec
                )
            }
        }
        
        IconButton(
            onClick = onShowLogs,
            modifier = Modifier
                .size(48.dp)
                .border(1.dp, BorderElite, CircleShape)
                .background(BgPanel, CircleShape)
        ) {
            Icon(Icons.Default.Terminal, contentDescription = null, tint = AccNeon, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun ServerStatusCard(uiState: ServerUiState, onStart: () -> Unit, onStop: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = BgPanel,
        border = BorderStroke(1.dp, BorderElite)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("LOCAL IP ADDRESS", color = TextSec, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(uiState.displayedIp, color = AccNeon, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
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
                Spacer(Modifier.height(20.dp))
                Divider(color = BorderElite, thickness = 0.5.dp)
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    SmallMetric("STATUS", "Running", AccSuccess)
                    SmallMetric("UPTIME", "100%", AccNeon)
                    SmallMetric("PORT", "8080", TextPrimary)
                }
            }
        }
    }
}

@Composable
fun MetricCard(label: String, value: String, progress: Float, color: Color, modifier: Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = BgPanel,
        border = BorderStroke(1.dp, BorderElite)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(label, color = TextSec, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(value, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                color = color,
                trackColor = BorderElite
            )
        }
    }
}

@Composable
fun StatsBox(label: String, value: String, icon: ImageVector, modifier: Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = BgPanel,
        border = BorderStroke(1.dp, BorderElite)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = AccNeon, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(label, color = TextSec, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Text(value, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

@Composable
fun DirectoryPanel(config: ServerConfig, onPickFolder: () -> Unit, onShowVariants: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { if (config.fileAccessVariant == "SAF") onPickFolder() else onShowVariants() },
        shape = RoundedCornerShape(16.dp),
        color = BgPanel,
        border = BorderStroke(1.dp, BorderElite)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.FolderOpen, contentDescription = null, tint = AccNeon)
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("ROOT FOLDER", color = TextSec, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = if (config.fileAccessVariant == "File API") "System Private" else config.folderDisplayPath.ifEmpty { "Select Path" },
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextSec)
        }
    }
}

@Composable
fun EliteSettingsPanel(config: ServerConfig, viewModel: ServerViewModel) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = BgPanel,
        border = BorderStroke(1.dp, BorderElite)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            EliteToggle("HTTPS Encryption", config.useTls) { viewModel.updateConfig { it.copy(useTls = !it.useTls) } }
            EliteToggle("Auth Required", config.requireAuthorization) { viewModel.updateConfig { it.copy(requireAuthorization = !it.requireAuthorization) } }
            EliteToggle("Rate Limiting", config.requestRateLimit) { viewModel.updateConfig { it.copy(requestRateLimit = !it.requestRateLimit) } }
            EliteToggle("Welcome Screen", config.serveWelcomeFile) { viewModel.updateServeWelcomeFile(!config.serveWelcomeFile) }
        }
    }
}

@Composable
fun EliteToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = AccNeon,
                uncheckedThumbColor = TextSec,
                uncheckedTrackColor = BgSurface
            )
        )
    }
}

@Composable
fun SmallMetric(label: String, value: String, color: Color) {
    Column {
        Text(label, color = TextSec, fontSize = 8.sp, fontWeight = FontWeight.Bold)
        Text(value, color = color, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        color = AccNeon,
        fontSize = 10.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = 1.5.sp,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
}

// --- Re-styling Logs Screen to match ---
@Composable
fun LogsScreen(uiState: ServerUiState, onBack: () -> Unit, onClear: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().background(BgDeep)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.border(1.dp, BorderElite, CircleShape)) {
                Icon(Icons.Default.ArrowBack, contentDescription = null, tint = TextPrimary)
            }
            Spacer(Modifier.width(16.dp))
            Text("NETWORK TRAFFIC", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary, modifier = Modifier.weight(1f))
            IconButton(onClick = onClear) {
                Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = AccError)
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(uiState.requestLogs) { log ->
                EliteLogCard(log)
            }
        }
    }
}

@Composable
fun EliteLogCard(log: LogEntry) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = BgPanel,
        border = BorderStroke(1.dp, BorderElite)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (log.method == "GET") AccSuccess.copy(alpha = 0.1f) else AccNeon.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Text(log.method.take(1), color = if (log.method == "GET") AccSuccess else AccNeon, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(log.path, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${log.clientIp} • ${log.durationMs}ms", color = TextSec, fontSize = 10.sp)
            }
            Text(
                text = "${log.statusCode}",
                color = if (log.statusCode < 400) AccSuccess else AccError,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 14.sp
            )
        }
    }
}
