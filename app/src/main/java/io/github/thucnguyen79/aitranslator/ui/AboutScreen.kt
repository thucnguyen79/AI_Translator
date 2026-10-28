package io.github.thucnguyen79.aitranslator.ui

import android.app.ActivityManager
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.thucnguyen79.aitranslator.AppContainer
import io.github.thucnguyen79.aitranslator.ConversationLayout
import io.github.thucnguyen79.aitranslator.R
import java.util.Locale

/** Settings, diagnostics, credits and licences (CC-BY-SA and FunASR require attribution). */
@Composable
fun AboutScreen(container: AppContainer) {
    val diagnostics by container.diagnostics.collectAsStateWithLifecycle()
    val layout by container.layout.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.about_tagline), fontSize = 14.sp)

        Text(stringResource(R.string.layout_title), style = MaterialTheme.typography.titleMedium)
        listOf(
            Triple(ConversationLayout.FACE_TO_FACE, R.string.layout_face, R.string.layout_face_desc),
            Triple(ConversationLayout.LIST, R.string.layout_list, R.string.layout_list_desc),
        ).forEach { (value, title, desc) ->
            Row(
                Modifier.selectable(selected = layout == value, onClick = { container.setLayout(value) }),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = layout == value, onClick = null)
                Column(Modifier.padding(start = 8.dp)) {
                    Text(stringResource(title))
                    Text(stringResource(desc), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.diagnostics_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.diagnostics_desc), fontSize = 13.sp)
            }
            Switch(diagnostics, container::setDiagnostics)
        }
        if (diagnostics) Diagnostics(container)

        Text(stringResource(R.string.models_licences), style = MaterialTheme.typography.titleMedium)
        container.packs.catalog.packs.filter { it.license.isNotEmpty() }.forEach { pack ->
            Text("• ${title(pack)}: ${pack.license}", fontSize = 14.sp)
        }
        Text(stringResource(R.string.libraries), style = MaterialTheme.typography.titleMedium)
        listOf(
            "sherpa-onnx (k2-fsa) — Apache-2.0",
            "Bergamot translator (Mozilla; Android fork by RTranslator) — MPL-2.0",
            "Marian NMT — MIT",
            "ONNX Runtime (Microsoft) — MIT",
            "vibert-capu (dragonSwing; ONNX: welcomyou) — CC-BY-SA-4.0",
            "Supertonic 3 (Supertone) — OpenRAIL-M",
            "Apache Commons Compress — Apache-2.0",
            "Jetpack Compose, AndroidX, Kotlin Coroutines — Apache-2.0",
        ).forEach { Text("• $it", fontSize = 14.sp) }
    }
}

@Composable
private fun Diagnostics(container: AppContainer) {
    val context = LocalContext.current
    val turns by container.engine.turns.collectAsStateWithLifecycle()
    val loadTimes by container.loadTimes.collectAsStateWithLifecycle()
    val mem = ActivityManager.MemoryInfo().also { context.getSystemService(ActivityManager::class.java).getMemoryInfo(it) }
    val spoken = turns.filterNot { it.typed }
    val notLoaded = stringResource(R.string.diag_not_loaded)
    val loadTitle = stringResource(R.string.diag_load)
    val session = stringResource(R.string.diag_session, turns.size, spoken.size, turns.size - spoken.size)

    val lines = buildList {
        val soc = if (Build.VERSION.SDK_INT >= 31) "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}" else Build.HARDWARE
        add("${Build.MANUFACTURER} ${Build.MODEL} · $soc")
        add("Android ${Build.VERSION.RELEASE} · ${Runtime.getRuntime().availableProcessors()} CPU · RAM %.1f GB".format(Locale.US, mem.totalMem / 1e9))
        add("")
        add(loadTitle)
        if (loadTimes.isEmpty()) add("  $notLoaded")
        loadTimes.forEach { (name, ms) -> add("  %-12s %6d ms".format(Locale.US, name, ms)) }
        add("")
        add(session)
        if (spoken.isNotEmpty()) {
            add("  audio avg   %6.1f s".format(Locale.US, spoken.map { it.timings.audioSec }.average()))
            add("  ASR avg     %6.0f ms · max %d ms".format(Locale.US, spoken.map { it.timings.asrMs }.average(), spoken.maxOf { it.timings.asrMs }))
        }
        if (turns.isNotEmpty()) {
            add("  MT avg      %6.0f ms · max %d ms".format(Locale.US, turns.map { it.timings.mtMs }.average(), turns.maxOf { it.timings.mtMs }))
        }
    }
    Text(lines.joinToString("\n"), fontFamily = FontFamily.Monospace, fontSize = 12.sp)
}
