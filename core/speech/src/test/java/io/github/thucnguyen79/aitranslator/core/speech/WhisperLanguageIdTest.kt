package io.github.thucnguyen79.aitranslator.core.speech

import io.github.thucnguyen79.aitranslator.core.model.Lang
import io.github.thucnguyen79.aitranslator.core.model.Utterance
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Runs the real model on sample speech when LID_TEST_DIR points at a folder with model/{encoder,decoder}.int8.onnx
 * and data/<lang><n>.wav (16 kHz mono PCM). Skipped otherwise, e.g. in CI.
 */
class WhisperLanguageIdTest {
    @Test fun aNarrowWinDoesNotTakeSomeoneElsesTurn() {
        val scores = mapOf(Lang.VI to 12.4f, Lang.ZH to 12.2f)
        assertEquals(Lang.ZH, WhisperLanguageId.choose(scores, expected = Lang.ZH))
        assertEquals(Lang.VI, WhisperLanguageId.choose(scores, expected = null))
        // A clear win does.
        assertEquals(Lang.VI, WhisperLanguageId.choose(mapOf(Lang.VI to 22.3f, Lang.ZH to 14.3f), expected = Lang.ZH))
    }

    @Test fun picksTheSpokenLanguageAmongThoseAtTheTable() = runBlocking {
        val root = System.getenv("LID_TEST_DIR")?.let(::File)
        assumeTrue(root != null && WhisperLanguageId.isPresent(File(root, "model")))
        val lid = WhisperLanguageId(File(root, "model"))
        var right = 0
        var total = 0
        for (wav in File(root, "data").listFiles { f -> f.extension == "wav" }!!.sorted()) {
            val expected = Lang.find(wav.nameWithoutExtension.trimEnd { it.isDigit() }) ?: continue
            val samples = readWav(wav) ?: continue
            for (other in Lang.entries.filter { it != expected }) {
                val got = lid.identify(Utterance(samples, 16_000, 0), listOf(expected, other))
                total++
                if (got == expected) right++ else println("${wav.name}: ${expected.code} vs ${other.code} -> ${got?.code}")
            }
        }
        println("language pairs right: $right/$total")
    }

    /** 16-bit mono 16 kHz PCM WAV, or null for anything else. */
    private fun readWav(file: File): FloatArray? {
        val b = ByteBuffer.wrap(file.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
        if (b.getShort(22).toInt() != 1 || b.getInt(24) != 16_000 || b.getShort(34).toInt() != 16) return null
        var pos = 12
        while (pos + 8 <= b.limit()) {
            val id = String(ByteArray(4) { b.get(pos + it) })
            val size = b.getInt(pos + 4)
            if (id == "data") return FloatArray(minOf(size, b.limit() - pos - 8) / 2) { b.getShort(pos + 8 + 2 * it) / 32768f }
            pos += 8 + size
        }
        return null
    }
}
