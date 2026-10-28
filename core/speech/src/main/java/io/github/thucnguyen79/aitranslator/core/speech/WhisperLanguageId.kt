package io.github.thucnguyen79.aitranslator.core.speech

import com.k2fsa.sherpa.onnx.SpokenLanguageIdentification
import com.k2fsa.sherpa.onnx.SpokenLanguageIdentificationConfig
import com.k2fsa.sherpa.onnx.SpokenLanguageIdentificationWhisperConfig
import io.github.thucnguyen79.aitranslator.core.model.Lang
import io.github.thucnguyen79.aitranslator.core.model.LanguageIdentifier
import io.github.thucnguyen79.aitranslator.core.model.Utterance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** Spoken language identification with Whisper tiny (int8) through sherpa-onnx: 99 languages, ~0.1–0.3 s. */
class WhisperLanguageId(dir: File, threads: Int = 2) : LanguageIdentifier, AutoCloseable {
    private val mutex = Mutex()
    private val lid = SpokenLanguageIdentification(
        assetManager = null,
        config = SpokenLanguageIdentificationConfig(
            whisper = SpokenLanguageIdentificationWhisperConfig(
                encoder = File(dir, "encoder.int8.onnx").absolutePath,
                decoder = File(dir, "decoder.int8.onnx").absolutePath,
            ),
            numThreads = threads,
        ),
    )

    override suspend fun identify(utterance: Utterance): Lang? = withContext(Dispatchers.Default) {
        mutex.withLock {
            val stream = lid.createStream()
            try {
                stream.acceptWaveform(utterance.samples, utterance.sampleRate)
                Lang.find(lid.compute(stream))
            } finally {
                stream.release()
            }
        }
    }

    override fun close() = lid.release()

    companion object {
        fun isPresent(dir: File) = File(dir, "encoder.int8.onnx").isFile && File(dir, "decoder.int8.onnx").isFile
    }
}
