package io.github.thucnguyen79.aitranslator.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.thucnguyen79.aitranslator.AppContainer
import io.github.thucnguyen79.aitranslator.core.packs.Pack
import io.github.thucnguyen79.aitranslator.core.packs.PackKind
import io.github.thucnguyen79.aitranslator.core.packs.PackState
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun PacksScreen(container: AppContainer) {
    val packs = container.packs
    val states by packs.states.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val visible = packs.catalog.packs.filter { it.kind != PackKind.SHARED }

    LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text("Gói ngôn ngữ", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 12.dp))
            Text(
                "Tải một lần, sau đó dịch hoàn toàn offline. Nên dùng Wi-Fi. Tiếng Anh, Trung, Hàn, Nhật dùng chung " +
                    "một mô hình nhận dạng giọng nói (~163 MB) tải kèm ở gói đầu tiên.",
                fontSize = 14.sp,
            )
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
                step = active?.takeIf { it.id != pack.id }?.let { "Đang tải phần dùng chung (${title(it)})" },
                downloadMb = packs.downloadSize(pack.id) / 1e6,
                onInstall = { packs.install(pack.id) },
                onCancel = { chain.forEach { packs.cancel(it.id) }; packs.cancel(pack.id) },
                onDelete = { scope.launch { packs.delete(pack.id); container.tts.invalidate() } },
            )
        }
    }
}

@Composable
private fun PackCard(
    pack: Pack,
    state: PackState,
    step: String?,
    downloadMb: Double,
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
                        Button(onClick = onInstall) { Text("Tải ${"%.0f".format(Locale.US, downloadMb)} MB") }
                    is PackState.Downloading, PackState.Installing, PackState.Queued -> OutlinedButton(onClick = onCancel) { Text("Huỷ") }
                    PackState.Installed -> OutlinedButton(onClick = onDelete) { Text("Xoá") }
                }
            }
            when (state) {
                is PackState.Downloading -> {
                    LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                    Text(
                        (step?.let { "$it · " } ?: "") + "%.0f / %.0f MB".format(Locale.US, state.bytes / 1e6, state.total / 1e6),
                        fontSize = 13.sp,
                    )
                }
                PackState.Installing -> {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("Đang giải nén…", fontSize = 13.sp)
                }
                is PackState.Failed -> Text("Lỗi: ${state.message}", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                PackState.Queued -> Text("Đang chờ gói khác tải xong…", fontSize = 13.sp)
                PackState.Installed -> Text("✓ Đã cài", fontSize = 13.sp)
                PackState.NotInstalled -> Unit
            }
        }
    }
}

internal fun title(pack: Pack): String = when (pack.kind) {
    PackKind.LANGUAGE -> pack.langs.single().nativeName
    PackKind.OPTIONAL -> "Giọng đọc dự phòng (Supertonic)"
    PackKind.SHARED -> if (pack.id == "core") "Thành phần cơ bản" else "Nhận dạng giọng nói đa ngôn ngữ"
}

private fun subtitle(pack: Pack): String = when (pack.kind) {
    PackKind.LANGUAGE -> "Nhận dạng giọng nói + dịch offline"
    PackKind.OPTIONAL -> "Đọc ${pack.langs.joinToString { it.code }} khi máy thiếu giọng offline"
    PackKind.SHARED -> ""
}
