package io.github.thucnguyen79.aitranslator.core.packs

import io.github.thucnguyen79.aitranslator.core.model.Lang
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class PackManagerTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun tarBz2(file: File, entries: Map<String, String>): File {
        TarArchiveOutputStream(BZip2CompressorOutputStream(file.outputStream())).use { tar ->
            for ((name, content) in entries) {
                val bytes = content.toByteArray()
                tar.putArchiveEntry(TarArchiveEntry(name).apply { size = bytes.size.toLong() })
                tar.write(bytes)
                tar.closeArchiveEntry()
            }
        }
        return file
    }

    private fun packFile(source: File, path: String, extract: String? = null) =
        PackFile(source.toURI().toString(), source.length(), Downloads.sha256(source), path, extract)

    @Test fun extractDropsTopFolderAndTestAudio() {
        val archive = tarBz2(tmp.newFile("m.tar.bz2"), mapOf("model-x/tokens.txt" to "a", "model-x/test_wavs/0.wav" to "w", "model-x/sub/b.onnx" to "b"))
        val out = tmp.newFolder("out")
        Downloads.extractTarBz2(archive, out)
        assertEquals("a", File(out, "tokens.txt").readText())
        assertEquals("b", File(out, "sub/b.onnx").readText())
        assertFalse(File(out, "test_wavs").exists())
    }

    @Test fun extractRejectsPathTraversal() {
        val archive = tarBz2(tmp.newFile("evil.tar.bz2"), mapOf("x/../../escape.txt" to "!"))
        assertThrows(IOException::class.java) { Downloads.extractTarBz2(archive, tmp.newFolder("out")) }
    }

    @Test fun installsPackWithRequirementsAndVerifiesFiles() = runBlocking {
        val vad = tmp.newFile("vad.onnx").apply { writeText("vad") }
        val asr = tarBz2(tmp.newFile("asr.tar.bz2"), mapOf("zipformer/tokens.txt" to "t"))
        val catalog = PackCatalog(
            1,
            listOf(
                Pack("core", PackKind.SHARED, emptyList(), emptyList(), "", listOf(packFile(vad, "vad/silero_vad.onnx"))),
                Pack("vi", PackKind.LANGUAGE, listOf(Lang.VI), listOf("core"), "", listOf(packFile(asr, "asr", "tar.bz2"))),
            ),
        )
        val manager = PackManager(tmp.newFolder("packs"), catalog, CoroutineScope(SupervisorJob() + Dispatchers.IO))
        assertFalse(manager.isLanguageReady(Lang.VI))

        manager.install("vi")
        withTimeout(10_000) { manager.states.first { it["vi"] == PackState.Installed } }

        assertTrue(manager.isLanguageReady(Lang.VI))
        val locator = ModelLocator(manager)
        assertEquals("vad", locator.vadModel.readText())
        assertEquals("t", File(locator.asrDir(Lang.VI), "tokens.txt").readText())
    }

    @Test fun checksumMismatchFailsThePack() = runBlocking {
        val vad = tmp.newFile("vad.onnx").apply { writeText("vad") }
        val bad = packFile(vad, "vad/silero_vad.onnx").copy(sha256 = "0".repeat(64))
        val catalog = PackCatalog(1, listOf(Pack("core", PackKind.SHARED, emptyList(), emptyList(), "", listOf(bad))))
        val manager = PackManager(tmp.newFolder("packs"), catalog, CoroutineScope(SupervisorJob() + Dispatchers.IO))

        manager.install("core")
        val state = withTimeout(10_000) { manager.states.first { it["core"] is PackState.Failed } }

        assertTrue((state["core"] as PackState.Failed).message.startsWith("Checksum mismatch"))
        assertFalse(manager.isInstalled("core"))
    }
}
