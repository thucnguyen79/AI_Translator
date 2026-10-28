package io.github.thucnguyen79.aitranslator.ui

import android.app.ActivityManager
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.thucnguyen79.aitranslator.AppContainer
import java.util.Locale

/** Diagnostics, credits and licences (CC-BY-SA and FunASR require attribution). */
@Composable
fun AboutScreen(container: AppContainer) {
    val diagnostics by container.diagnostics.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("AI Translator", style = MaterialTheme.typography.headlineSmall)
        Text("Dịch hội thoại offline cho người du lịch. Âm thanh và văn bản không rời khỏi máy.", fontSize = 14.sp)

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Chế độ chẩn đoán", style = MaterialTheme.typography.titleMedium)
                Text("Hiện thời gian nhận dạng và dịch dưới mỗi câu", fontSize = 13.sp)
            }
            Switch(diagnostics, container::setDiagnostics)
        }
        if (diagnostics) Diagnostics(container)

        Text("Mô hình & giấy phép", style = MaterialTheme.typography.titleMedium)
        container.packs.catalog.packs.filter { it.license.isNotEmpty() }.forEach { pack ->
            Text("• ${title(pack)}: ${pack.license}", fontSize = 14.sp)
        }
        Text("Thư viện", style = MaterialTheme.typography.titleMedium)
        listOf(
            "sherpa-onnx (k2-fsa) — Apache-2.0",
            "Bergamot translator (Mozilla, bản fork Android của RTranslator) — MPL-2.0",
            "Marian NMT — MIT",
            "ONNX Runtime (Microsoft) — MIT",
            "vibert-capu (dragonSwing; ONNX: welcomyou) — CC-BY-SA-4.0",
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

    val lines = buildList {
        val soc = if (Build.VERSION.SDK_INT >= 31) "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}" else Build.HARDWARE
        add("Máy: ${Build.MANUFACTURER} ${Build.MODEL} · $soc")
        add("Android ${Build.VERSION.RELEASE} · ${Runtime.getRuntime().availableProcessors()} nhân · RAM %.1f GB".format(Locale.US, mem.totalMem / 1e9))
        add("")
        add("Nạp mô hình (lần đầu):")
        if (loadTimes.isEmpty()) add("  chưa nạp — mở tab Hội thoại")
        loadTimes.forEach { (name, ms) -> add("  %-18s %6d ms".format(Locale.US, name, ms)) }
        add("")
        add("Phiên hiện tại: ${turns.size} câu (${spoken.size} nói, ${turns.size - spoken.size} gõ)")
        if (spoken.isNotEmpty()) {
            add("  âm thanh TB   %6.1f s".format(Locale.US, spoken.map { it.timings.audioSec }.average()))
            add("  nhận dạng TB  %6.0f ms · max %d ms".format(Locale.US, spoken.map { it.timings.asrMs }.average(), spoken.maxOf { it.timings.asrMs }))
        }
        if (turns.isNotEmpty()) {
            add("  dịch TB       %6.0f ms · max %d ms".format(Locale.US, turns.map { it.timings.mtMs }.average(), turns.maxOf { it.timings.mtMs }))
        }
    }
    Text(lines.joinToString("\n"), fontFamily = FontFamily.Monospace, fontSize = 12.sp)
}
