package com.vic2ray.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.view.ContextThemeWrapper
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vic2ray.R
import com.vic2ray.models.VpnConfig
import com.vic2ray.vpn.V2rayConfigGenerator
import com.vic2ray.vpn.VicVpnService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        setContent { VictoryTheme { Vic2rayApp() } }
    }
}

/** Localize only this activity's presentation; persist the choice in the catalog. */
@Composable
private fun AppLanguage(language: String, content: @Composable () -> Unit) {
    val baseContext = LocalContext.current
    val currentConfiguration = LocalConfiguration.current
    val configuration = remember(language, currentConfiguration) {
        Configuration(currentConfiguration).apply {
            val locale = Locale.forLanguageTag(language)
            setLocale(locale)
            setLayoutDirection(locale)
        }
    }
    val localizedContext = remember(baseContext, configuration) {
        ContextThemeWrapper(baseContext, R.style.Theme_Vic2ray).apply {
            applyOverrideConfiguration(configuration)
        }
    }
    CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalConfiguration provides configuration,
        LocalLayoutDirection provides if (language == "fa") LayoutDirection.Rtl else LayoutDirection.Ltr,
        content = content
    )
}

@Composable
fun Vic2rayApp(mainViewModel: MainViewModel = viewModel()) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(mainViewModel, lifecycleOwner) {
        val lifecycle = lifecycleOwner.lifecycle
        var started = false
        fun startDiscovery() {
            if (!started) {
                started = true
                mainViewModel.onAppStarted()
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> startDiscovery()
                Lifecycle.Event.ON_STOP -> {
                    started = false
                    mainViewModel.onAppStopped()
                }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) startDiscovery()
        onDispose {
            lifecycle.removeObserver(observer)
            mainViewModel.onAppStopped()
        }
    }
    val language by mainViewModel.language.collectAsState()
    AppLanguage(language) { DashboardHost(mainViewModel) }
}

@Composable
private fun DashboardHost(viewModel: MainViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var showImport by rememberSaveable { mutableStateOf(false) }
    var showSources by rememberSaveable { mutableStateOf(false) }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var importMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var localError by rememberSaveable { mutableStateOf<String?>(null) }
    // An ID survives recreation without putting large credentials in Android's saved-state Bundle.
    var pendingConfigId by rememberSaveable { mutableStateOf<String?>(null) }

    DisposableEffect(viewModel, showImport) {
        viewModel.setImportVisible(showImport)
        onDispose { if (showImport) viewModel.setImportVisible(false) }
    }

    fun startPendingVpn() {
        val config = viewModel.allConfigs.value.firstOrNull { it.id == pendingConfigId }
        if (config != null) {
            try {
                val json = V2rayConfigGenerator.generateJsonConfig(config.rawConfig, config.protocol)
                ContextCompat.startForegroundService(context, Intent(context, VicVpnService::class.java).apply {
                    action = VicVpnService.ACTION_CONNECT
                    putExtra(VicVpnService.EXTRA_CONFIG, json)
                    putExtra(VicVpnService.EXTRA_RAW_CONFIG, config.rawConfig)
                })
                localError = null
            } catch (error: Exception) {
                localError = context.getString(R.string.start_failed, error.localizedMessage.orEmpty())
            }
        } else if (pendingConfigId != null) localError = context.getString(R.string.pending_config_missing)
        pendingConfigId = null
    }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        // Notification denial does not remove the right to start a foreground VPN.
        startPendingVpn()
    }
    fun finishConsent() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else startPendingVpn()
    }
    val vpnConsent = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) finishConsent() else {
            pendingConfigId = null
            localError = context.getString(R.string.permission_denied)
        }
    }

    fun connect(config: VpnConfig) {
        try {
            viewModel.pauseDiscoveryForConnection()
            pendingConfigId = config.id
            localError = null
            val intent = VpnService.prepare(context)
            if (intent == null) finishConsent() else vpnConsent.launch(intent)
        } catch (error: Exception) {
            pendingConfigId = null
            localError = context.getString(R.string.invalid_config, error.localizedMessage.orEmpty())
        }
    }

    fun importText(text: String) {
        scope.launch {
            importing = true
            importMessage = null
            try {
                val result = withContext(Dispatchers.Default) { viewModel.importConfigs(text) }
                val message = when {
                    result > 0 -> context.getString(R.string.import_added, result)
                    result == -1 -> context.getString(R.string.import_too_large)
                    result == -2 -> context.getString(R.string.import_busy)
                    else -> context.getString(R.string.import_empty)
                }
                importing = false
                if (result > 0) {
                    showImport = false
                    snackbar.showSnackbar(message)
                } else importMessage = message
            } finally {
                importing = false
            }
        }
    }

    val openDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            importing = true
            importMessage = null
            try {
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        var total = 0
                        while (true) {
                            val count = stream.read(buffer)
                            if (count < 0) break
                            total += count
                            if (total > MAX_IMPORT_BYTES) throw ImportTooLargeException()
                            output.write(buffer, 0, count)
                        }
                        output.toString(Charsets.UTF_8.name()).removePrefix("\uFEFF")
                    } ?: error("No input stream")
                }
                importing = false
                importText(text)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                importing = false
                importMessage = context.getString(
                    if (error is ImportTooLargeException) R.string.import_too_large else R.string.import_failed
                )
            } finally {
                importing = false
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Dashboard(
            viewModel = viewModel,
            localError = localError,
            onConnect = ::connect,
            onDisconnect = {
                try {
                    context.startService(Intent(context, VicVpnService::class.java).apply {
                        action = VicVpnService.ACTION_DISCONNECT
                    })
                    localError = null
                } catch (error: Exception) {
                    localError = context.getString(R.string.start_failed, error.localizedMessage.orEmpty())
                }
            },
            onImport = { importMessage = null; showImport = true },
            onSources = { showSources = true },
            onAbout = { showAbout = true },
            onShare = { config ->
                try {
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, config.rawConfig)
                    }, context.getString(R.string.share_title)))
                } catch (_: Exception) {
                    scope.launch { snackbar.showSnackbar(context.getString(R.string.share_failed)) }
                }
            }
        )
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(12.dp)) { data ->
            Snackbar(
                snackbarData = data,
                containerColor = PanelLight,
                contentColor = MainText,
                actionColor = Accent,
                dismissActionContentColor = Muted
            )
        }
    }
    if (showImport) ImportServersDialog(
        busy = importing,
        message = importMessage,
        onDismiss = { if (!importing) showImport = false },
        onImport = ::importText,
        onOpenFile = { openDocument.launch(arrayOf("text/*", "application/octet-stream", "application/json")) },
        onMessage = { importMessage = it }
    )
    if (showSources) SourcesManagementDialog(viewModel) { showSources = false }
    if (showAbout) AboutDialog { showAbout = false }
}

private const val MAX_IMPORT_BYTES = 1024 * 1024
private class ImportTooLargeException : Exception()
