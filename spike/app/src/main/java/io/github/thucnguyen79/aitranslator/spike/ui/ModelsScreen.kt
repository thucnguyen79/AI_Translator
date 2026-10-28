package io.github.thucnguyen79.aitranslator.spike.ui

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import io.github.thucnguyen79.aitranslator.spike.Engines
import io.github.thucnguyen79.aitranslator.spike.SpikeApp
import io.github.thucnguyen79.aitranslator.spike.asr.AsrCatalog
import io.github.thucnguyen79.aitranslator.spike.bench.AsrBench
import io.github.thucnguyen79.aitranslator.spike.core.DeviceInfo
import io.github.thucnguyen79.aitranslator.spike.core.Lang
import io.github.thucnguyen79.aitranslator.spike.core.Sentences
import io.github.thucnguyen79.aitranslator.spike.mt.BergamotNative

@Composable
fun ModelsScreen() {
    val context = LocalContext.current
    val paths = SpikeApp.paths
    var tick by remember { mutableIntStateOf(0) }
    tick // re-read the file system on refresh

    Section("Thiết bị") {
        Mono(DeviceInfo.summary(context))
        Mono("Thư mục dữ liệu:\n${paths.root.absolutePath}")
    }
    Section("Nhận dạng giọng nói (models/asr)") {
        Mono(
            AsrCatalog.models.joinToString("\n") { m ->
                "${if (m.isPresent(paths)) "✓" else "✗"} ${m.id} [${m.langs.joinToString(",") { it.code }}]"
            } + "\n${if (paths.vadModel.isFile) "✓" else "✗"} Silero VAD",
        )
    }
    Section("Dịch (models/bergamot)") {
        val dirs = paths.bergamotModels.listFiles().orEmpty().filter { it.isDirectory }.map { it.name }.sorted()
        Mono(
            "Thư viện Bergamot trong APK: ${if (BergamotNative.isLoaded) "có" else "không (chỉ ML Kit)"}\n" +
                "Cặp đã có: ${dirs.ifEmpty { listOf("—") }.joinToString(" ")}",
        )
    }
    Section("Bộ câu thử & ghi âm") {
        val n = Sentences.all(context, paths).size
        Mono(
            "$n câu${if (paths.sentencesOverride.isFile) " (từ testset/sentences.tsv)" else " (đóng gói trong app)"}\n" +
                Lang.entries.joinToString("\n") { "${it.code}: ${AsrBench.audioFiles(paths, it).size} file ghi âm" },
        )
    }
    Button(onClick = {
        Sentences.invalidate()
        Engines.releaseAll()
        tick++
    }) { Text("Làm mới & giải phóng mô hình") }
}
