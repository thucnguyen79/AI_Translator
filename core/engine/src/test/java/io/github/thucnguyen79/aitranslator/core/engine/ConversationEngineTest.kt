package io.github.thucnguyen79.aitranslator.core.engine

import io.github.thucnguyen79.aitranslator.core.model.AudioInput
import io.github.thucnguyen79.aitranslator.core.model.Lang
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
        override suspend fun capture(shouldStop: () -> Boolean): Utterance? {
            captures++
            gate?.await()
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
