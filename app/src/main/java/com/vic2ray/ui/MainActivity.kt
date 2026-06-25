package com.vic2ray.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vic2ray.models.VpnConfig
import com.vic2ray.vpn.VicVpnService
import com.vic2ray.vpn.V2rayConfigGenerator
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VictoryTheme {
                Vic2rayApp()
            }
        }
    }
}

@Composable
fun VictoryTheme(content: @Composable () -> Unit) {
    val victoryColorScheme = darkColorScheme(
        primary = Color(0xFFFFD700), // Victory Gold
        onPrimary = Color(0xFF332B00),
        secondary = Color(0xFF00E676), // Emerald Success
        background = Color(0xFF050A18), // Ultra Dark Navy
        surface = Color(0xFF11192E), // Deep Navy
        surfaceVariant = Color(0xFF1E2738),
        onSurface = Color.White,
        error = Color(0xFFFF5252)
    )

    MaterialTheme(
        colorScheme = victoryColorScheme,
        typography = Typography(),
        content = content
    )
}

/**
 * Safe start of VPN Service
 */
private fun safeStartVpnService(context: Context, jsonConfig: String, rawConfig: String) {
    val intent = Intent(context, VicVpnService::class.java).apply {
        action = VicVpnService.ACTION_CONNECT
        putExtra(VicVpnService.EXTRA_CONFIG, jsonConfig)
        putExtra(VicVpnService.EXTRA_RAW_CONFIG, rawConfig)
    }
    try {
        context.startService(intent)
        Toast.makeText(context, "Victory is near...", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Start error: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Vic2rayApp(mainViewModel: MainViewModel = viewModel()) {
    val uiState by mainViewModel.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    
    val isVpnConnected by VicVpnService.isConnected.collectAsState()
    val currentPing by VicVpnService.currentPing.collectAsState()
    val connectedRawConfig by VicVpnService.connectedRawConfig.collectAsState()
    val scope = rememberCoroutineScope()

    val context = LocalContext.current
    var pendingVpnConfig by remember { mutableStateOf<Pair<String, String>?>(null) }

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            pendingVpnConfig?.let { config ->
                safeStartVpnService(context, config.first, config.second)
            }
        } else {
            Toast.makeText(context, "VPN Permission Denied!", Toast.LENGTH_SHORT).show()
        }
        pendingVpnConfig = null
    }

    if (showAddDialog) {
        SourcesManagementDialog(
            mainViewModel = mainViewModel,
            onDismiss = { showAddDialog = false }
        )
    }

    if (showAboutDialog) {
        AboutDialog(onDismiss = { showAboutDialog = false })
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Vic2Ray",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 28.sp,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp
                    )
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Sources", tint = Color.Gray)
                    }
                    IconButton(onClick = { showAboutDialog = true }) {
                        Icon(Icons.Default.Info, contentDescription = "About", tint = Color.Gray)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent,
                )
            )
        },
        bottomBar = {
            ConnectionStatusPanel(
                isConnected = isVpnConnected,
                ping = currentPing,
                onPingClick = {
                    if (currentPing != -2) {
                        scope.launch {
                            VicVpnService.currentPing.value = -2
                            val p = com.vic2ray.tester.RealPingTester.testCurrentConnectionPing()
                            VicVpnService.currentPing.value = p
                        }
                    }
                },
                onDisconnect = {
                    val intent = Intent(context, VicVpnService::class.java).apply {
                        action = VicVpnService.ACTION_DISCONNECT
                    }
                    context.startService(intent)
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (val state = uiState) {
                is UiState.Idle -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(120.dp)
                                .shadow(24.dp, CircleShape, spotColor = MaterialTheme.colorScheme.primary)
                                .clip(CircleShape)
                                .background(Brush.radialGradient(listOf(MaterialTheme.colorScheme.primary, Color(0xFFB8860B))))
                                .clickable { mainViewModel.syncAndTestServers() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Refresh, "Sync", modifier = Modifier.size(48.dp), tint = Color.Black)
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        Text("Ready for Victory", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("Tap to find active servers", color = Color.Gray, fontSize = 14.sp)
                    }
                }
                is UiState.Loading -> {
                    LoadingView(state.message)
                }
                is UiState.Testing -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        TestingHeader(state.foundCount, state.totalConfigs) { mainViewModel.stopSync() }
                        ConfigList(
                            configs = state.currentWorking,
                            isRefreshing = true,
                            onRefresh = { },
                            onConnect = { json, raw -> handleConnect(context, json, raw, vpnPermissionLauncher) { pendingVpnConfig = it } },
                            connectedRawConfig = connectedRawConfig
                        )
                    }
                }
                is UiState.Error -> {
                    ErrorView(state.error) { mainViewModel.syncAndTestServers() }
                }
                is UiState.Success -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        ConfigList(
                            configs = state.configs,
                            isRefreshing = false,
                            onRefresh = { mainViewModel.syncAndTestServers() },
                            onConnect = { json, raw -> handleConnect(context, json, raw, vpnPermissionLauncher) { pendingVpnConfig = it } },
                            connectedRawConfig = connectedRawConfig
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LoadingView(message: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, strokeWidth = 4.dp)
        Spacer(modifier = Modifier.height(16.dp))
        Text(message, color = Color.LightGray, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun ErrorView(error: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(64.dp))
        Spacer(modifier = Modifier.height(16.dp))
        Text("Oops! Something went wrong", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(error, color = Color.Gray, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) {
            Text("Retry", color = Color.Black, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun TestingHeader(found: Int, total: Int, onStop: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text("Scanning the Horizon", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
            Text("Verified: $found / Total: $total", color = Color.Gray, fontSize = 12.sp)
        }
        TextButton(onClick = onStop, colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)) {
            Text("Stop", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ConnectionStatusPanel(isConnected: Boolean, ping: Int, onPingClick: () -> Unit, onDisconnect: () -> Unit) {
    AnimatedVisibility(
        visible = isConnected,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
        ) {
            Row(
                modifier = Modifier.padding(20.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onPingClick() }) {
                    Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondary))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Connection Secure", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                        val pingText = when {
                            ping == -2 -> "Testing Speed..."
                            ping > 0 -> "$ping ms"
                            else -> "Network Timeout"
                        }
                        Text(pingText, fontSize = 13.sp, color = if(ping > 0) MaterialTheme.colorScheme.secondary else Color.Gray)
                    }
                }
                Button(
                    onClick = onDisconnect,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.8f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("End Session", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun handleConnect(
    context: Context,
    jsonConfig: String,
    rawConfig: String,
    vpnPermissionLauncher: androidx.activity.result.ActivityResultLauncher<Intent>,
    setPendingConfig: (Pair<String, String>) -> Unit
) {
    try {
        val vpnIntent = android.net.VpnService.prepare(context)
        if (vpnIntent != null) {
            setPendingConfig(Pair(jsonConfig, rawConfig))
            vpnPermissionLauncher.launch(vpnIntent)
        } else {
            safeStartVpnService(context, jsonConfig, rawConfig)
        }
    } catch (e: Exception) {
        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourcesManagementDialog(mainViewModel: MainViewModel, onDismiss: () -> Unit) {
    val customSources by mainViewModel.customSources.collectAsState()
    var urlInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Network Sources", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp)) {
                    items(customSources) { source ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (source.length > 35) "..." + source.takeLast(32) else source,
                                    color = Color.LightGray,
                                    fontSize = 11.sp,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1
                                )
                                IconButton(onClick = { mainViewModel.removeSource(source) }) {
                                    Icon(Icons.Default.Delete, null, tint = Color.Gray, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    label = { Text("New Feed URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.DarkGray
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (urlInput.isNotBlank()) {
                        mainViewModel.addCustomSource(urlInput)
                        urlInput = ""
                    }
                },
                shape = RoundedCornerShape(8.dp)
            ) { Text("Add", color = Color.Black, fontWeight = FontWeight.Bold) }
        }
    )
}

@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { 
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier.size(60.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text("V", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 32.sp)
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text("Vic2Ray", color = Color.White, fontWeight = FontWeight.Black, fontSize = 24.sp)
                Text("v1.0.19 Stable", color = Color.Gray, fontSize = 12.sp)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Crafted with passion for freedom.", color = Color.LightGray, fontSize = 14.sp, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(24.dp))
                
                AboutLinkItem("Hooman Ghardashkhani", "https://hoomanghkhani.github.io/", Icons.Default.Person)
                Spacer(modifier = Modifier.height(12.dp))
                AboutLinkItem("GitHub Repository", "https://github.com/Hoomanghkhani/", Icons.Default.Share)
                Spacer(modifier = Modifier.height(12.dp))
                AboutLinkItem("LinkedIn Profile", "https://www.linkedin.com/in/hooman-ghkhani/", Icons.Default.AccountCircle)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutLinkItem(label: String, url: String, icon: ImageVector) {
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    Surface(
        onClick = { uriHandler.openUri(url) },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Text(label, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            Spacer(modifier = Modifier.weight(1f))
            Icon(Icons.Default.ArrowForward, null, tint = Color.Gray, modifier = Modifier.size(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun ConfigList(
    configs: List<VpnConfig>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onConnect: (String, String) -> Unit,
    connectedRawConfig: String?
) {
    val pullRefreshState = rememberPullRefreshState(refreshing = isRefreshing, onRefresh = onRefresh)

    Box(modifier = Modifier.fillMaxSize().pullRefresh(pullRefreshState)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 8.dp, bottom = 100.dp)
        ) {
            if (configs.isEmpty() && !isRefreshing) {
                item {
                    Box(
                        modifier = Modifier
                            .fillParentMaxSize()
                            .padding(bottom = 100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No servers available.\nPull down to discover.",
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                items(configs) { config ->
                    ConfigItemCard(
                        config = config, 
                        isConnected = config.rawConfig == connectedRawConfig,
                        onConnect = onConnect
                    )
                }
            }
        }

        PullRefreshIndicator(
            refreshing = isRefreshing,
            state = pullRefreshState,
            modifier = Modifier.align(Alignment.TopCenter),
            backgroundColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
fun ConfigItemCard(config: VpnConfig, isConnected: Boolean, onConnect: (String, String) -> Unit) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable {
                try {
                    val jsonConfig = V2rayConfigGenerator.generateJsonConfig(config.rawConfig, config.protocol)
                    onConnect(jsonConfig, config.rawConfig)
                } catch (e: Exception) {
                    Toast.makeText(context, "Config error", Toast.LENGTH_SHORT).show()
                }
            },
        colors = CardDefaults.cardColors(
            containerColor = if (isConnected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface
        ),
        border = if (isConnected) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = config.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1
                )
                Text(
                    text = config.protocol.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Bold
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                val pingColor = when {
                    config.ping < 300 -> MaterialTheme.colorScheme.secondary
                    config.ping < 600 -> Color(0xFFFFD600)
                    else -> MaterialTheme.colorScheme.error
                }
                Text(
                    text = "${config.ping} ms",
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp,
                    color = pingColor
                )
                if (isConnected) {
                    Text("ACTIVE", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black, fontSize = 10.sp)
                }
            }
        }
    }
}
