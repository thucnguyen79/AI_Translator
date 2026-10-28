package io.github.thucnguyen79.aitranslator.spike.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.thucnguyen79.aitranslator.spike.Engines
import io.github.thucnguyen79.aitranslator.spike.SpikeApp
import io.github.thucnguyen79.aitranslator.spike.asr.AsrCatalog
import io.github.thucnguyen79.aitranslator.spike.asr.AsrModel
import io.github.thucnguyen79.aitranslator.spike.audio.AudioCapture
import io.github.thucnguyen79.aitranslator.spike.bench.AsrBench
import io.github.thucnguyen79.aitranslator.spike.core.Lang
import kotlinx.coroutines.sync.withLock

private object AsrUi {
    var model by mutableStateOf<AsrModel?>(null)
    var lang by mutableStateOf(Lang.VI)
    var threads by mutableStateOf(2)
    val bench = TaskState()
    val live = TaskState()
    @Volatile var stopLive = false
}

@Composable
fun AsrScreen() {
    val context = LocalContext.current
    val paths = SpikeApp.paths
    val installed = AsrCatalog.models.filter { it.isPresent(paths) }
    if (installed.isEmpty()) {
        Section("Chưa có mô hình") { Mono("Chạy: python tools/spike.py models --asr ... rồi push lên máy (xem spike/README.md)") }
        return
    }
    val model = AsrUi.model?.takeIf { it in installed } ?: installed.first()
    val lang = AsrUi.lang.takeIf { it in model.langs } ?: model.langs.first()

    Section("Cấu hình") {
        ChipRow(installed, model, { it.id }) { AsrUi.model = it }
        ChipRow(model.langs, lang, { it.code }) { AsrUi.lang = it }
        ChipRow(listOf(1, 2, 4), AsrUi.threads, { "$it luồng" }) { AsrUi.threads = it }
    }

    Section("Đo trên bộ ghi âm (${AsrBench.audioFiles(paths, lang).size} file ${lang.code})") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = !AsrUi.bench.running, onClick = {
                AsrUi.bench.start {
                    val r = Engines.lock.withLock {
                        AsrBench.run(context, paths, model, lang, AsrUi.threads) { done, total, line ->
                            progress = done / total.toFloat(); status = line
                        }
                    }
                    result = r.summary + "\n→ ${r.file.name}"
                }
            }) { Text("Chạy đo") }
            if (AsrUi.bench.running) OutlinedButton(onClick = { AsrUi.bench.cancel() }) { Text("Huỷ") }
        }
        TaskView(AsrUi.bench)
    }

    Section("Thử nhanh bằng micro (tự dừng khi im lặng)") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = !AsrUi.live.running, onClick = {
                AsrUi.stopLive = false
                AsrUi.live.start {
                    Engines.lock.withLock {
                        val asr = Engines.asr(model, AsrUi.threads)
                        status = "Đang nghe… nói một câu ${lang.label}"
                        val rec = AudioCapture.record(Engines.vad(), { AsrUi.stopLive })
                        status = "Đang nhận dạng ${"%.1f".format(rec.seconds)} s âm thanh…"
                        val t0 = System.nanoTime()
                        val text = asr.transcribe(rec.speech, lang)
                        val ms = (System.nanoTime() - t0) / 1_000_000
                        status = ""
                        result = "$text\n(${"%.1f".format(rec.seconds)} s âm thanh · nhận dạng $ms ms · ${model.id})"
                    }
                }
            }) { Text("🎤 Nói") }
            if (AsrUi.live.running) OutlinedButton(onClick = { AsrUi.stopLive = true }) { Text("Dừng") }
        }
        TaskView(AsrUi.live)
    }
}
