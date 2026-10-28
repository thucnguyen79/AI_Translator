package io.github.thucnguyen79.aitranslator.spike.ui

import android.content.Intent
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.thucnguyen79.aitranslator.spike.Engines
import io.github.thucnguyen79.aitranslator.spike.SpikeApp
import io.github.thucnguyen79.aitranslator.spike.core.DeviceInfo
import io.github.thucnguyen79.aitranslator.spike.core.Lang
import io.github.thucnguyen79.aitranslator.spike.core.Sentences
import org.json.JSONArray
import org.json.JSONObject

private object TtsUi {
    val probe = TaskState()
}

@Composable
fun TtsScreen() {
    val context = LocalContext.current
    val paths = SpikeApp.paths

    Section("Giọng đọc offline của Android") {
        Mono("Kiểm tra từng ngôn ngữ có giọng offline không, đọc thử 1 câu và đo thời gian tới khi bắt đầu phát.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = !TtsUi.probe.running, onClick = {
                TtsUi.probe.start {
                    val tts = Engines.tts
                    val sample = Sentences.all(context, paths).first()
                    val rows = JSONArray()
                    val lines = StringBuilder()
                    Lang.entries.forEachIndexed { i, lang ->
                        status = "Đang thử ${lang.label}…"
                        val p = tts.probe(lang)
                        val startMs = if (p.availability >= TextToSpeech.LANG_AVAILABLE) {
                            runCatching { tts.speak(sample[lang], lang) }.getOrNull()
                        } else null
                        rows.put(
                            JSONObject().put("lang", lang.code).put("engine", p.engine).put("availability", p.availability)
                                .put("offlineVoices", JSONArray(p.offlineVoices)).put("networkVoices", JSONArray(p.networkVoices))
                                .put("timeToAudioMs", startMs ?: JSONObject.NULL),
                        )
                        lines.append("${if (p.usableOffline) "✓" else "✗"} ${lang.code}: ${p.offlineVoices.size} giọng offline")
                            .append(", ${p.networkVoices.size} cần mạng")
                            .append(startMs?.let { " · bắt đầu phát sau $it ms" } ?: " · không đọc được")
                            .append('\n')
                        progress = (i + 1) / Lang.entries.size.toFloat()
                    }
                    val json = JSONObject().put("type", "tts").put("device", DeviceInfo.json(context))
                        .put("engine", rows.optJSONObject(0)?.optString("engine")).put("langs", rows)
                    val file = DeviceInfo.writeResult(paths.results, "tts-android", json)
                    status = ""
                    result = lines.toString() + "→ ${file.name}"
                }
            }) { Text("Kiểm tra 5 ngôn ngữ") }
            if (TtsUi.probe.running) OutlinedButton(onClick = { TtsUi.probe.cancel(); Engines.tts.stop() }) { Text("Huỷ") }
        }
        TaskView(TtsUi.probe)
    }

    Section("Thiếu giọng offline?") {
        Mono("Cài thêm dữ liệu giọng trong cài đặt Text-to-speech của máy rồi kiểm tra lại.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                runCatching { context.startActivity(Intent("com.android.settings.TTS_SETTINGS")) }
            }) { Text("Cài đặt TTS") }
            OutlinedButton(onClick = {
                runCatching { context.startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)) }
            }) { Text("Tải dữ liệu giọng") }
        }
    }
}
