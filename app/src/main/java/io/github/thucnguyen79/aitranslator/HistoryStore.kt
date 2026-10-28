package io.github.thucnguyen79.aitranslator

import io.github.thucnguyen79.aitranslator.core.engine.Timings
import io.github.thucnguyen79.aitranslator.core.engine.Turn
import io.github.thucnguyen79.aitranslator.core.model.Lang
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.DateFormat
import java.util.Date

/** A saved conversation: text only, never audio (F9.1). [id] is its start time in ms. */
data class SavedSession(val id: Long, val updated: Long, val langs: List<Lang>, val turns: List<Turn>) {
    fun matches(query: String): Boolean = query.isBlank() || turns.any { it.mentions(query) }

    /** Plain-text transcript for sharing (F9.4): each sentence followed by its translations. */
    fun toText(appName: String): String = buildString {
        appendLine("$appName — ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(id))}")
        appendLine(langs.joinToString(" · ") { it.nativeName })
        turns.forEachIndexed { i, turn ->
            appendLine()
            appendLine("${i + 1}. [${turn.from.nativeName}] ${turn.source}")
            turn.translations.forEach { (lang, text) -> appendLine("   [${lang.nativeName}] $text") }
        }
    }
}

/** Whether the sentence or one of its translations contains [query] (ignoring case). */
fun Turn.mentions(query: String): Boolean = (listOf(source) + translations.values).any { it.contains(query.trim(), ignoreCase = true) }

/** One JSON file per conversation in [dir]; [sessions] is kept newest first. */
class HistoryStore(private val dir: File) {
    private val _sessions = MutableStateFlow<List<SavedSession>>(emptyList())
    val sessions: StateFlow<List<SavedSession>> = _sessions.asStateFlow()

    /** Reads the saved conversations; damaged files are skipped. */
    fun load() {
        val loaded = dir.listFiles { f -> f.name.endsWith(".json") }.orEmpty()
            .mapNotNull { f -> runCatching { decode(JSONObject(f.readText())) }.getOrNull() }
        _sessions.value = loaded.sortedByDescending { it.id }
    }

    @Synchronized
    fun save(session: SavedSession) {
        dir.mkdirs()
        val tmp = File(dir, "${session.id}.json.tmp")
        tmp.writeText(encode(session).toString())
        tmp.renameTo(File(dir, "${session.id}.json"))
        _sessions.update { list -> (list.filter { it.id != session.id } + session).sortedByDescending { it.id } }
    }

    @Synchronized
    fun delete(id: Long) {
        File(dir, "$id.json").delete()
        _sessions.update { list -> list.filter { it.id != id } }
    }

    @Synchronized
    fun deleteAll() {
        dir.listFiles().orEmpty().forEach { it.delete() }
        _sessions.value = emptyList()
    }

    companion object {
        fun encode(s: SavedSession): JSONObject = JSONObject()
            .put("id", s.id)
            .put("updated", s.updated)
            .put("langs", JSONArray(s.langs.map { it.code }))
            .put("turns", JSONArray(s.turns.map { t ->
                JSONObject()
                    .put("seat", t.seat)
                    .put("from", t.from.code)
                    .put("source", t.source)
                    .put("translations", JSONObject(t.translations.mapKeys { it.key.code }))
                    .put("typed", t.typed)
                    .put("phrase", t.phrase)
                    .put("edited", t.edited)
            }))

        fun decode(o: JSONObject): SavedSession {
            val langs = o.getJSONArray("langs").let { a -> (0 until a.length()).mapNotNull { Lang.find(a.getString(it)) } }
            val turns = o.getJSONArray("turns").let { a ->
                (0 until a.length()).mapNotNull { i ->
                    val t = a.getJSONObject(i)
                    val from = Lang.find(t.getString("from")) ?: return@mapNotNull null
                    val tr = t.getJSONObject("translations")
                    Turn(
                        id = i + 1L,
                        seat = t.getInt("seat"),
                        from = from,
                        source = t.getString("source"),
                        translations = tr.keys().asSequence().mapNotNull { k -> Lang.find(k)?.let { it to tr.getString(k) } }.toMap(),
                        typed = t.optBoolean("typed"),
                        timings = Timings(),
                        phrase = t.optBoolean("phrase"),
                        edited = t.optBoolean("edited"),
                    )
                }
            }
            return SavedSession(o.getLong("id"), o.optLong("updated", o.getLong("id")), langs, turns)
        }
    }
}
