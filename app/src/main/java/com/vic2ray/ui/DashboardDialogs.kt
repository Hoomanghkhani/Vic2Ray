package com.vic2ray.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vic2ray.BuildConfig
import com.vic2ray.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ImportServersDialog(
    busy: Boolean,
    message: String?,
    onDismiss: () -> Unit,
    onImport: (String) -> Unit,
    onOpenFile: () -> Unit,
    onMessage: (String) -> Unit
) {
    // User-supplied text may approach 1 MiB; keep it out of saved-state Binder transactions.
    var input by remember { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(26.dp),
        containerColor = Panel,
        title = { Text(stringResource(R.string.import_title), fontWeight = FontWeight.Bold) },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(stringResource(R.string.import_hint), style = MaterialTheme.typography.bodySmall, color = Muted)
                OutlinedTextField(
                    value = input,
                    onValueChange = {
                        if (it.length <= 1024 * 1024) input = it
                        else onMessage(context.getString(R.string.import_too_large))
                    },
                    label = { Text(stringResource(R.string.config_links)) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 130.dp, max = 220.dp),
                    textStyle = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr),
                    enabled = !busy,
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        enabled = !busy,
                        onClick = {
                            val text = clipboard.getText()?.text.orEmpty()
                            when {
                                text.isBlank() -> onMessage(context.getString(R.string.clipboard_empty))
                                text.length > 1024 * 1024 -> onMessage(context.getString(R.string.import_too_large))
                                else -> input = text
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text(stringResource(R.string.paste_clipboard)) }
                    OutlinedButton(
                        enabled = !busy,
                        onClick = onOpenFile,
                        modifier = Modifier.weight(1.4f),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text(stringResource(R.string.open_file)) }
                }
                if (busy) Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.import_reading), color = Muted, style = MaterialTheme.typography.labelSmall)
                }
                if (message != null) Text(message, color = Gold, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(onClick = { onImport(input) }, enabled = !busy && input.isNotBlank(), shape = RoundedCornerShape(12.dp)) {
                Text(stringResource(R.string.import_action))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.cancel)) } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourcesManagementDialog(mainViewModel: MainViewModel, onDismiss: () -> Unit) {
    val sources by mainViewModel.customSources.collectAsState()
    val failures by mainViewModel.sourceFailures.collectAsState()
    var url by rememberSaveable { mutableStateOf("") }
    var invalid by rememberSaveable { mutableStateOf(false) }
    var restore by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(26.dp),
        containerColor = Panel,
        title = { Text(stringResource(R.string.sources), fontWeight = FontWeight.Bold) },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(stringResource(R.string.sources_hint), color = Muted, style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.scan_limit), color = Gold.copy(alpha = 0.85f), style = MaterialTheme.typography.labelSmall)
                if (sources.isEmpty()) Text(stringResource(R.string.no_sources), color = Muted, style = MaterialTheme.typography.bodySmall)
                sources.forEach { source ->
                    Surface(shape = RoundedCornerShape(12.dp), color = PanelLight) {
                        Row(Modifier.fillMaxWidth().padding(start = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                                Text(
                                    source,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelSmall.copy(textDirection = TextDirection.Ltr),
                                    color = Color.White
                                )
                                if (source in failures) Text(stringResource(R.string.source_unreachable), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 5.dp))
                            }
                            IconButton(onClick = { mainViewModel.removeSource(source) }) {
                                Icon(Icons.Default.Delete, stringResource(R.string.remove_source), tint = Muted, modifier = Modifier.size(19.dp))
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it.take(4096); invalid = false },
                    label = { Text(stringResource(R.string.source_url)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    textStyle = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr),
                    isError = invalid,
                    supportingText = if (invalid) ({ Text(stringResource(R.string.source_invalid)) }) else null
                )
                OutlinedButton(
                    onClick = {
                        if (mainViewModel.addCustomSource(url.trim())) { url = ""; invalid = false } else invalid = true
                    },
                    enabled = url.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(7.dp))
                    Text(stringResource(R.string.add_source))
                }
                TextButton(onClick = { restore = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.restore_sources), textAlign = TextAlign.Center)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } }
    )
    if (restore) AlertDialog(
        onDismissRequest = { restore = false },
        containerColor = Panel,
        title = { Text(stringResource(R.string.restore_sources_title)) },
        text = { Text(stringResource(R.string.restore_sources_hint), color = Muted) },
        confirmButton = { TextButton(onClick = { mainViewModel.restoreDefaultSources(); restore = false }) { Text(stringResource(R.string.restore)) } },
        dismissButton = { TextButton(onClick = { restore = false }) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(26.dp),
        containerColor = Panel,
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.size(58.dp).background(Gold, RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
                    Text("V", fontWeight = FontWeight.Black, fontSize = 36.sp, color = Navy)
                }
                Spacer(Modifier.height(14.dp))
                Text("Vic2Ray", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.version, BuildConfig.VERSION_NAME), color = Gold, style = MaterialTheme.typography.labelMedium)
            }
        },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(stringResource(R.string.about_description), color = Muted, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                Surface(shape = RoundedCornerShape(14.dp), color = PanelLight, border = BorderStroke(1.dp, Muted.copy(alpha = 0.1f))) {
                    Text(stringResource(R.string.about_privacy), color = Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(14.dp))
                }
                OutlinedButton(
                    onClick = {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Hoomanghkhani/Vic2Ray"))) }
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Share, null, Modifier.size(17.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.project_github), textAlign = TextAlign.Center)
                }
                Text(stringResource(R.string.creator), color = Muted, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } }
    )
}
