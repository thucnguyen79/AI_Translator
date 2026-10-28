package io.github.thucnguyen79.aitranslator.core.engine

import io.github.thucnguyen79.aitranslator.core.model.AudioInput
import io.github.thucnguyen79.aitranslator.core.model.Lang
import io.github.thucnguyen79.aitranslator.core.model.SpeechRecognizer
import io.github.thucnguyen79.aitranslator.core.model.SpeechSynthesizer
import io.github.thucnguyen79.aitranslator.core.model.Translator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A person at the table: their position (0..3) and language. */
data class Seat(val index: Int, val lang: Lang)

/** Per-stage durations in ms, measured from the end of speech. */
data class Timings(val asrMs: Long = 0, val mtMs: Long = 0)

data class Turn(
    val id: Long,
    val seat: Int,
    val from: Lang,
    val source: String,
    /** One translation per distinct language of the other seats. */
    val translations: Map<Lang, String>,
    val typed: Boolean,
    val timings: Timings,
)

sealed interface EngineState {
    data object Idle : EngineState
    data class Listening(val seat: Int) : EngineState
    data class Recognizing(val seat: Int) : EngineState
    data class Translating(val seat: Int) : EngineState
    data class Speaking(val seat: Int, val lang: Lang) : EngineState
    /** Nothing usable was heard; the UI shows a hint until the next action. */
    data class NothingHeard(val seat: Int) : EngineState
    data class Failed(val message: String) : EngineState
}

/**
 * Runs one turn at a time on a single phone: listen → recognise → translate into every other language at
 * the table → speak the translations in seat order. Only one seat can listen at a time; pressing a mic
 * while translations are being spoken interrupts them (barge-in).
 */
class ConversationEngine(
    private val audio: AudioInput,
    private val recognizer: SpeechRecognizer,
    private val translator: Translator,
    private val tts: SpeechSynthesizer,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::nanoTime,
) {
    private val _state = MutableStateFlow<EngineState>(EngineState.Idle)
    val state: StateFlow<EngineState> = _state.asStateFlow()

    private val _turns = MutableStateFlow<List<Turn>>(emptyList())
    val turns: StateFlow<List<Turn>> = _turns.asStateFlow()

    var seats: List<Seat> = listOf(Seat(0, Lang.VI), Seat(1, Lang.EN))
    var speakTranslations = true

    private var job: Job? = null
    @Volatile private var stopRequested = false
    private var nextId = 1L

    /** Starts listening for [seat]. Ignored while another seat is listening or its speech is processed. */
    fun startListening(seat: Int) {
        when (state.value) {
            is EngineState.Listening, is EngineState.Recognizing, is EngineState.Translating -> return
            is EngineState.Speaking -> interrupt()
            else -> Unit
        }
        val from = langOf(seat)
        stopRequested = false
        launchTurn(seat) {
            _state.value = EngineState.Listening(seat)
            val utterance = audio.capture { stopRequested }
            if (utterance == null) {
                _state.value = EngineState.NothingHeard(seat)
                return@launchTurn
            }
            _state.value = EngineState.Recognizing(seat)
            val text = recognizer.transcribe(utterance, from)
            val asrMs = (clock() - utterance.endNanos) / 1_000_000
            if (text.isBlank()) {
                _state.value = EngineState.NothingHeard(seat)
                return@launchTurn
            }
            translateAndSpeak(seat, from, text, typed = false, asrMs = asrMs)
        }
    }

    /** Ends the current utterance now instead of waiting for silence. */
    fun stopListening() {
        stopRequested = true
    }

    /** Typed input (noisy places, misrecognised words): same pipeline without the microphone. */
    fun submitText(seat: Int, text: String) {
        if (text.isBlank()) return
        if (state.value !is EngineState.Idle && state.value !is EngineState.NothingHeard && state.value !is EngineState.Failed) interrupt()
        val from = langOf(seat)
        launchTurn(seat) { translateAndSpeak(seat, from, text.trim(), typed = true, asrMs = 0) }
    }

    /** Speaks one translation of an earlier turn again. */
    fun replay(turn: Turn, lang: Lang) {
        val text = turn.translations[lang] ?: return
        if (state.value !is EngineState.Idle && state.value !is EngineState.NothingHeard && state.value !is EngineState.Failed) return
        launchTurn(turn.seat) {
            _state.value = EngineState.Speaking(turn.seat, lang)
            tts.speak(text, lang)
        }
    }

    /** Stops whatever is running (listening, translating or speaking). */
    fun interrupt() {
        stopRequested = true
        tts.stop()
        job?.cancel()
        _state.value = EngineState.Idle
    }

    fun clear() {
        interrupt()
        _turns.value = emptyList()
    }

    private suspend fun translateAndSpeak(seat: Int, from: Lang, text: String, typed: Boolean, asrMs: Long) {
        _state.value = EngineState.Translating(seat)
        val targets = seats.filter { it.index != seat }.map { it.lang }.distinct().filter { it != from }
        val t0 = clock()
        val translations = targets.associateWith { translator.translate(text, from, it) }
        val mtMs = (clock() - t0) / 1_000_000
        _turns.update { it + Turn(nextId++, seat, from, text, translations, typed, Timings(asrMs, mtMs)) }
        if (speakTranslations) {
            for (lang in targets) {
                _state.value = EngineState.Speaking(seat, lang)
                tts.speak(translations.getValue(lang), lang)
            }
        }
    }

    private fun launchTurn(seat: Int, block: suspend () -> Unit) {
        job = scope.launch {
            try {
                block()
                if (state.value !is EngineState.NothingHeard) _state.value = EngineState.Idle
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = EngineState.Failed(e.message ?: e::class.simpleName.orEmpty())
            }
        }
    }

    private fun langOf(seat: Int) = seats.first { it.index == seat }.lang
}
