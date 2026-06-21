package com.vic2ray.ui

import android.content.Context
import android.content.Intent
import android.os.Build
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vic2ray.models.ProtocolType
import com.vic2ray.models.VpnConfig
import com.vic2ray.vpn.VicVpnService
import com.vic2ray.vpn.V2rayConfigGenerator
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF00E5FF),
                    secondary = Color(0xFFB388FF),
                    background = Color(0xFF0F172A),
                    surface = Color(0xFF1E293B),
                    error = Color(0xFFFF5252)
                )
            ) {
                Vic2rayApp()
            }
        }
    }
}

/**
 * Safe start of VPN Service
 */
private fun safeStartVpnService(context: Context, jsonConfig: String) {
    val intent = Intent(context, VicVpnService::class.java).apply {
        action = VicVpnService.ACTION_CONNECT
        putExtra(VicVpnService.EXTRA_CONFIG, jsonConfig)
    }
    try {
        // VpnService only needs startService, NOT startForegroundService.
        // It automatically handles foreground system notification.
        context.startService(intent)
        Toast.makeText(context, "Connecting...", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Start error: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Vic2rayApp(mainViewModel: MainViewModel = viewModel()) {
    val uiState by mainViewModel.uiState.collectAsState()
    val selectedProtocol by mainViewModel.selectedProtocol.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    
    val isVpnConnected by VicVpnService.isConnected.collectAsState()
    val currentPing by VicVpnService.currentPing.collectAsState()
    val scope = rememberCoroutineScope()

    val context = LocalContext.current
    var pendingVpnConfig by remember { mutableStateOf<String?>(null) }

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            pendingVpnConfig?.let { config ->
                safeStartVpnService(context, config)
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Brush.linearGradient(listOf(Color(0xFF00E5FF), Color(0xFFB388FF)))),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("V", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Vic2Ray VPN", fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, color = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { showAboutDialog = true }) {
                        Icon(Icons.Default.Info, contentDescription = "About", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Manage Sources", tint = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent,
                )
            )
        },
        bottomBar = {
            AnimatedVisibility(
                visible = isVpnConnected,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                Surface(
                    color = Color(0xFF00E676).copy(alpha = 0.15f),
                    contentColor = Color(0xFF00E676),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clickable {
                                    if (currentPing != -2) {
                                        scope.launch {
                                            VicVpnService.currentPing.value = -2 // loading state
                                            val ping = com.vic2ray.tester.RealPingTester.testCurrentConnectionPing()
                                            VicVpnService.currentPing.value = ping
                                        }
                                    }
                                }
                                .padding(8.dp)
                        ) {
                            Box(modifier = Modifier.size(10.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color(0xFF00E676)))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Connected", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            
                            val pingText = when {
                                currentPing == -2 -> "Testing..."
                                currentPing > 0 -> "$currentPing ms"
                                else -> "Timeout"
                            }
                            val pingColor = when {
                                currentPing == -2 -> Color.Gray
                                currentPing in 1..299 -> Color(0xFF00E676)
                                currentPing in 300..599 -> Color(0xFFFFD600)
                                else -> Color(0xFFFF3D00)
                            }
                            Text(
                                text = "($pingText)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = pingColor
                            )
                        }
                        Button(
                            onClick = {
                                val intent = Intent(context, VicVpnService::class.java).apply {
                                    action = VicVpnService.ACTION_DISCONNECT
                                }
                                context.startService(intent)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.8f)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Disconnect", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {

            ScrollableTabRow(
                selectedTabIndex = ProtocolType.values().indexOf(selectedProtocol),
                edgePadding = 8.dp,
                containerColor = Color.Transparent,
                divider = {},
                indicator = { tabPositions ->
                    TabRowDefaults.Indicator(
                        Modifier.tabIndicatorOffset(tabPositions[ProtocolType.values().indexOf(selectedProtocol)]),
                        color = MaterialTheme.colorScheme.primary,
                        height = 3.dp
                    )
                }
            ) {
                ProtocolType.values().forEach { protocol ->
                    if (protocol != ProtocolType.UNKNOWN) {
                        Tab(
                            selected = protocol == selectedProtocol,
                            onClick = { mainViewModel.setProtocolFilter(protocol) },
                            text = {
                                Text(
                                    protocol.name,
                                    fontWeight = if (protocol == selectedProtocol) FontWeight.Bold else FontWeight.Normal,
                                    color = if (protocol == selectedProtocol) MaterialTheme.colorScheme.primary else Color.Gray
                                )
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (val state = uiState) {
                    is UiState.Idle -> {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Ready to scan servers", color = Color.Gray, fontSize = 18.sp)
                            Spacer(modifier = Modifier.height(16.dp))
                            SyncButton(onClick = { mainViewModel.syncAndTestServers() }, text = "Start Sync")
                        }
                    }
                    is UiState.Loading -> {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(state.message, color = Color.LightGray)
                        }
                    }
                    is UiState.Testing -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Testing Servers...", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                    Text("Found: ${state.foundCount}", color = Color.LightGray, fontSize = 12.sp)
                                }
                                IconButton(
                                    onClick = { mainViewModel.stopSync() },
                                    modifier = Modifier.background(Color.Red.copy(alpha = 0.2f), RoundedCornerShape(50))
                                ) {
                                    Icon(Icons.Default.Close, "Stop", tint = Color.Red)
                                }
                            }

                            ConfigList(
                                allConfigs = state.currentWorking,
                                selectedProtocol = selectedProtocol,
                                isRefreshing = true,
                                onRefresh = { },
                                onConnect = { jsonConfig ->
                                    handleConnect(context, jsonConfig, vpnPermissionLauncher) { pendingVpnConfig = it }
                                }
                            )
                        }
                    }
                    is UiState.Error -> {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Error", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                            Text(state.error, color = Color.LightGray, modifier = Modifier.padding(16.dp))
                            Spacer(modifier = Modifier.height(16.dp))
                            SyncButton(onClick = { mainViewModel.syncAndTestServers() }, text = "Retry")
                        }
                    }
                    is UiState.Success -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                horizontalArrangement = Arrangement.End
                            ) {
                                IconButton(
                                    onClick = { mainViewModel.syncAndTestServers() },
                                    modifier = Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), RoundedCornerShape(50))
                                ) {
                                    Icon(Icons.Default.Refresh, "Refresh", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                            ConfigList(
                                allConfigs = state.configs,
                                selectedProtocol = selectedProtocol,
                                isRefreshing = false,
                                onRefresh = { mainViewModel.syncAndTestServers() },
                                onConnect = { jsonConfig ->
                                    handleConnect(context, jsonConfig, vpnPermissionLauncher) { pendingVpnConfig = it }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun handleConnect(
    context: Context,
    jsonConfig: String,
    vpnPermissionLauncher: androidx.activity.result.ActivityResultLauncher<Intent>,
    setPendingConfig: (String) -> Unit
) {
    try {
        val vpnIntent = android.net.VpnService.prepare(context)
        if (vpnIntent != null) {
            setPendingConfig(jsonConfig)
            vpnPermissionLauncher.launch(vpnIntent)
        } else {
            safeStartVpnService(context, jsonConfig)
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
        title = { Text("Manage Sources", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Current Sources:", color = Color.LightGray, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                ) {
                    items(customSources) { source ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = source,
                                color = Color.White,
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f),
                                maxLines = 1
                            )
                            IconButton(onClick = { mainViewModel.removeSource(source) }) {
                                Icon(Icons.Default.Close, contentDescription = "Delete", tint = Color.Red, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    label = { Text("New Raw URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Gray
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
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) { Text("Add", color = Color.Black, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = Color.LightGray) }
        }
    )
}

@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("About", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Developer: Hooman Ghardashkhani", color = Color.LightGray, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(16.dp))
                
                Button(
                    onClick = { uriHandler.openUri("https://daramet.com/hoomanghkhani") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Donate (Daramet)", color = Color.Black, fontWeight = FontWeight.Bold)
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Button(
                    onClick = { uriHandler.openUri("https://github.com/Hoomanghkhani/") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333))
                ) {
                    Text("GitHub", color = Color.White, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(8.dp))
                
                Button(
                    onClick = { uriHandler.openUri("https://www.linkedin.com/in/hooman-ghkhani/") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0077B5))
                ) {
                    Text("LinkedIn", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = MaterialTheme.colorScheme.primary) }
        }
    )
}

@Composable
fun SyncButton(onClick: () -> Unit, text: String = "Start Sync") {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.padding(16.dp).height(48.dp)
    ) {
        Text(text, color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun ConfigList(
    allConfigs: List<VpnConfig>,
    selectedProtocol: ProtocolType,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onConnect: (String) -> Unit
) {
    val filteredList = allConfigs.filter { it.protocol == selectedProtocol }
    val pullRefreshState = rememberPullRefreshState(refreshing = isRefreshing, onRefresh = onRefresh)

    Box(modifier = Modifier.fillMaxSize().pullRefresh(pullRefreshState)) {
        if (filteredList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No server found for this protocol.\nPull down to refresh.",
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(filteredList) { config ->
                    ConfigItemCard(config = config, onConnect = onConnect)
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
fun ConfigItemCard(config: VpnConfig, onConnect: (String) -> Unit) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable {
                try {
                    val jsonConfig = V2rayConfigGenerator.generateJsonConfig(config.rawConfig, config.protocol)
                    onConnect(jsonConfig)
                } catch (e: Exception) {
                    Toast.makeText(context, "Config parse error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surface,
                            Color(0xFF263238)
                        )
                    )
                )
        ) {
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
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
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = config.protocol.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                val pingColor = when {
                    config.ping < 300 -> Color(0xFF00E676)
                    config.ping < 600 -> Color(0xFFFFD600)
                    else -> Color(0xFFFF3D00)
                }
                Text(
                    text = "${config.ping} ms",
                    fontWeight = FontWeight.Bold,
                    color = pingColor
                )
            }
        }
    }
}
