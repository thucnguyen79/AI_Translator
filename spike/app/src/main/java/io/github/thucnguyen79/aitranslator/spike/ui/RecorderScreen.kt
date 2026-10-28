package io.github.thucnguyen79.aitranslator.spike.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.thucnguyen79.aitranslator.spike.SpikeApp
import io.github.thucnguyen79.aitranslator.spike.audio.AudioCapture
import io.github.thucnguyen79.aitranslator.spike.audio.Wav
import io.github.thucnguyen79.aitranslator.spike.core.Lang
import io.github.thucnguyen79.aitranslator.spike.core.Sentences
import java.io.File

private object RecUi {
    var lang by mutableStateOf(Lang.VI)
    var speaker by mutableStateOf("s1")
    var index by mutableIntStateOf(0)
    var level by mutableStateOf(0f)
    val task = TaskState()
    @Volatile var stop = false
}

/** Builds the ASR test set: one WAV per sentence at testset/audio/<lang>/<speaker>/<id>.wav. */
@Composable
fun RecorderScreen() {
    val context = LocalContext.current
    val paths = SpikeApp.paths
    val sentences = Sentences.all(context, paths)
    val speaker = RecUi.speaker.trim().ifEmpty { "s1" }
    val dir = paths.audioDir(RecUi.lang, speaker)
    val index = RecUi.index.coerceIn(0, sentences.lastIndex)
    val s = sentences[index]
    val file = File(dir, "${s.id}.wav")
    val recorded = sentences.count { File(dir, "${it.id}.wav").isFile }

    Section("Người đọc & ngôn ngữ") {
        ChipRow(Lang.entries, RecUi.lang, { it.label }) { RecUi.lang = it }
        OutlinedTextField(
            RecUi.speaker, { RecUi.speaker = it.filter { c -> c.isLetterOrDigit() || c == '-' || c == '_' } },
            Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("Mã người đọc (vd: bac-nam-1, nam-nu-2)") },
        )
        Mono("Đã ghi $recorded/${sentences.size} câu · ${dir.relativeTo(paths.root).path}")
    }

    Section("Câu ${index + 1}/${sentences.size} · ${s.id} · ${s.category}${if (file.isFile) " · ✓ đã ghi" else ""}") {
        Text(s[RecUi.lang], style = MaterialTheme.typography.headlineSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!RecUi.task.running) {
                Button(onClick = {
                    RecUi.stop = false
                    RecUi.task.start {
                        status = "Đang ghi… bấm Dừng khi đọc xong"
                        val rec = AudioCapture.record(null, { RecUi.stop }, maxSeconds = 30.0) { RecUi.level = it }
                        Wav.write(file, rec.all)
                        status = ""
                        result = "Đã lưu ${file.name} (${"%.1f".format(rec.seconds)} s)"
                        if (RecUi.index < sentences.lastIndex) RecUi.index++
                    }
                }) { Text("🎤 Ghi") }
            } else {
                Button(onClick = { RecUi.stop = true }) { Text("■ Dừng & lưu") }
            }
            OutlinedButton(enabled = !RecUi.task.running && index > 0, onClick = { RecUi.index = index - 1 }) { Text("‹ Trước") }
            OutlinedButton(enabled = !RecUi.task.running && index < sentences.lastIndex, onClick = { RecUi.index = index + 1 }) { Text("Sau ›") }
        }
        if (RecUi.task.running) Mono("Mức âm: " + "▮".repeat((RecUi.level * 60).toInt().coerceIn(0, 30)))
        TaskView(RecUi.task)
    }
}
