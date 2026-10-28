package io.github.thucnguyen79.aitranslator.core.packs

import io.github.thucnguyen79.aitranslator.core.model.Lang
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

sealed interface PackState {
    data object NotInstalled : PackState
    /** [bytes] of [total] downloaded for this pack (its required packs report their own progress). */
    data class Downloading(val bytes: Long, val total: Long) : PackState {
        val progress: Float get() = if (total > 0) bytes.toFloat() / total else 0f
    }
    data object Installing : PackState
    data object Installed : PackState
    data class Failed(val message: String) : PackState
}

/**
 * Downloads, verifies and installs packs into `root/<pack id>/`. Partial downloads stay in
 * `root/.downloads/` and resume on the next attempt; a pack counts as installed once its marker exists.
 */
class PackManager(
    private val root: File,
    val catalog: PackCatalog,
    private val scope: CoroutineScope,
) {
    private val downloads = File(root, ".downloads")
    private val jobs = HashMap<String, Job>()
    private val _states = MutableStateFlow(catalog.packs.associate { it.id to initialState(it) })
    val states: StateFlow<Map<String, PackState>> = _states.asStateFlow()

    fun dir(packId: String) = File(root, packId)

    fun isInstalled(packId: String) = marker(packId).isFile

    fun isLanguageReady(lang: Lang) = catalog.withRequirements(catalog.languagePack(lang).id).all { isInstalled(it.id) }

    /** Bytes still to download for [packId] including its missing requirements. */
    fun downloadSize(packId: String): Long = catalog.withRequirements(packId).filterNot { isInstalled(it.id) }.sumOf { it.size }

    /** Installs [packId] and the packs it requires, one after another. */
    fun install(packId: String) {
        synchronized(jobs) {
            if (jobs[packId]?.isActive == true) return
            jobs[packId] = scope.launch(Dispatchers.IO) {
                for (pack in catalog.withRequirements(packId)) {
                    if (isInstalled(pack.id)) continue
                    if (!installOne(pack)) break
                }
            }
        }
    }

    fun cancel(packId: String) {
        synchronized(jobs) { jobs.remove(packId)?.cancel() }
    }

    suspend fun delete(packId: String) = withContext(Dispatchers.IO) {
        cancel(packId)
        marker(packId).delete()
        dir(packId).deleteRecursively()
        set(packId, PackState.NotInstalled)
    }

    private suspend fun installOne(pack: Pack): Boolean {
        val total = pack.size
        try {
            var done = 0L
            set(pack.id, PackState.Downloading(0, total))
            val files = pack.files.map { f ->
                val part = File(downloads, f.sha256)
                if (!(part.isFile && part.length() == f.size && Downloads.sha256(part) == f.sha256)) {
                    Downloads.fetch(f.url, part) { bytes -> set(pack.id, PackState.Downloading(done + bytes, total)) }
                    if (Downloads.sha256(part) != f.sha256) {
                        part.delete()
                        throw IOException("Checksum mismatch: ${f.url}")
                    }
                }
                done += f.size
                f to part
            }
            set(pack.id, PackState.Installing)
            val target = dir(pack.id)
            target.deleteRecursively()
            for ((f, part) in files) {
                val dest = File(target, f.path)
                when (f.extract) {
                    null -> {
                        dest.parentFile?.mkdirs()
                        if (!part.renameTo(dest)) {
                            part.copyTo(dest, overwrite = true)
                            part.delete()
                        }
                    }
                    "tar.bz2" -> {
                        Downloads.extractTarBz2(part, dest)
                        part.delete()
                    }
                    else -> error("Unsupported archive ${f.extract}")
                }
            }
            marker(pack.id).writeText(catalog.version.toString())
            set(pack.id, PackState.Installed)
            return true
        } catch (e: CancellationException) {
            set(pack.id, PackState.NotInstalled)
            throw e
        } catch (e: Exception) {
            set(pack.id, PackState.Failed(e.message ?: e::class.simpleName.orEmpty()))
            return false
        }
    }

    private fun marker(packId: String) = File(dir(packId), ".installed")

    private fun initialState(pack: Pack) = if (marker(pack.id).isFile) PackState.Installed else PackState.NotInstalled

    private fun set(packId: String, state: PackState) = _states.update { it + (packId to state) }
}
