package io.github.thucnguyen79.aitranslator.spike.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import io.github.thucnguyen79.aitranslator.spike.Engines
import io.github.thucnguyen79.aitranslator.spike.SpikeApp
import io.github.thucnguyen79.aitranslator.spike.TtsRoute
import io.github.thucnguyen79.aitranslator.spike.asr.AsrCatalog
import io.github.thucnguyen79.aitranslator.spike.asr.ViPunctuator
import io.github.thucnguyen79.aitranslator.spike.audio.AudioCapture
import io.github.thucnguyen79.aitranslator.spike.core.DeviceInfo
import io.github.thucnguyen79.aitranslator.spike.core.Lang
import io.github.thucnguyen79.aitranslator.spike.mt.MtEngineId
import io.github.thucnguyen79.aitranslator.spike.mt.MtEngines
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File

/** One turn of the end-to-end demo with per-stage timings (ms). */
data class Turn(
    val from: Lang, val to: Lang, val source: String, val target: String,
    val audioSec: Double, val asrMs: Long, val mtMs: Long, val ttsStartMs: Long?, val tts: TtsRoute? = null,
    /** Part of [asrMs] spent restoring case and punctuation. */
    val postMs: Long = 0, val punct: String = "",
) {
    /** End of speech detected → translation on screen. */
    val toTextMs get() = asrMs + mtMs
    /** End of speech detected → translated audio starts. */
    val toAudioMs get() = ttsStartMs?.let { toTextMs + it }
}

private object ConvUi {
    var langA by mutableStateOf(Lang.VI)
    var langB by mutableStateOf(Lang.EN)
    var engine by mutableStateOf(MtEngines.available().first())
    var speak by mutableStateOf(true)
    var punctModel by mutableStateOf(true)
    val turns = mutableStateListOf<Turn>()
    val task = TaskState()
    @Volatile var stop = false
    val log: File by lazy { File(SpikeApp.paths.results, "conversation-${DeviceInfo.stamp()}.jsonl") }
}

@Composable
fun ConversationScreen() {
    val paths = SpikeApp.paths

    Section("Hai người, một máy") {
        ChipRow(Lang.entries, ConvUi.langA, { "A: ${it.code}" }) { ConvUi.langA = it }
        ChipRow(Lang.entries.filter { it != ConvUi.langA }, ConvUi.langB, { "B: ${it.code}" }) { ConvUi.langB = it }
        ChipRow(MtEngines.available(), ConvUi.engine, MtEngineId::label) { ConvUi.engine = it }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Switch(ConvUi.speak, { ConvUi.speak = it })
            Text("Đọc bản dịch")
        }
        if (ViPunctuator.isPresent(paths)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Switch(ConvUi.punctModel, { ConvUi.punctModel = it })
                Text("Dấu câu tiếng Việt bằng mô hình (tắt = quy tắc)")
            }
        }
        Mono(
            "ASR: A=${AsrCatalog.defaultFor(ConvUi.langA, paths)?.id ?: "chưa có"} · " +
                "B=${AsrCatalog.defaultFor(ConvUi.langB, paths)?.id ?: "chưa có"}",
        )
        val routes by produceState<Pair<TtsRoute, TtsRoute>?>(null, ConvUi.langA, ConvUi.langB) {
            value = runCatching { Engines.ttsRoute(ConvUi.langA) to Engines.ttsRoute(ConvUi.langB) }.getOrNull()
        }
        routes?.let { (a, b) ->
            Mono("Đọc: A=${a.label} · B=${b.label}")
            listOf(ConvUi.langA to a, ConvUi.langB to b).filter { !it.second.offline }.forEach { (lang, _) ->
                Text(
                    "⚠ Không có giọng đọc offline cho ${lang.label}: tải dữ liệu giọng ở tab TTS hoặc thêm mô hình Supertonic.",
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = !ConvUi.task.running, onClick = { turn(ConvUi.langA, ConvUi.langB) }) { Text("🎤 A nói") }
            Button(enabled = !ConvUi.task.running, onClick = { turn(ConvUi.langB, ConvUi.langA) }) { Text("🎤 B nói") }
            if (ConvUi.task.running) OutlinedButton(onClick = { ConvUi.stop = true; Engines.stopSpeaking() }) { Text("Dừng") }
        }
        TaskView(ConvUi.task)
    }

    ConvUi.turns.asReversed().forEach { t ->
        Section("${t.from.code} → ${t.to.code}") {
            Text(t.source, style = MaterialTheme.typography.bodyMedium)
            Text(t.target, style = MaterialTheme.typography.titleLarge)
            Mono(
                "âm thanh ${"%.1f".format(t.audioSec)} s · ASR ${t.asrMs} ms (dấu câu ${t.punct} ${t.postMs} ms) · dịch ${t.mtMs} ms" +
                    (t.ttsStartMs?.let { " · TTS ${t.tts?.label} ${it} ms" } ?: "") +
                    "\n→ chữ sau ${t.toTextMs} ms" + (t.toAudioMs?.let { " · tiếng sau $it ms" } ?: "") +
                    " (tính từ lúc VAD báo hết câu)",
            )
        }
    }
}

private fun turn(from: Lang, to: Lang) {
    val paths = SpikeApp.paths
    ConvUi.stop = false
    ConvUi.task.start {
        Engines.lock.withLock {
            val model = requireNotNull(AsrCatalog.defaultFor(from, paths)) { "Chưa có mô hình ASR cho ${from.code}" }
            status = "Đang nạp mô hình…"
            Engines.usePunctModel = ConvUi.punctModel
            val asr = Engines.asr(model)
            val punct = if (asr.punctuator != null) "mô hình" else "quy tắc"
            val mt = Engines.mt(ConvUi.engine, listOf(from to to))
            val vad = Engines.vad()

            status = "Đang nghe ${from.label}…"
            val rec = AudioCapture.record(vad, { ConvUi.stop })
            if (ConvUi.stop && !rec.endedByVad) { status = "Đã dừng"; return@withLock }

            status = "Đang nhận dạng…"
            val t1 = System.nanoTime()
            val text = asr.transcribe(rec.speech, from)
            val asrMs = (System.nanoTime() - t1) / 1_000_000
            if (text.isBlank()) { status = "Không nghe rõ, thử lại"; return@withLock }

            status = "Đang dịch…"
            val t2 = System.nanoTime()
            val out = mt.translate(text, from, to)
            val mtMs = (System.nanoTime() - t2) / 1_000_000

            // Show the translation first; Supertonic plays synchronously.
            var t = Turn(from, to, text, out, rec.seconds, asrMs, mtMs, null, postMs = asr.lastPostMs, punct = punct)
            ConvUi.turns += t
            if (ConvUi.speak) {
                status = "Đang đọc…"
                runCatching { Engines.speak(out, to) }.getOrNull()?.let { (route, ms) ->
                    t = t.copy(ttsStartMs = ms, tts = route)
                    ConvUi.turns[ConvUi.turns.lastIndex] = t
                }
            }
            status = ""
            ConvUi.log.appendText(
                JSONObject().put("from", from.code).put("to", to.code).put("asrModel", model.id).put("mt", mt.id)
                    .put("source", text).put("target", out).put("audioSec", rec.seconds)
                    .put("asrMs", asrMs).put("mtMs", mtMs).put("ttsStartMs", t.ttsStartMs ?: JSONObject.NULL)
                    .put("tts", t.tts?.name ?: JSONObject.NULL)
                    .put("punct", if (punct == "mô hình") "model" else "rules").put("postMs", t.postMs)
                    .put("endedByVad", rec.endedByVad).toString() + "\n",
            )
        }
    }
}
