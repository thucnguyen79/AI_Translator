package io.github.thucnguyen79.aitranslator.spike.bench

import android.content.Context
import com.k2fsa.sherpa.onnx.WaveReader
import io.github.thucnguyen79.aitranslator.spike.asr.AsrModel
import io.github.thucnguyen79.aitranslator.spike.asr.SherpaAsr
import io.github.thucnguyen79.aitranslator.spike.audio.SAMPLE_RATE
import io.github.thucnguyen79.aitranslator.spike.core.ChrF
import io.github.thucnguyen79.aitranslator.spike.core.DeviceInfo
import io.github.thucnguyen79.aitranslator.spike.core.Lang
import io.github.thucnguyen79.aitranslator.spike.core.Metrics
import io.github.thucnguyen79.aitranslator.spike.core.Sentences
import io.github.thucnguyen79.aitranslator.spike.core.SpikePaths
import io.github.thucnguyen79.aitranslator.spike.mt.MtEngineId
import io.github.thucnguyen79.aitranslator.spike.mt.MtEngines
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

class BenchResult(val summary: String, val file: File)

private fun Double.f(digits: Int = 2) = "%.${digits}f".format(Locale.US, this)

/** ASR accuracy and speed over the recorded test set in testset/audio/<lang>/<speaker>/<id>.wav. */
object AsrBench {
    fun audioFiles(paths: SpikePaths, lang: Lang): List<File> =
        File(paths.audio, lang.code).walkTopDown().filter { it.isFile && it.extension == "wav" }.sortedBy { it.path }.toList()

    fun run(
        context: Context,
        paths: SpikePaths,
        model: AsrModel,
        lang: Lang,
        threads: Int,
        progress: (done: Int, total: Int, line: String) -> Unit,
    ): BenchResult {
        val files = audioFiles(paths, lang)
        require(files.isNotEmpty()) { "Chưa có file ghi âm cho ${lang.code} (dùng tab Ghi âm hoặc adb push)" }
        val refs = Sentences.byId(context, paths)

        val pssBefore = DeviceInfo.pssMb()
        SherpaAsr(model, paths, threads).use { asr ->
            val pssAfter = DeviceInfo.pssMb()
            // Warm-up run so the first measured file does not pay one-off initialisation costs.
            asr.transcribe(WaveReader.readWave(files.first().absolutePath).samples, lang)

            val items = JSONArray()
            var charErr = 0; var charRef = 0; var wordErr = 0; var wordRef = 0
            val decodeMs = ArrayList<Double>(); val rtfs = ArrayList<Double>()
            files.forEachIndexed { i, f ->
                val wave = WaveReader.readWave(f.absolutePath)
                val ref = refs[f.nameWithoutExtension]?.get(lang).orEmpty()
                if (wave.sampleRate != SAMPLE_RATE) {
                    progress(i + 1, files.size, "Bỏ qua ${f.name}: ${wave.sampleRate} Hz")
                    return@forEachIndexed
                }
                val t0 = System.nanoTime()
                val hyp = asr.transcribe(wave.samples, lang)
                val ms = (System.nanoTime() - t0) / 1e6
                val sec = wave.samples.size / SAMPLE_RATE.toDouble()
                val (ce, cr) = Metrics.charErrors(ref, hyp)
                val (we, wr) = Metrics.wordErrors(ref, hyp)
                if (ref.isNotEmpty()) { charErr += ce; charRef += cr; wordErr += we; wordRef += wr }
                decodeMs += ms; rtfs += ms / 1000.0 / sec
                items.put(
                    JSONObject().put("file", f.relativeTo(paths.audio).path).put("speaker", f.parentFile?.name)
                        .put("id", f.nameWithoutExtension).put("ref", ref).put("hyp", hyp)
                        .put("audioSec", sec).put("decodeMs", ms).put("rtf", ms / 1000.0 / sec)
                        .put("cer", if (cr > 0) ce.toDouble() / cr else JSONObject.NULL),
                )
                progress(i + 1, files.size, "${f.nameWithoutExtension}: $hyp")
            }

            val cer = if (charRef > 0) charErr.toDouble() / charRef else 0.0
            val wer = if (wordRef > 0) wordErr.toDouble() / wordRef else 0.0
            val summary = JSONObject()
                .put("files", decodeMs.size)
                .put("cer", cer)
                .put("wer", if (lang.spaced) wer else JSONObject.NULL)
                .put("decodeMsMean", Metrics.mean(decodeMs))
                .put("decodeMsP95", Metrics.percentile(decodeMs, 95.0))
                .put("rtfMean", Metrics.mean(rtfs))
            val json = JSONObject()
                .put("type", "asr")
                .put("device", DeviceInfo.json(context))
                .put("model", model.id).put("modelDir", model.dir)
                .put("lang", lang.code).put("threads", threads)
                .put("loadMs", asr.loadMs).put("modelPssMb", pssAfter - pssBefore)
                .put("summary", summary).put("items", items)
            val file = DeviceInfo.writeResult(paths.results, "asr-${model.id}-${lang.code}-t$threads", json)
            val text = buildString {
                append("${model.id} · ${lang.code} · ${threads} luồng · ${decodeMs.size} câu\n")
                append("CER ${(cer * 100).f(1)}%")
                if (lang.spaced) append(" · WER ${(wer * 100).f(1)}%")
                append("\nGiải mã TB ${Metrics.mean(decodeMs).f(0)} ms · p95 ${Metrics.percentile(decodeMs, 95.0).f(0)} ms")
                append(" · RTF ${Metrics.mean(rtfs).f(3)}\n")
                append("Nạp mô hình ${asr.loadMs} ms · RAM +${(pssAfter - pssBefore).f(0)} MB")
            }
            return BenchResult(text, file)
        }
    }
}

/** Translation quality (chrF) and latency, one sentence at a time as in a real conversation. */
object MtBench {
    val defaultDirections = listOf(
        Lang.VI to Lang.EN, Lang.EN to Lang.VI,
        Lang.VI to Lang.ZH, Lang.ZH to Lang.VI,
        Lang.VI to Lang.KO, Lang.KO to Lang.VI,
        Lang.VI to Lang.JA, Lang.JA to Lang.VI,
    )

    suspend fun run(
        context: Context,
        paths: SpikePaths,
        engineId: MtEngineId,
        directions: List<Pair<Lang, Lang>>,
        progress: (done: Int, total: Int, line: String) -> Unit,
    ): BenchResult {
        val sentences = Sentences.all(context, paths)
        val total = sentences.size * directions.size
        var done = 0
        val pssBefore = DeviceInfo.pssMb()
        MtEngines.create(engineId, paths).use { engine ->
            val loadMs = engine.prepare(directions)
            val pssAfter = DeviceInfo.pssMb()
            val dirsJson = JSONArray()
            val lines = StringBuilder("${engineId.label} · nạp ${loadMs} ms · RAM +${(pssAfter - pssBefore).f(0)} MB\n")
            for ((from, to) in directions) {
                engine.translate(sentences.first()[from], from, to) // warm-up
                val chrf = ChrF()
                val ms = ArrayList<Double>()
                val items = JSONArray()
                for (s in sentences) {
                    val src = s[from]; val ref = s[to]
                    val t0 = System.nanoTime()
                    val hyp = engine.translate(src, from, to)
                    val t = (System.nanoTime() - t0) / 1e6
                    ms += t
                    chrf.add(hyp, ref)
                    items.put(JSONObject().put("id", s.id).put("src", src).put("ref", ref).put("hyp", hyp).put("ms", t))
                    progress(++done, total, "${from.code}→${to.code} ${s.id}: $hyp")
                }
                val score = chrf.score()
                dirsJson.put(
                    JSONObject().put("from", from.code).put("to", to.code).put("pivot", engine.pivots(from, to))
                        .put("summary", JSONObject().put("n", ms.size).put("chrF", score)
                            .put("msMean", Metrics.mean(ms)).put("msP95", Metrics.percentile(ms, 95.0)))
                        .put("items", items),
                )
                lines.append("${from.code}→${to.code}${if (engine.pivots(from, to)) " (qua en)" else ""}: ")
                    .append("chrF ${score.f(1)} · TB ${Metrics.mean(ms).f(0)} ms · p95 ${Metrics.percentile(ms, 95.0).f(0)} ms\n")
            }
            val json = JSONObject()
                .put("type", "mt")
                .put("device", DeviceInfo.json(context))
                .put("engine", engine.id).put("loadMs", loadMs).put("modelPssMb", pssAfter - pssBefore)
                .put("directions", dirsJson)
            val file = DeviceInfo.writeResult(paths.results, "mt-${engine.id}", json)
            return BenchResult(lines.toString().trimEnd(), file)
        }
    }
}
