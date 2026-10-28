package io.github.thucnguyen79.aitranslator.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Build
import android.os.StatFs
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.thucnguyen79.aitranslator.AppContainer
import io.github.thucnguyen79.aitranslator.R
import io.github.thucnguyen79.aitranslator.core.packs.Pack
import io.github.thucnguyen79.aitranslator.core.packs.PackKind
import io.github.thucnguyen79.aitranslator.core.packs.PackState
import kotlinx.coroutines.launch

private sealed interface InstallPrompt {
    data class Metered(val pack: Pack, val mb: Int) : InstallPrompt
    data class NoSpace(val neededMb: Int, val freeMb: Int) : InstallPrompt
}

@Composable
fun PacksScreen(container: AppContainer) {
    val context = LocalContext.current
    val packs = container.packs
    val states by packs.states.collectAsStateWithLifecycle()
    val wifiOnly by container.wifiOnly.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val visible = packs.catalog.packs.filter { it.kind != PackKind.SHARED }
    var prompt by remember { mutableStateOf<InstallPrompt?>(null) }
    var pendingPack by remember { mutableStateOf<Pack?>(null) }
    // The progress notification needs this on Android 13+; downloads run either way.
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        pendingPack?.let { container.startDownloads(it.id) }
        pendingPack = null
    }

    fun install(pack: Pack) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (Build.VERSION.SDK_INT >= 33 && !granted) {
            pendingPack = pack
            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            container.startDownloads(pack.id)
        }
    }

    /** Checks storage, then asks before using mobile data when "Wi-Fi only" is on. */
    fun requestInstall(pack: Pack) {
        val download = packs.downloadSize(pack.id)
        // Archives are extracted next to the download before it is deleted: keep room for both.
        val needed = download * 2 + 50_000_000
        val free = StatFs(context.filesDir.path).availableBytes
        prompt = when {
            free < needed -> InstallPrompt.NoSpace((needed / 1_000_000).toInt(), (free / 1_000_000).toInt())
            wifiOnly && isMetered(context) -> InstallPrompt.Metered(pack, (download / 1_000_000).toInt())
            else -> {
                install(pack)
                null
            }
        }
    }

    when (val p = prompt) {
        is InstallPrompt.Metered -> AlertDialog(
            onDismissRequest = { prompt = null },
            title = { Text(stringResource(R.string.packs_metered_title)) },
            text = { Text(stringResource(R.string.packs_metered_message, p.mb)) },
            confirmButton = {
                TextButton(onClick = {
                    prompt = null
                    install(p.pack)
                }) { Text(stringResource(R.string.packs_download_anyway)) }
            },
            dismissButton = { TextButton(onClick = { prompt = null }) { Text(stringResource(R.string.cancel)) } },
        )
        is InstallPrompt.NoSpace -> AlertDialog(
            onDismissRequest = { prompt = null },
            text = { Text(stringResource(R.string.packs_no_space, p.neededMb, p.freeMb)) },
            confirmButton = { TextButton(onClick = { prompt = null }) { Text("OK") } },
        )
        null -> Unit
    }

    LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text(stringResource(R.string.packs_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 12.dp))
            Text(stringResource(R.string.packs_intro), fontSize = 14.sp)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.packs_wifi_only), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.packs_wifi_only_desc), fontSize = 13.sp)
                }
                Switch(wifiOnly, container::setWifiOnly)
            }
        }
        items(visible, key = { it.id }) { pack ->
            // While this pack is requested, show the step in progress: it first installs the shared packs it
            // needs. A pack that was not requested ignores requirements another pack is installing.
            val chain = packs.catalog.withRequirements(pack.id)
            val own = states.getValue(pack.id)
            val active = chain.firstOrNull { states[it.id] is PackState.Downloading || states[it.id] == PackState.Installing }
            val state = when (own) {
                // A deleted requirement makes the language unusable again.
                PackState.Installed -> if (chain.all { states[it.id] == PackState.Installed }) own else PackState.NotInstalled
                PackState.NotInstalled, is PackState.Failed -> own
                else -> active?.let { states.getValue(it.id) } ?: own
            }
            PackCard(
                pack = pack,
                state = state,
                step = active?.takeIf { it.id != pack.id }?.let { stringResource(R.string.pack_shared_step, title(it)) },
                downloadMb = (packs.downloadSize(pack.id) / 1_000_000).toInt(),
                onInstall = { requestInstall(pack) },
                onCancel = { chain.forEach { packs.cancel(it.id) }; packs.cancel(pack.id) },
                onDelete = { scope.launch { packs.delete(pack.id); container.refreshVoices() } },
            )
        }
    }
}

private fun isMetered(context: Context): Boolean =
    context.getSystemService(ConnectivityManager::class.java).isActiveNetworkMetered

@Composable
private fun PackCard(
    pack: Pack,
    state: PackState,
    step: String?,
    downloadMb: Int,
    onInstall: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title(pack), style = MaterialTheme.typography.titleMedium)
                    Text(subtitle(pack), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                when (state) {
                    PackState.NotInstalled, is PackState.Failed ->
                        Button(onClick = onInstall) { Text(stringResource(R.string.pack_download, downloadMb)) }
                    is PackState.Downloading, PackState.Installing, PackState.Queued ->
                        OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
                    PackState.Installed -> OutlinedButton(onClick = onDelete) { Text(stringResource(R.string.delete)) }
                }
            }
            when (state) {
                is PackState.Downloading -> {
                    LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                    val progress = stringResource(R.string.pack_progress, (state.bytes / 1_000_000).toInt(), (state.total / 1_000_000).toInt())
                    Text((step?.let { "$it · " } ?: "") + progress, fontSize = 13.sp)
                }
                PackState.Installing -> {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(stringResource(R.string.pack_extracting), fontSize = 13.sp)
                }
                is PackState.Failed -> Text(stringResource(R.string.pack_error, state.message), color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                PackState.Queued -> Text(stringResource(R.string.pack_queued), fontSize = 13.sp)
                PackState.Installed -> Text(stringResource(R.string.pack_installed), fontSize = 13.sp)
                PackState.NotInstalled -> Unit
            }
        }
    }
}

@Composable
internal fun title(pack: Pack): String = when (pack.kind) {
    PackKind.LANGUAGE -> pack.langs.single().nativeName
    PackKind.OPTIONAL -> stringResource(R.string.pack_supertonic)
    PackKind.SHARED -> stringResource(if (pack.id == "core") R.string.pack_core else R.string.pack_speech_multi)
}

@Composable
private fun subtitle(pack: Pack): String = when (pack.kind) {
    PackKind.LANGUAGE -> stringResource(R.string.pack_language_subtitle)
    PackKind.OPTIONAL -> stringResource(R.string.pack_supertonic_subtitle, pack.langs.joinToString { it.nativeName })
    PackKind.SHARED -> ""
}
