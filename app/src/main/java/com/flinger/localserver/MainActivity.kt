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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF82AAFF),
    onPrimary = Color(0xFF003062),
    primaryContainer = Color(0xFF00468B),
    onPrimaryContainer = Color(0xFFD5E3FF),
    secondary = Color(0xFF89DDFF),
    onSecondary = Color(0xFF003549),
    secondaryContainer = Color(0xFF004D67),
    onSecondaryContainer = Color(0xFFBFE9FF),
    tertiary = Color(0xFFC3F0CA),
    onTertiary = Color(0xFF003914),
    tertiaryContainer = Color(0xFF005221),
    onTertiaryContainer = Color(0xFFDFF7E4),
    background = Color(0xFF0D1117),
    surface = Color(0xFF0D1117),
    surfaceVariant = Color(0xFF161B22),
    onBackground = Color(0xFFC9D1D9),
    onSurface = Color(0xFFC9D1D9),
    onSurfaceVariant = Color(0xFF8B949E),
    error = Color(0xFFFF7B72),
    onError = Color(0xFF690005),
    outline = Color(0xFF30363D),
)

class MainActivity : ComponentActivity() {

    private val viewModel: ServerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = DarkColorScheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    XLocalHostApp(viewModel = viewModel)
                }
            }
        }
        requestNotificationPermission()
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

    if (showLogs) {
        LogsScreen(
            logs = uiState.logs,
            onBack = { showLogs = false },
            onClear = viewModel::clearLogs
        )
    } else {
        MainScreen(
            uiState = uiState,
            viewModel = viewModel,
            onShowLogs = { showLogs = true }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    uiState: ServerUiState,
    viewModel: ServerViewModel,
    onShowLogs: () -> Unit
) {
    val context = LocalContext.current

    var generalExpanded by rememberSaveable { mutableStateOf(true) }
    var filesExpanded by rememberSaveable { mutableStateOf(true) }
    var securityExpanded by rememberSaveable { mutableStateOf(false) }
    var miscExpanded by rememberSaveable { mutableStateOf(false) }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, flags)
            val displayPath = uri.lastPathSegment ?: uri.toString()
            viewModel.setFolderUri(uri, displayPath)
        }
    }

    Scaffold(
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "x-localhost",
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                actions = {
                    IconButton(onClick = onShowLogs) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = "Logs",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                ServerStatusCard(
                    uiState = uiState,
                    onStart = { viewModel.startServer(context) },
                    onStop = { viewModel.stopServer(context) },
                    onShowLogs = onShowLogs
                )
            }

            item {
                ExpandableSection(
                    title = "General",
                    icon = Icons.Default.Tune,
                    expanded = generalExpanded,
                    onToggle = { generalExpanded = !generalExpanded }
                ) {
                    GeneralSectionContent(uiState = uiState, viewModel = viewModel)
                }
            }

            item {
                ExpandableSection(
                    title = "Files",
                    icon = Icons.Default.Folder,
                    expanded = filesExpanded,
                    onToggle = { filesExpanded = !filesExpanded }
                ) {
                    FilesSectionContent(
                        uiState = uiState,
                        viewModel = viewModel,
                        onPickFolder = { folderPickerLauncher.launch(null) }
                    )
                }
            }

            item {
                ExpandableSection(
                    title = "Security",
                    icon = Icons.Default.Security,
                    expanded = securityExpanded,
                    onToggle = { securityExpanded = !securityExpanded }
                ) {
                    SecuritySectionContent(uiState = uiState, viewModel = viewModel)
                }
            }

            item {
                ExpandableSection(
                    title = "Misc",
                    icon = Icons.Default.Settings,
                    expanded = miscExpanded,
                    onToggle = { miscExpanded = !miscExpanded }
                ) {
                    MiscSectionContent(uiState = uiState, viewModel = viewModel)
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
fun ServerStatusCard(
    uiState: ServerUiState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onShowLogs: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    val serverUrl = "http://${uiState.localIpAddress}:${uiState.config.port}"
    val isRunning = uiState.isRunning

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(
                            if (isRunning) Color(0xFF3FB950) else Color(0xFF6E7681)
                        )
                )
                Text(
                    text = if (isRunning) "RUNNING" else "STOPPED",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = if (isRunning) Color(0xFF3FB950) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isRunning) {
                TextButton(onClick = { uriHandler.openUri(serverUrl) }) {
                    Text(
                        text = serverUrl,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onStart,
                    enabled = !isRunning && uiState.config.folderUri != null,
                    modifier = Modifier.weight(1f).height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF238636),
                        contentColor = Color.White,
                        disabledContainerColor = Color(0xFF21262D),
                        disabledContentColor = Color(0xFF6E7681)
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("START", fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }

                Button(
                    onClick = onStop,
                    enabled = isRunning,
                    modifier = Modifier.weight(1f).height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFDA3633),
                        contentColor = Color.White,
                        disabledContainerColor = Color(0xFF21262D),
                        disabledContentColor = Color(0xFF6E7681)
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("STOP", fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }

                FilledTonalButton(
                    onClick = onShowLogs,
                    modifier = Modifier.height(52.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Terminal, contentDescription = "Logs", modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("LOGS", fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

@Composable
fun ExpandableSection(
    title: String,
    icon: ImageVector,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = content
                )
            }
        }
    }
}

@Composable
fun GeneralSectionContent(uiState: ServerUiState, viewModel: ServerViewModel) {
    var portText by remember { mutableStateOf(uiState.config.port.toString()) }
    val isRunning = uiState.isRunning

    OutlinedTextField(
        value = portText,
        onValueChange = { value ->
            portText = value
            value.toIntOrNull()?.let { port ->
                if (port in 1024..65535) {
                    viewModel.updatePort(port)
                }
            }
        },
        label = { Text("Puerto", fontFamily = FontFamily.Monospace) },
        enabled = !isRunning,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
        )
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "IP Local",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = uiState.localIpAddress,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "URL",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        val uriHandler = LocalUriHandler.current
        val url = "http://${uiState.localIpAddress}:${uiState.config.port}"
        TextButton(
            onClick = { if (isRunning) uriHandler.openUri(url) },
            enabled = isRunning
        ) {
            Text(
                text = url,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun FilesSectionContent(
    uiState: ServerUiState,
    viewModel: ServerViewModel,
    onPickFolder: () -> Unit
) {
    val config = uiState.config

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.background,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(12.dp)
    ) {
        Text(
            text = "Carpeta raíz",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = config.folderDisplayPath.ifEmpty { "Ninguna carpeta seleccionada" },
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            color = if (config.folderDisplayPath.isEmpty())
                MaterialTheme.colorScheme.onSurfaceVariant
            else
                MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }

    Button(
        onClick = onPickFolder,
        modifier = Modifier.fillMaxWidth(),
        enabled = !uiState.isRunning,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text("Seleccionar carpeta", fontFamily = FontFamily.Monospace)
    }

    CheckboxRow(
        checked = config.renderFolderPages,
        label = "Render folder content pages",
        onCheckedChange = { viewModel.updateConfig { it.copy(renderFolderPages = !it.renderFolderPages) } }
    )

    CheckboxRow(
        checked = config.allowModification,
        label = "Allow file modification (PUT/DELETE)",
        onCheckedChange = { viewModel.updateConfig { it.copy(allowModification = !it.allowModification) } }
    )
}

@Composable
fun SecuritySectionContent(uiState: ServerUiState, viewModel: ServerViewModel) {
    val config = uiState.config

    Text(
        text = "Las funciones de seguridad estarán disponibles en una próxima versión.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontFamily = FontFamily.Monospace
    )

    Spacer(modifier = Modifier.height(4.dp))

    CheckboxRow(
        checked = config.requireAuthorization,
        label = "Require authorization (básica)",
        enabled = false,
        onCheckedChange = { viewModel.updateConfig { it.copy(requireAuthorization = !it.requireAuthorization) } }
    )

    CheckboxRow(
        checked = config.useTls,
        label = "Use TLS encryption (HTTPS)",
        enabled = false,
        onCheckedChange = { viewModel.updateConfig { it.copy(useTls = !it.useTls) } }
    )
}

@Composable
fun MiscSectionContent(uiState: ServerUiState, viewModel: ServerViewModel) {
    val context = LocalContext.current
    val config = uiState.config

    CheckboxRow(
        checked = config.autoShutdownByInactivity,
        label = "Auto shutdown by inactivity",
        onCheckedChange = {
            viewModel.updateConfig { it.copy(autoShutdownByInactivity = !it.autoShutdownByInactivity) }
            viewModel.addLog("Auto-shutdown por inactividad: ${!config.autoShutdownByInactivity}")
        }
    )

    CheckboxRow(
        checked = config.excludeFromBatteryOptimization,
        label = "Exclude from battery optimization",
        onCheckedChange = { checked ->
            viewModel.updateConfig { it.copy(excludeFromBatteryOptimization = checked) }
            if (checked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                try {
                    context.startActivity(intent)
                } catch (e: Exception) {
                    viewModel.addLog("Error: no se pudo abrir configuración de batería.")
                }
            }
        }
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.BatteryFull,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = "WakeLock parcial activo mientras el servidor esté corriendo.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
fun CheckboxRow(
    checked: Boolean,
    label: String,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = CheckboxDefaults.colors(
                checkedColor = MaterialTheme.colorScheme.primary,
                uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = if (enabled) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(
    logs: List<String>,
    onBack: () -> Unit,
    onClear: () -> Unit
) {
    val listState = rememberLazyListState()

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    Scaffold(
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Logs",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Volver",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onClear) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Limpiar logs",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        if (logs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Sin logs aún.",
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(logs) { entry ->
                    Text(
                        text = entry,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = when {
                            entry.contains("Error") -> MaterialTheme.colorScheme.error
                            entry.contains("iniciado") -> Color(0xFF3FB950)
                            entry.contains("detenido") -> Color(0xFFFF7B72)
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}
