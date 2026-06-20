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
 * شروع امن سرویس VPN با مدیریت تمام نسخه‌های اندروید
 */
private fun safeStartVpnService(context: Context, jsonConfig: String) {
    val intent = Intent(context, VicVpnService::class.java).apply {
        action = VicVpnService.ACTION_CONNECT
        putExtra(VicVpnService.EXTRA_CONFIG, jsonConfig)
    }
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        Toast.makeText(context, "در حال اتصال به سرور...", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "خطا در شروع سرویس: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Vic2rayApp(mainViewModel: MainViewModel = viewModel()) {
    val uiState by mainViewModel.uiState.collectAsState()
    val selectedProtocol by mainViewModel.selectedProtocol.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

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
            Toast.makeText(context, "مجوز VPN داده نشد!", Toast.LENGTH_SHORT).show()
        }
        pendingVpnConfig = null
    }

    // دیالوگ مدیریت لینک‌ها
    if (showAddDialog) {
        SourcesManagementDialog(
            mainViewModel = mainViewModel,
            onDismiss = { showAddDialog = false }
        )
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
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "مدیریت سورس‌ها", tint = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent,
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {

            // تب‌های پروتکل
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

            // بخش اصلی
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (val state = uiState) {
                    is UiState.Idle -> {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("آماده برای اسکن سرورها", color = Color.Gray, fontSize = 18.sp)
                            Spacer(modifier = Modifier.height(16.dp))
                            SyncButton(onClick = { mainViewModel.syncAndTestServers() })
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
                                    Text("در حال تست...", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                    Text("پیدا شده: ${state.foundCount}", color = Color.LightGray, fontSize = 12.sp)
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
                                onRefresh = { /* در حالت تست، رفرش مجدد فعال نیست */ },
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
                            Text("خطا", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                            Text(state.error, color = Color.LightGray, modifier = Modifier.padding(16.dp))
                            Spacer(modifier = Modifier.height(16.dp))
                            SyncButton(onClick = { mainViewModel.syncAndTestServers() }, text = "تلاش مجدد")
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

/**
 * مدیریت فرآیند اتصال: بررسی مجوز VPN و شروع سرویس
 */
private fun handleConnect(
    context: Context,
    jsonConfig: String,
    vpnPermissionLauncher: androidx.activity.result.ActivityResultLauncher<Intent>,
    setPendingConfig: (String) -> Unit
) {
    try {
        val vpnIntent = android.net.VpnService.prepare(context)
        if (vpnIntent != null) {
            // نیاز به دریافت مجوز VPN از کاربر
            setPendingConfig(jsonConfig)
            vpnPermissionLauncher.launch(vpnIntent)
        } else {
            // مجوز قبلاً داده شده، مستقیم وصل شو
            safeStartVpnService(context, jsonConfig)
        }
    } catch (e: Exception) {
        Toast.makeText(context, "خطا: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

// ============================================================
// دیالوگ مدیریت لینک‌ها
// ============================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourcesManagementDialog(mainViewModel: MainViewModel, onDismiss: () -> Unit) {
    val customSources by mainViewModel.customSources.collectAsState()
    var urlInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("مدیریت لینک‌های سرور", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("لینک‌های فعلی:", color = Color.LightGray, fontSize = 14.sp)
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
                                Icon(Icons.Default.Close, contentDescription = "حذف", tint = Color.Red, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    label = { Text("لینک خام (Raw) جدید") },
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
            ) { Text("اضافه کردن", color = Color.Black, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("بستن", color = Color.LightGray) }
        }
    )
}

// ============================================================
// دکمه همگام‌سازی
// ============================================================

@Composable
fun SyncButton(onClick: () -> Unit, text: String = "شروع همگام سازی") {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.padding(16.dp).height(48.dp)
    ) {
        Text(text, color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

// ============================================================
// لیست سرورها با Pull-to-Refresh
// ============================================================

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
                    "سروری برای این پروتکل یافت نشد.\nبه پایین بکشید تا رفرش شود.",
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

// ============================================================
// کارت هر سرور
// ============================================================

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
                    Toast.makeText(context, "خطا در پردازش کانفیگ: ${e.message}", Toast.LENGTH_LONG).show()
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

                // پینگ
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
