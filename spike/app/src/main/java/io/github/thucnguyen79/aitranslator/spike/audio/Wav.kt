package io.github.thucnguyen79.aitranslator.spike.audio

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Writes mono 16-bit PCM WAV. Reading is done with sherpa-onnx's WaveReader. */
object Wav {
    fun write(file: File, samples: FloatArray, sampleRate: Int = SAMPLE_RATE) {
        file.parentFile?.mkdirs()
        val dataBytes = samples.size * 2
        val buf = ByteBuffer.allocate(44 + dataBytes).order(ByteOrder.LITTLE_ENDIAN)
        buf.put("RIFF".toByteArray()).putInt(36 + dataBytes).put("WAVE".toByteArray())
        buf.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1)
            .putInt(sampleRate).putInt(sampleRate * 2).putShort(2).putShort(16)
        buf.put("data".toByteArray()).putInt(dataBytes)
        for (s in samples) buf.putShort((s.coerceIn(-1f, 1f) * 32767f).toInt().toShort())
        file.writeBytes(buf.array())
    }
}
