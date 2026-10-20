package com.vic2ray.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vic2ray.R
import com.vic2ray.models.ProtocolType
import com.vic2ray.models.NetworkType
import com.vic2ray.models.ServerSort
import com.vic2ray.models.TestFailure
import com.vic2ray.models.VpnConfig
import com.vic2ray.vpn.VicVpnService
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun Dashboard(
    viewModel: MainViewModel,
    localError: String?,
    onConnect: (VpnConfig) -> Unit,
    onDisconnect: () -> Unit,
    onImport: () -> Unit,
    onSources: () -> Unit,
    onAbout: () -> Unit,
    onShare: (VpnConfig) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val configs by viewModel.allConfigs.collectAsState()
    val visible by viewModel.visibleConfigs.collectAsState()
    val search by viewModel.searchQuery.collectAsState()
    val protocol by viewModel.selectedProtocol.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val onlyFavorites by viewModel.favoritesOnly.collectAsState()
    val sort by viewModel.sortOrder.collectAsState()
    val lastSync by viewModel.lastSyncTime.collectAsState()
    val failures by viewModel.sourceFailures.collectAsState()
    val testFailures by viewModel.testFailures.collectAsState()
    val networkType by viewModel.networkType.collectAsState()
    val language by viewModel.language.collectAsState()
    val connected by VicVpnService.isConnected.collectAsState()
    val connecting by VicVpnService.isConnecting.collectAsState()
    val activeRaw by VicVpnService.connectedRawConfig.collectAsState()
    val ping by VicVpnService.currentPing.collectAsState()
    val since by VicVpnService.connectedSince.collectAsState()
    val serviceError by VicVpnService.lastError.collectAsState()
    val refreshing = state is UiState.Loading || state is UiState.Testing
    val fastest = remember(configs) { configs.filter { it.isWorking && it.ping > 0 }.minByOrNull { it.ping } }
    val current = configs.firstOrNull { it.rawConfig == activeRaw }
    val workingCount = configs.count { it.isWorking && it.ping > 0 }
    val lastSyncLabel = if (lastSync == 0L) stringResource(R.string.never_synced) else {
        val dateText = remember(lastSync, language) {
            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale.forLanguageTag(language)).format(Date(lastSync))
        }
        stringResource(R.string.last_sync, dateText)
    }

    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Panel, Navy, Navy))
        ),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxHeight().widthIn(max = 760.dp).fillMaxWidth()
                .statusBarsPadding().navigationBarsPadding().imePadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "brand") {
                DashboardHeader(
                    onLanguage = { viewModel.setLanguage(if (language == "fa") "en" else "fa") },
                    onSources = onSources,
                    onImport = onImport,
                    onAbout = onAbout
                )
            }
            item(key = "connection") {
                ConnectionHero(
                    connected = connected,
                    connecting = connecting,
                    serverName = current?.name,
                    ping = ping,
                    connectedSince = since,
                    fastest = fastest,
                    onPower = { if (connected || connecting) onDisconnect() else fastest?.let(onConnect) }
                )
            }
            item(key = "actions") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { if (refreshing) viewModel.stopSync() else viewModel.syncAndTestServers() },
                        modifier = Modifier.weight(1.35f).heightIn(min = 52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = victoryButtonColors()
                    ) {
                        Icon(if (refreshing) Icons.Default.Close else Icons.Default.Refresh, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(if (refreshing) R.string.stop else if (configs.isEmpty()) R.string.refresh else R.string.refresh_again), fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = onImport,
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Outline),
                        colors = victoryOutlinedButtonColors()
                    ) {
                        Icon(Icons.Default.Add, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.import_action), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            item(key = "summary") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).background(if (workingCount > 0) Mint else Muted, CircleShape))
                    Spacer(Modifier.width(7.dp))
                    Text(stringResource(R.string.working_count, workingCount), style = MaterialTheme.typography.labelMedium, color = Mint)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.all_count, configs.size), style = MaterialTheme.typography.labelMedium, color = Muted)
                }
            }
            if (refreshing) item(key = "progress") {
                RefreshProgress(state, viewModel::stopSync)
            }
            if (testFailures.isNotEmpty()) item(key = "test_failures") {
                TestFailureSummary(testFailures)
            }
            val errorMessage = localError ?: serviceError
            if (!errorMessage.isNullOrBlank()) item(key = "connection_error") {
                MessageCard(stringResource(R.string.connection_error), errorMessage, isError = true)
            }
            if (state is UiState.Error) item(key = "refresh_error") {
                MessageCard(
                    title = stringResource(R.string.refresh_error),
                    message = (state as UiState.Error).error,
                    isError = true,
                    action = stringResource(R.string.retry),
                    onAction = viewModel::syncAndTestServers
                )
            }
            if (failures.isNotEmpty()) item(key = "source_failures") {
                MessageCard(
                    title = stringResource(R.string.sources),
                    message = stringResource(R.string.failed_sources, failures.size),
                    action = stringResource(R.string.manage_sources),
                    onAction = onSources
                )
            }
            item(key = "catalog_controls") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.servers), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                            Spacer(Modifier.height(3.dp))
                            Text(lastSyncLabel, style = MaterialTheme.typography.labelSmall, color = Muted)
                            Text(stringResource(R.string.network_test_context, stringResource(when (networkType) {
                                NetworkType.WIFI -> R.string.network_wifi
                                NetworkType.MOBILE -> R.string.network_mobile
                                NetworkType.OTHER -> R.string.network_other
                                NetworkType.OFFLINE -> R.string.network_offline
                            })), style = MaterialTheme.typography.labelSmall, color = Muted,
                                modifier = Modifier.padding(top = 3.dp))
                        }
                        Text(stringResource(R.string.server_count, visible.size), style = MaterialTheme.typography.labelMedium, color = Muted)
                    }
                    OutlinedTextField(
                        value = search,
                        onValueChange = viewModel::setSearchQuery,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.search_servers)) },
                        leadingIcon = { Icon(Icons.Default.Search, null, tint = Muted) },
                        trailingIcon = if (search.isNotEmpty()) ({
                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                Icon(Icons.Default.Close, stringResource(R.string.clear_search), tint = Muted)
                            }
                        }) else null,
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = victoryTextFieldColors()
                    )
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = onlyFavorites,
                            onClick = { viewModel.setFavoritesOnly(!onlyFavorites) },
                            label = { Text(stringResource(R.string.favorites)) },
                            colors = victoryFilterChipColors(),
                            border = victoryFilterChipBorder(),
                            leadingIcon = { Icon(if (onlyFavorites) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null, Modifier.size(16.dp)) }
                        )
                        FilterChip(selected = protocol == null, onClick = { viewModel.setProtocolFilter(null) }, label = { Text(stringResource(R.string.all_protocols)) }, colors = victoryFilterChipColors(), border = victoryFilterChipBorder())
                        listOf(ProtocolType.VLESS, ProtocolType.VMESS, ProtocolType.TROJAN, ProtocolType.SS).forEach { item ->
                            FilterChip(selected = protocol == item, onClick = { viewModel.setProtocolFilter(item) }, label = { Text(item.name) }, colors = victoryFilterChipColors(), border = victoryFilterChipBorder())
                        }
                    }
                    SortSelector(sort, viewModel::setSortOrder)
                }
            }
            if (visible.isEmpty()) item(key = "empty") {
                EmptyCatalog(configs.isEmpty(), refreshing, onImport)
            }
            items(visible, key = { it.rawConfig }) { config ->
                Box(Modifier.animateItemPlacement(tween(250))) { ServerCard(
                    config = config,
                    favorite = config.rawConfig in favorites,
                    active = connected && config.rawConfig == activeRaw,
                    connecting = connecting && config.rawConfig == activeRaw,
                    onConnect = { if (config.rawConfig == activeRaw && (connected || connecting)) onDisconnect() else onConnect(config) },
                    onFavorite = { viewModel.toggleFavorite(config) },
                    onShare = { onShare(config) }
                ) }
            }
        }
    }
}

@Composable
private fun DashboardHeader(onLanguage: () -> Unit, onSources: () -> Unit, onImport: () -> Unit, onAbout: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val languageDescription = stringResource(R.string.change_language)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(46.dp).background(Brush.linearGradient(listOf(Accent, Mint)), RoundedCornerShape(15.dp)),
            contentAlignment = Alignment.Center
        ) { Text("V", color = Navy, fontWeight = FontWeight.Black, fontSize = 28.sp) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Vic2Ray", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleLarge, letterSpacing = 0.5.sp)
            Text(stringResource(R.string.tagline), color = Muted, style = MaterialTheme.typography.labelSmall)
        }
        TextButton(onClick = onLanguage, colors = victoryTextButtonColors(), modifier = Modifier.semantics { contentDescription = languageDescription }) {
            Text(stringResource(R.string.language), style = MaterialTheme.typography.labelMedium)
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.more_options), tint = Muted) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.sources)) }, onClick = { menu = false; onSources() }, leadingIcon = { Icon(Icons.Default.Settings, null) })
                DropdownMenuItem(text = { Text(stringResource(R.string.import_title)) }, onClick = { menu = false; onImport() }, leadingIcon = { Icon(Icons.Default.Add, null) })
                DropdownMenuItem(text = { Text(stringResource(R.string.about)) }, onClick = { menu = false; onAbout() }, leadingIcon = { Icon(Icons.Default.Info, null) })
            }
        }
    }
}

@Composable
private fun ConnectionHero(
    connected: Boolean,
    connecting: Boolean,
    serverName: String?,
    ping: Int,
    connectedSince: Long,
    fastest: VpnConfig?,
    onPower: () -> Unit
) {
    val accent by animateColorAsState(if (connected) Mint else Accent, tween(500))
    val title = stringResource(when { connecting -> R.string.connecting; connected -> R.string.connected; else -> R.string.disconnected })
    val action = stringResource(when { connecting -> R.string.cancel_connection; connected -> R.string.disconnect; else -> R.string.quick_connect })
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Panel, contentColor = MainText),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.16f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.045f), Color.Transparent))).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).background(accent, CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(title, color = accent, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(4.dp))
            PowerOrb(
                color = accent,
                active = connected,
                connecting = connecting,
                enabled = connected || connecting || fastest != null,
                description = action,
                status = title,
                onClick = onPower
            )
            Text(
                if (connected || connecting) serverName ?: stringResource(R.string.selected_server) else stringResource(R.string.quick_connect),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(when { connecting -> R.string.connecting_hint; connected -> R.string.connected_hint; fastest != null -> R.string.quick_connect_hint; else -> R.string.discover_first }),
                color = Muted,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
            AnimatedVisibility(visible = connected, enter = fadeIn(), exit = fadeOut()) {
                Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    HeroMetric(stringResource(R.string.latency), when {
                        ping == -2 -> stringResource(R.string.latency_pending)
                        ping > 0 -> stringResource(R.string.latency_ms, ping)
                        else -> stringResource(R.string.latency_unavailable)
                    }, Mint)
                    HeroMetric(stringResource(R.string.uptime), connectionDuration(connectedSince), MainText)
                }
            }
            if (connected || connecting) {
                TextButton(onClick = onPower, colors = victoryTextButtonColors(), modifier = Modifier.padding(top = 6.dp)) {
                    Text(action, color = if (connecting) Accent else MaterialTheme.colorScheme.error)
                }
            } else if (fastest != null) {
                Spacer(Modifier.height(14.dp))
                Text(
                    "${fastest.name}  ·  ${stringResource(R.string.latency_ms, fastest.ping)}",
                    color = accent,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun PowerOrb(color: Color, active: Boolean, connecting: Boolean, enabled: Boolean, description: String, status: String, onClick: () -> Unit) {
    val transition = rememberInfiniteTransition()
    val phase by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse))
    val turn by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(2400, easing = androidx.compose.animation.core.LinearEasing)))
    val activation by animateFloatAsState(if (active || connecting) 1f else 0f, tween(600))
    Box(
        Modifier.padding(vertical = 6.dp).size(164.dp).clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = description, onClick = onClick)
            .semantics { contentDescription = description; stateDescription = status },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val centerPoint = center
            val radius = size.minDimension / 2
            val pulse = if (active || connecting) phase else 0.3f
            drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.12f + activation * 0.05f), Color.Transparent)), radius)
            drawCircle(color.copy(alpha = 0.08f + pulse * 0.05f), radius * (0.9f + pulse * 0.04f), style = Stroke(1.dp.toPx()))
            drawCircle(color.copy(alpha = 0.17f), radius * 0.78f, style = Stroke(1.dp.toPx()))
            drawCircle(Brush.linearGradient(listOf(color.copy(alpha = 0.14f), color.copy(alpha = 0.035f))), radius * 0.65f)
            drawCircle(color.copy(alpha = if (enabled) 0.75f else 0.22f), radius * 0.65f, style = Stroke(1.5.dp.toPx()))
            if (connecting) {
                drawArc(color, turn, 95f, false, Offset(radius * 0.22f, radius * 0.22f), Size(radius * 1.56f, radius * 1.56f), style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
            }
            val glyphColor = if (enabled) color else DisabledText
            val glyphRadius = radius * 0.24f
            drawArc(glyphColor, -48f, 276f, false, Offset(centerPoint.x - glyphRadius, centerPoint.y - glyphRadius), Size(glyphRadius * 2, glyphRadius * 2), style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
            drawLine(glyphColor, Offset(centerPoint.x, centerPoint.y - radius * 0.32f), Offset(centerPoint.x, centerPoint.y - radius * 0.04f), 4.dp.toPx(), StrokeCap.Round)
        }
    }
}

@Composable
private fun HeroMetric(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(max = 150.dp).padding(horizontal = 4.dp)) {
        Text(value, color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(3.dp))
        Text(label, color = Muted, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
    }
}

@Composable
private fun connectionDuration(since: Long): String {
    val seconds by produceState(initialValue = 0L, key1 = since) {
        while (since > 0L) {
            value = ((System.currentTimeMillis() - since) / 1000).coerceAtLeast(0L)
            delay(1000)
        }
    }
    val locale = LocalContext.current.resources.configuration.locales[0]
    return if (seconds >= 3600) String.format(locale, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
    else String.format(locale, "%02d:%02d", seconds / 60, seconds % 60)
}

@Composable
private fun TestFailureSummary(failures: Map<TestFailure, Int>) {
    val reasons = failures.entries.sortedByDescending { it.value }
        .map { "${testFailureLabel(it.key)}: ${it.value}" }.joinToString(" · ")
    Card(shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = PanelLight, contentColor = MainText)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.test_failure_summary), color = Warning,
                style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Text(reasons, color = Muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun RefreshProgress(state: UiState, onStop: () -> Unit) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = PanelLight, contentColor = MainText)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(if (state is UiState.Testing) R.string.testing_title else R.string.refresh_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    if (state is UiState.Testing) Text(stringResource(R.string.test_progress, state.testedCount, state.totalConfigs, state.currentWorking.size), style = MaterialTheme.typography.labelSmall, color = Muted, modifier = Modifier.padding(top = 4.dp))
                }
                TextButton(onClick = onStop, colors = victoryTextButtonColors()) { Text(stringResource(R.string.stop)) }
            }
            if (state is UiState.Testing && state.totalConfigs > 0) {
                LinearProgressIndicator(progress = (state.testedCount.toFloat() / state.totalConfigs).coerceIn(0f, 1f), modifier = Modifier.fillMaxWidth().clip(CircleShape), color = Mint, trackColor = Mint.copy(alpha = 0.1f))
            } else LinearProgressIndicator(modifier = Modifier.fillMaxWidth().clip(CircleShape), color = Accent, trackColor = Accent.copy(alpha = 0.1f))
        }
    }
}

@Composable
private fun MessageCard(title: String, message: String, isError: Boolean = false, action: String? = null, onAction: () -> Unit = {}) {
    val color = if (isError) MaterialTheme.colorScheme.error else Accent
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isError) MaterialTheme.colorScheme.errorContainer else AccentPanel,
            contentColor = MainText
        ),
        border = BorderStroke(1.dp, color.copy(alpha = 0.5f))
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (isError) Icons.Default.Warning else Icons.Default.Info, null, tint = color, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, color = color, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            Text(message, color = Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            if (action != null) TextButton(onClick = onAction, colors = victoryTextButtonColors()) { Text(action, color = color) }
        }
    }
}

@Composable
private fun SortSelector(sort: ServerSort, onSort: (ServerSort) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }, colors = victoryTextButtonColors(), contentPadding = PaddingValues(horizontal = 0.dp), modifier = Modifier.heightIn(min = 48.dp)) {
            Icon(Icons.Default.List, null, Modifier.size(18.dp), tint = Muted)
            Spacer(Modifier.width(7.dp))
            Text(stringResource(if (sort == ServerSort.LATENCY) R.string.sort_latency else R.string.sort_name), color = Muted, style = MaterialTheme.typography.labelMedium)
            Icon(Icons.Default.ArrowDropDown, stringResource(R.string.sort_servers), tint = Muted)
        }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            ServerSort.values().forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(if (option == ServerSort.LATENCY) R.string.sort_latency else R.string.sort_name)) },
                    onClick = { onSort(option); expanded = false },
                    trailingIcon = if (sort == option) ({ Icon(Icons.Default.Check, null, tint = Accent) }) else null
                )
            }
        }
    }
}

@Composable
private fun EmptyCatalog(noSaved: Boolean, refreshing: Boolean, onImport: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(64.dp).background(PanelLight, RoundedCornerShape(22.dp)), contentAlignment = Alignment.Center) {
            Icon(if (noSaved) Icons.Default.Add else Icons.Default.Search, null, tint = Accent, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text(stringResource(if (noSaved) R.string.no_servers_title else R.string.no_results_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(if (noSaved) R.string.no_servers_hint else R.string.no_results_hint), color = Muted, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        if (noSaved && !refreshing) TextButton(onClick = onImport, colors = victoryTextButtonColors(), modifier = Modifier.padding(top = 8.dp)) { Text(stringResource(R.string.import_title)) }
    }
}

@Composable
private fun ServerCard(config: VpnConfig, favorite: Boolean, active: Boolean, connecting: Boolean, onConnect: () -> Unit, onFavorite: () -> Unit, onShare: () -> Unit) {
    val accent = when { active -> Mint; connecting -> Accent; else -> Muted }
    val latencyColor = when { !config.isWorking || config.ping <= 0 -> Muted; config.ping < 300 -> Mint; config.ping < 700 -> Warning; else -> MaterialTheme.colorScheme.error }
    val connectDescription = stringResource(if (active || connecting) R.string.disconnect else R.string.server_connect, config.name)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = if (active) ConnectedPanel else Panel, contentColor = MainText),
        border = BorderStroke(1.dp, if (active || connecting) accent.copy(alpha = 0.45f) else PanelLight),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = connectDescription, onClick = onConnect).padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(40.dp).background(accent.copy(alpha = 0.09f), RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
                    Icon(if (active) Icons.Default.Check else if (connecting) Icons.Default.Refresh else Icons.Default.PlayArrow, null, tint = if (active || connecting) accent else Accent, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(config.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(5.dp))
                    Text(config.protocol.name, color = Accent, style = MaterialTheme.typography.labelSmall, letterSpacing = 0.8.sp)
                }
                IconButton(onClick = onFavorite) {
                    Icon(if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, stringResource(if (favorite) R.string.favorite_remove else R.string.favorite_add, config.name), tint = if (favorite) Accent else Muted, modifier = Modifier.size(21.dp))
                }
            }
            Row(Modifier.fillMaxWidth().padding(start = 68.dp, end = 8.dp, bottom = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(when {
                        config.isWorking && config.ping > 0 -> stringResource(R.string.latency_ms, config.ping)
                        config.lastTestFailure != null -> testFailureLabel(config.lastTestFailure)
                        else -> stringResource(R.string.not_tested)
                    }, color = latencyColor, style = MaterialTheme.typography.labelMedium)
                    if (active || connecting) Text(stringResource(if (active) R.string.active else R.string.connecting), color = accent, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = onShare) { Icon(Icons.Default.Share, stringResource(R.string.share_server, config.name), tint = Muted, modifier = Modifier.size(18.dp)) }
            }
        }
    }
}
