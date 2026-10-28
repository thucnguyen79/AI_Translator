package io.github.thucnguyen79.aitranslator.core.packs

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

internal object Downloads {
    private const val BUFFER = 1 shl 16

    /**
     * Downloads [url] into [dest], resuming a partial file with an HTTP Range request.
     * [onBytes] receives the number of bytes on disk so far.
     */
    suspend fun fetch(url: String, dest: File, onBytes: (Long) -> Unit) {
        dest.parentFile?.mkdirs()
        var offset = if (dest.isFile) dest.length() else 0L
        val conn = URL(url).openConnection()
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        if (conn is HttpURLConnection) {
            conn.instanceFollowRedirects = true
            if (offset > 0) conn.setRequestProperty("Range", "bytes=$offset-")
            val code = conn.responseCode
            when {
                code == HttpURLConnection.HTTP_PARTIAL -> Unit
                code == 416 -> return conn.disconnect() // already complete
                code in 200..299 -> offset = 0 // server ignored the range: start over
                else -> throw IOException("HTTP $code for $url")
            }
        } else {
            offset = 0
        }
        conn.getInputStream().use { input ->
            FileOutputStream(dest, offset > 0).use { out ->
                val buf = ByteArray(BUFFER)
                var total = offset
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    total += n
                    onBytes(total)
                }
            }
        }
    }

    fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(BUFFER)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Extracts a .tar.bz2 into [target], dropping the archive's top-level folder and test audio.
     * Entries that would escape [target] are rejected.
     */
    fun extractTarBz2(archive: File, target: File) {
        target.mkdirs()
        val root = target.canonicalFile
        TarArchiveInputStream(BZip2CompressorInputStream(archive.inputStream().buffered(BUFFER))).use { tar ->
            while (true) {
                val entry = tar.nextEntry ?: break
                val parts = entry.name.split('/').filter { it.isNotEmpty() }
                if (parts.size < 2 || "test_wavs" in parts) continue
                val out = File(root, parts.drop(1).joinToString("/")).canonicalFile
                if (!out.path.startsWith(root.path + File.separator)) throw IOException("Unsafe archive entry ${entry.name}")
                if (entry.isDirectory) {
                    out.mkdirs()
                } else if (entry.isFile) {
                    out.parentFile?.mkdirs()
                    out.outputStream().use { tar.copyTo(it, BUFFER) }
                }
            }
        }
    }
}
