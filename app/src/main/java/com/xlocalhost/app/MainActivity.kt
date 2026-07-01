package com.xlocalhost.app

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
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

// ── Corporate Elite Palette ────────────────────────────────────────────────
private val BgDeep      = Color(0xFF090B0D)
private val BgPanel     = Color(0xFF121417)
private val BgSurface   = Color(0xFF1A1D21)
private val AccNeon     = Color(0xFF00D4FF)
private val AccSuccess  = Color(0xFF00FF85)
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
        override fun onReceive(context: Context?, intent: Intent?) {
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
            registerReceiver(logReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
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
        HeaderSection(uiState.isRunning, onShowLogs)

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                ServerStatusCard(
                    uiState = uiState, 
                    onStart = { viewModel.startServer(context) }, 
                    onStop = { viewModel.stopServer(context) }
                )
            }

            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetricCard(
                        label = "RAM USAGE", 
                        value = "${(uiState.ramUsage * 100).toInt()}%", 
                        progress = uiState.ramUsage, 
                        color = AccNeon, 
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        label = "STORAGE", 
                        value = "${(uiState.storageUsage * 100).toInt()}%", 
                        progress = uiState.storageUsage, 
                        color = AccSuccess, 
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatsBox("REQUESTS", "${uiState.totalRequests}", Icons.Default.TrendingUp, Modifier.weight(1f))
                    StatsBox("DATABASE", if (config.enableSqlite) "ACTIVE" else "OFF", Icons.Default.Storage, Modifier.weight(1f))
                }
            }

            item {
                SectionLabel("Directory Control")
                DirectoryPanel(
                    config = config, 
                    onPickFolder = { folderPickerLauncher.launch(null) }
                )
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
    val context = LocalContext.current
    
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = BgPanel,
        border = BorderStroke(1.dp, BorderElite)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("LOCAL ACCESS URL", color = TextSec, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = uiState.serverUrl, 
                        color = AccNeon, 
                        fontSize = 18.sp, 
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.clickable {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Server URL", uiState.serverUrl)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "URL copiada al portapapeles", Toast.LENGTH_SHORT).show()
                        }
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
            
            AnimatedVisibility(
                visible = uiState.isRunning,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    Spacer(Modifier.height(20.dp))
                    Divider(color = BorderElite, thickness = 0.5.dp)
                    Spacer(Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        SmallMetric("STATUS", "Running", AccSuccess)
                        SmallMetric("UPTIME", uiState.uptime, AccNeon)
                        SmallMetric("PORT", "${uiState.config.port}", TextPrimary)
                    }
                }
            }
        }
    }
}

@Composable
fun MetricCard(label: String, value: String, progress: Float, color: Color, modifier: Modifier) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(1000, easing = FastOutSlowInEasing)
    )

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
                progress = animatedProgress,
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
fun DirectoryPanel(config: ServerConfig, onPickFolder: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPickFolder() },
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
                    text = if (config.folderDisplayPath.isEmpty()) "No folder selected" else config.folderDisplayPath,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(Icons.Default.Edit, contentDescription = null, tint = TextSec, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
fun SectionLabel(label: String) {
    Text(
        text = label,
        color = TextSec,
        fontSize = 11.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
}

@Composable
fun SmallMetric(label: String, value: String, color: Color) {
    Column {
        Text(label, color = TextSec, fontSize = 8.sp, fontWeight = FontWeight.Bold)
        Text(value, color = color, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
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
        Column(modifier = Modifier.padding(16.dp)) {
            EliteToggle("Allow Modifications", config.allowModification) {
                viewModel.updateConfig { it.copy(allowModification = it) }
            }
            Divider(color = BorderElite, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 12.dp))
            EliteToggle("Enable SQLite API", config.enableSqlite) {
                viewModel.updateConfig { it.copy(enableSqlite = it) }
            }
            Divider(color = BorderElite, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 12.dp))
            EliteToggle("Auto-start on Boot", config.autostartOnBoot) {
                viewModel.updateConfig { it.copy(autostartOnBoot = it) }
            }
            Divider(color = BorderElite, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 12.dp))
            EliteToggle("Enable CORS (*)", config.configureCors) {
                viewModel.updateConfig { it.copy(configureCors = it) }
            }
        }
    }
}

@Composable
fun EliteToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = AccNeon,
                checkedTrackColor = AccNeon.copy(alpha = 0.2f),
                uncheckedThumbColor = TextSec,
                uncheckedTrackColor = BgSurface
            )
        )
    }
}

@Composable
fun LogsScreen(uiState: ServerUiState, onBack: () -> Unit, onClear: () -> Unit) {
    val listState = rememberLazyListState()
    
    LaunchedEffect(uiState.logs.size) {
        if (uiState.logs.isNotEmpty()) {
            listState.animateScrollToItem(uiState.logs.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(BgDeep)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = null, tint = TextPrimary)
            }
            Text("SYSTEM TERMINAL", color = TextPrimary, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            IconButton(onClick = onClear) {
                Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = AccError)
            }
        }
        
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black)
                .border(1.dp, BorderElite, RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            items(uiState.logs) { log ->
                Text(
                    text = log,
                    color = AccSuccess,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
