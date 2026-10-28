package io.github.thucnguyen79.aitranslator.core.engine

import io.github.thucnguyen79.aitranslator.core.model.AudioInput
import io.github.thucnguyen79.aitranslator.core.model.Lang
import io.github.thucnguyen79.aitranslator.core.model.LanguageIdentifier
import io.github.thucnguyen79.aitranslator.core.model.SpeechRecognizer
import io.github.thucnguyen79.aitranslator.core.model.SpeechSynthesizer
import io.github.thucnguyen79.aitranslator.core.model.Translator
import io.github.thucnguyen79.aitranslator.core.model.Utterance
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationEngineTest {
    private class FakeAudio : AudioInput {
        var next: Utterance? = Utterance(FloatArray(16_000), 16_000, 0)
        var captures = 0
        var gate: CompletableDeferred<Unit>? = null
        /** Captures after this many wait forever (until cancelled): stops hands-free loops in tests. */
        var blockAfter = Int.MAX_VALUE
        override suspend fun capture(shouldStop: () -> Boolean): Utterance? {
            captures++
            gate?.await()
            if (captures > blockAfter) CompletableDeferred<Unit>().await()
            return next
        }
    }

    private class FakeRecognizer(var text: String = "xin chào") : SpeechRecognizer {
        override suspend fun transcribe(utterance: Utterance, lang: Lang) = text
    }

    private class FakeTranslator : Translator {
        val calls = mutableListOf<Pair<Lang, Lang>>()
        override suspend fun translate(text: String, from: Lang, to: Lang): String {
            calls += from to to
            return "${to.code}:$text"
        }
    }

    private class FakeTts : SpeechSynthesizer {
        val spoken = mutableListOf<Pair<String, Lang>>()
        var hold: CompletableDeferred<Unit>? = null
        var stops = 0
        override suspend fun speak(text: String, lang: Lang) {
            spoken += text to lang
            hold?.await()
        }
        override fun stop() {
            stops++
            hold?.complete(Unit)
        }
    }

    private val audio = FakeAudio()
    private val recognizer = FakeRecognizer()
    private val translator = FakeTranslator()
    private val tts = FakeTts()

    // The test scope itself: advanceUntilIdle() does not wait for backgroundScope work.
    private fun TestScope.engine() = ConversationEngine(audio, recognizer, translator, tts, this, clock = { 0 })

    @Test fun twoSeatsTranslateAndSpeak() = runTest {
        val engine = engine()
        engine.startListening(0)
        advanceUntilIdle()

        val turn = engine.turns.value.single()
        assertEquals(Lang.VI, turn.from)
        assertEquals(mapOf(Lang.EN to "en:xin chào"), turn.translations)
        assertEquals(listOf("en:xin chào" to Lang.EN), tts.spoken)
        assertEquals(EngineState.Idle, engine.state.value)
    }

    @Test fun eachDistinctLanguageIsTranslatedOnce() = runTest {
        val engine = engine()
        engine.seats = listOf(Seat(0, Lang.VI), Seat(1, Lang.JA), Seat(2, Lang.JA), Seat(3, Lang.EN))
        engine.startListening(0)
        advanceUntilIdle()

        assertEquals(listOf(Lang.VI to Lang.JA, Lang.VI to Lang.EN), translator.calls)
        assertEquals(listOf(Lang.JA, Lang.EN), tts.spoken.map { it.second })
    }

    @Test fun sameLanguageSeatsGetNoTranslation() = runTest {
        val engine = engine()
        engine.seats = listOf(Seat(0, Lang.VI), Seat(1, Lang.VI))
        engine.startListening(0)
        advanceUntilIdle()

        assertTrue(engine.turns.value.single().translations.isEmpty())
        assertTrue(tts.spoken.isEmpty())
    }

    @Test fun otherSeatsAreLockedWhileListening() = runTest {
        val engine = engine()
        audio.gate = CompletableDeferred()
        engine.startListening(0)
        runCurrent()
        engine.startListening(1)
        runCurrent()

        assertEquals(EngineState.Listening(0), engine.state.value)
        assertEquals(1, audio.captures)
        audio.gate!!.complete(Unit)
        advanceUntilIdle()
        assertEquals(Lang.VI, engine.turns.value.single().from)
    }

    @Test fun pressingAMicWhileSpeakingInterrupts() = runTest {
        val engine = engine()
        tts.hold = CompletableDeferred()
        engine.startListening(0)
        runCurrent()
        assertEquals(EngineState.Speaking(0, Lang.EN), engine.state.value)

        tts.hold = null
        recognizer.text = "hello"
        engine.startListening(1)
        advanceUntilIdle()

        assertEquals(1, tts.stops)
        assertEquals(listOf(Lang.VI, Lang.EN), engine.turns.value.map { it.from })
        assertEquals("vi:hello", engine.turns.value.last().translations[Lang.VI])
    }

    @Test fun silenceOrBlankTranscriptAddsNoTurn() = runTest {
        val engine = engine()
        audio.next = null
        engine.startListening(0)
        advanceUntilIdle()
        assertEquals(EngineState.NothingHeard(0), engine.state.value)

        audio.next = Utterance(FloatArray(1), 16_000, 0)
        recognizer.text = "  "
        engine.startListening(0)
        advanceUntilIdle()
        assertEquals(EngineState.NothingHeard(0), engine.state.value)
        assertTrue(engine.turns.value.isEmpty())
    }

    @Test fun typedTextSkipsTheMicrophone() = runTest {
        val engine = engine()
        engine.submitText(1, "Where is the toilet?")
        advanceUntilIdle()

        assertEquals(0, audio.captures)
        val turn = engine.turns.value.single()
        assertTrue(turn.typed)
        assertEquals("vi:Where is the toilet?", turn.translations[Lang.VI])
    }

    @Test fun changingASeatLanguageTranslatesEarlierTurns() = runTest {
        val engine = engine()
        engine.startListening(0)
        advanceUntilIdle()

        engine.seats = listOf(Seat(0, Lang.VI), Seat(1, Lang.ZH))
        advanceUntilIdle()

        val turn = engine.turns.value.single()
        assertEquals("zh:xin chào", turn.textIn(Lang.ZH))
        // Earlier translations are kept, so switching back needs no new work.
        assertEquals("en:xin chào", turn.textIn(Lang.EN))
        assertEquals("xin chào", turn.textIn(Lang.VI))
        val calls = translator.calls.size
        engine.seats = listOf(Seat(0, Lang.VI), Seat(1, Lang.EN))
        advanceUntilIdle()
        assertEquals(calls, translator.calls.size)
    }

    @Test fun directionsWithoutModelsAreSkipped() = runTest {
        val engine = ConversationEngine(audio, recognizer, translator, tts, this, clock = { 0 }, canTranslate = { _, to -> to != Lang.KO })
        engine.seats = listOf(Seat(0, Lang.VI), Seat(1, Lang.KO))
        engine.startListening(0)
        advanceUntilIdle()

        assertTrue(engine.turns.value.single().translations.isEmpty())
        assertEquals(EngineState.Idle, engine.state.value)
        assertTrue(translator.calls.isEmpty())
    }

    @Test fun mutedSeatsAreNotReadAloud() = runTest {
        val engine = engine()
        engine.seats = listOf(Seat(0, Lang.VI), Seat(1, Lang.EN), Seat(2, Lang.JA), Seat(3, Lang.JA))
        engine.mutedSeats = setOf(1, 2)
        engine.startListening(0)
        advanceUntilIdle()

        // English is muted; Japanese is still wanted by seat 3. Everything is still translated.
        assertEquals(listOf(Lang.JA), tts.spoken.map { it.second })
        assertEquals(setOf(Lang.EN, Lang.JA), engine.turns.value.single().translations.keys)
    }

    @Test fun phrasesUseTheirOwnTranslations() = runTest {
        val engine = engine()
        engine.seats = listOf(Seat(0, Lang.VI), Seat(1, Lang.EN), Seat(2, Lang.KO))
        engine.submitPhrase(0, mapOf(Lang.VI to "Nhà vệ sinh ở đâu?", Lang.EN to "Where is the restroom?"))
        advanceUntilIdle()

        val turn = engine.turns.value.single()
        assertTrue(turn.phrase)
        assertEquals("Where is the restroom?", turn.textIn(Lang.EN))
        // Korean was not in the phrasebook entry, so it falls back to the model.
        assertEquals(listOf(Lang.VI to Lang.KO), translator.calls)
    }

    @Test fun editingASentenceTranslatesItAgain() = runTest {
        val engine = engine()
        engine.startListening(0)
        advanceUntilIdle()
        val id = engine.turns.value.single().id

        engine.editTurn(id, "xin lỗi")
        advanceUntilIdle()

        val turn = engine.turns.value.single()
        assertTrue(turn.edited)
        assertEquals("xin lỗi", turn.source)
        assertEquals("en:xin lỗi", turn.textIn(Lang.EN))
    }

    @Test fun backTranslationsComeAfterSpeaking() = runTest {
        val engine = engine()
        engine.backTranslate = true
        engine.startListening(0)
        advanceUntilIdle()

        val turn = engine.turns.value.single()
        assertEquals(mapOf(Lang.EN to "vi:en:xin chào"), turn.backTranslations)
        assertEquals(listOf(Lang.VI to Lang.EN, Lang.EN to Lang.VI), translator.calls)
    }

    @Test fun handsFreeSeatsTakeTurns() = runTest {
        val engine = engine()
        audio.blockAfter = 1
        engine.setHandsFree(true)
        advanceUntilIdle()

        assertEquals(Lang.VI, engine.turns.value.single().from)
        // Without language identification the next seat is listened to automatically.
        assertEquals(EngineState.Listening(1), engine.state.value)
        assertEquals(2, audio.captures)

        engine.setHandsFree(false)
        assertEquals(EngineState.Idle, engine.state.value)
    }

    @Test fun handsFreePicksTheSeatFromTheSpokenLanguage() = runTest {
        val engine = engine()
        engine.identifier = object : LanguageIdentifier {
            override suspend fun identify(utterance: Utterance, candidates: Collection<Lang>, expected: Lang?) = Lang.EN
        }
        recognizer.text = "hello"
        audio.blockAfter = 1
        engine.setHandsFree(true)
        advanceUntilIdle()

        val turn = engine.turns.value.single()
        assertEquals(1, turn.seat)
        assertEquals("vi:hello", turn.translations[Lang.VI])
        assertEquals(EngineState.Listening(ANY_SEAT), engine.state.value)
        engine.setHandsFree(false)
    }

    @Test fun handsFreeTellsTheIdentifierWhoseTurnItIs() = runTest {
        val engine = engine()
        val seen = mutableListOf<Lang?>()
        engine.identifier = object : LanguageIdentifier {
            override suspend fun identify(utterance: Utterance, candidates: Collection<Lang>, expected: Lang?): Lang {
                seen += expected
                return Lang.VI
            }
        }
        audio.blockAfter = 2
        engine.setHandsFree(true)
        advanceUntilIdle()

        // Nobody has spoken yet, then after seat 0 (Vietnamese) it is seat 1's (English) turn.
        assertEquals(listOf(null, Lang.EN), seen)
        engine.setHandsFree(false)
    }

    @Test fun aMicPressTakesOverHandsFreeListening() = runTest {
        val engine = engine()
        audio.blockAfter = 0
        engine.setHandsFree(true)
        runCurrent()
        assertEquals(EngineState.Listening(0), engine.state.value)

        engine.startListening(1)
        runCurrent()
        assertEquals(EngineState.Listening(1), engine.state.value)
        assertEquals(2, audio.captures)
        engine.setHandsFree(false)
        engine.interrupt()
    }

    @Test fun failuresAreReported() = runTest {
        val engine = ConversationEngine(
            audio,
            object : SpeechRecognizer {
                override suspend fun transcribe(utterance: Utterance, lang: Lang): String = error("model missing")
            },
            translator, tts, this,
        )
        engine.startListening(0)
        advanceUntilIdle()
        assertEquals(EngineState.Failed("model missing"), engine.state.value)
    }
}
