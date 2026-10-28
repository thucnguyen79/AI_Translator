package io.github.thucnguyen79.aitranslator.spike.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.thucnguyen79.aitranslator.spike.Engines
import io.github.thucnguyen79.aitranslator.spike.SpikeApp
import io.github.thucnguyen79.aitranslator.spike.bench.MtBench
import io.github.thucnguyen79.aitranslator.spike.core.Lang
import io.github.thucnguyen79.aitranslator.spike.mt.MtEngineId
import io.github.thucnguyen79.aitranslator.spike.mt.MtEngines
import kotlinx.coroutines.sync.withLock

private object MtUi {
    var engine by mutableStateOf(MtEngines.available().first())
    var directions by mutableStateOf(MtBench.defaultDirections.toSet())
    var from by mutableStateOf(Lang.VI)
    var to by mutableStateOf(Lang.EN)
    var text by mutableStateOf("Cho tôi xem thực đơn được không?")
    val bench = TaskState()
    val quick = TaskState()
}

@Composable
fun MtScreen() {
    val context = LocalContext.current
    val paths = SpikeApp.paths

    Section("Bộ dịch") {
        ChipRow(MtEngines.available(), MtUi.engine, MtEngineId::label) { MtUi.engine = it }
        if (MtUi.engine == MtEngineId.MLKIT) Mono("ML Kit cần mạng ở lần đầu để tải mô hình (~30 MB/ngôn ngữ).")
    }

    Section("Đo trên bộ câu thử") {
        MultiChipRow(MtBench.defaultDirections, MtUi.directions, { "${it.first.code}→${it.second.code}" }) { d ->
            MtUi.directions = if (d in MtUi.directions) MtUi.directions - d else MtUi.directions + d
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = !MtUi.bench.running && MtUi.directions.isNotEmpty(), onClick = {
                val dirs = MtBench.defaultDirections.filter { it in MtUi.directions }
                MtUi.bench.start {
                    val r = Engines.lock.withLock {
                        MtBench.run(context, paths, MtUi.engine, dirs) { done, total, line ->
                            progress = done / total.toFloat(); status = line
                        }
                    }
                    result = r.summary + "\n→ ${r.file.name}"
                }
            }) { Text("Chạy đo") }
            if (MtUi.bench.running) OutlinedButton(onClick = { MtUi.bench.cancel() }) { Text("Huỷ") }
        }
        TaskView(MtUi.bench)
    }

    Section("Dịch thử") {
        ChipRow(Lang.entries, MtUi.from, { "từ ${it.code}" }) { MtUi.from = it }
        ChipRow(Lang.entries.filter { it != MtUi.from }, MtUi.to, { "sang ${it.code}" }) { MtUi.to = it }
        OutlinedTextField(MtUi.text, { MtUi.text = it }, Modifier.fillMaxWidth(), label = { Text("Câu nguồn") })
        Button(enabled = !MtUi.quick.running && MtUi.from != MtUi.to, onClick = {
            val (from, to, text) = Triple(MtUi.from, MtUi.to, MtUi.text)
            MtUi.quick.start {
                Engines.lock.withLock {
                    status = "Đang nạp mô hình…"
                    val engine = Engines.mt(MtUi.engine, listOf(from to to))
                    status = ""
                    val t0 = System.nanoTime()
                    val out = engine.translate(text, from, to)
                    val ms = (System.nanoTime() - t0) / 1_000_000
                    result = "$out\n($ms ms${if (engine.pivots(from, to)) " · qua tiếng Anh" else ""})"
                }
            }
        }) { Text("Dịch") }
        TaskView(MtUi.quick)
    }
}
