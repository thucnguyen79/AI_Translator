package io.github.thucnguyen79.aitranslator

import io.github.thucnguyen79.aitranslator.core.engine.Timings
import io.github.thucnguyen79.aitranslator.core.engine.Turn
import io.github.thucnguyen79.aitranslator.core.model.Lang
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class HistoryStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private val session = SavedSession(
        id = 1_000L,
        updated = 2_000L,
        langs = listOf(Lang.VI, Lang.EN),
        turns = listOf(
            Turn(1, 0, Lang.VI, "Nhà vệ sinh ở đâu?", mapOf(Lang.EN to "Where is the restroom?"), typed = false, timings = Timings(), phrase = true),
            Turn(2, 1, Lang.EN, "Over there", mapOf(Lang.VI to "Ở đằng kia"), typed = true, timings = Timings(), edited = true),
        ),
    )

    @Test fun sessionsSurviveARestart() {
        val named = session.copy(names = listOf("Minh", "Anna"))
        HistoryStore(tmp.root).save(named)

        val reopened = HistoryStore(tmp.root).apply { load() }
        assertEquals(listOf(named), reopened.sessions.value)
    }

    @Test fun newestFirstAndDelete() {
        val store = HistoryStore(tmp.root)
        store.save(session)
        store.save(session.copy(id = 5_000L))
        assertEquals(listOf(5_000L, 1_000L), store.sessions.value.map { it.id })

        store.delete(5_000L)
        assertEquals(listOf(1_000L), HistoryStore(tmp.root).apply { load() }.sessions.value.map { it.id })
        store.deleteAll()
        assertTrue(HistoryStore(tmp.root).apply { load() }.sessions.value.isEmpty())
    }

    @Test fun searchLooksInTranslationsToo() {
        assertTrue(session.matches("restroom"))
        assertTrue(session.matches("ĐẰNG KIA"))
        assertFalse(session.matches("taxi"))
    }

    @Test fun exportListsEachSentenceWithItsTranslations() {
        val text = session.toText("AI Translator")
        assertTrue(text.contains("1. [Tiếng Việt] Nhà vệ sinh ở đâu?"))
        assertTrue(text.contains("   [English] Where is the restroom?"))
        assertTrue(text.contains("2. [English] Over there"))
        // Named seats are shown before the language.
        assertTrue(session.copy(names = listOf("", "Anna")).toText("AI Translator").contains("2. Anna [English] Over there"))
    }
}
