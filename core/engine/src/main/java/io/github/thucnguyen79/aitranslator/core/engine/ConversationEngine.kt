package io.github.thucnguyen79.aitranslator.core.engine

import io.github.thucnguyen79.aitranslator.core.model.AudioInput
import io.github.thucnguyen79.aitranslator.core.model.Lang
import io.github.thucnguyen79.aitranslator.core.model.LanguageIdentifier
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

/** Listening for whoever speaks next: the seat is chosen from the detected language (hands-free mode). */
const val ANY_SEAT = -1

/** Per-stage durations: recognition (from the end of speech) and translation in ms, speech length in s. */
data class Timings(val asrMs: Long = 0, val mtMs: Long = 0, val audioSec: Double = 0.0)

data class Turn(
    val id: Long,
    val seat: Int,
    val from: Lang,
    val source: String,
    /** One translation per distinct language of the other seats. */
    val translations: Map<Lang, String>,
    val typed: Boolean,
    val timings: Timings,
    /** Each translation translated back into [from], so the speaker can check what the others read. */
    val backTranslations: Map<Lang, String> = emptyMap(),
    /** From the phrasebook: its translations were written by people, not the model. */
    val phrase: Boolean = false,
    /** The text was corrected by hand after recognition. */
    val edited: Boolean = false,
) {
    /** The sentence in [lang]: the original when it was said in [lang], else a translation (null if none yet). */
    fun textIn(lang: Lang): String? = if (lang == from) source else translations[lang]
}

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
    /** Whether models for [from]→[to] are installed; other directions are skipped instead of failing. */
    private val canTranslate: (from: Lang, to: Lang) -> Boolean = { _, _ -> true },
) {
    private val _state = MutableStateFlow<EngineState>(EngineState.Idle)
    val state: StateFlow<EngineState> = _state.asStateFlow()

    private val _turns = MutableStateFlow<List<Turn>>(emptyList())
    val turns: StateFlow<List<Turn>> = _turns.asStateFlow()

    /** Changing a seat's language translates the earlier turns into it (see [catchUpTranslations]). */
    var seats: List<Seat> = listOf(Seat(0, Lang.VI), Seat(1, Lang.EN))
        set(value) {
            field = value
            catchUpTranslations()
        }
    /** Seats that do not want translations read aloud; a language is spoken if any seat using it is not muted. */
    var mutedSeats: Set<Int> = emptySet()

    /** Translate each translation back into the speaker's language (F4.5); runs after speaking. */
    var backTranslate = false

    /** Detects the spoken language in hands-free mode (null: the optional pack is not installed). */
    var identifier: LanguageIdentifier? = null

    private val _handsFree = MutableStateFlow(false)
    /** Hands-free (F2.6): after each turn the engine listens again by itself. */
    val handsFree: StateFlow<Boolean> = _handsFree.asStateFlow()

    /** True while the current listening was started by hands-free mode rather than a mic press. */
    private var autoListening = false
    private var lastSeat: Int? = null

    private var job: Job? = null
    private var catchUpJob: Job? = null
    @Volatile private var stopRequested = false
    private var nextId = 1L

    /**
     * Starts listening for [seat]. Ignored while another seat is listening or its speech is processed, except
     * that a mic press takes over from hands-free listening.
     */
    fun startListening(seat: Int) = startListening(seat, auto = false)

    private fun startListening(seat: Int, auto: Boolean) {
        when (state.value) {
            is EngineState.Listening -> if (autoListening && !auto) interrupt() else return
            is EngineState.Recognizing, is EngineState.Translating -> return
            is EngineState.Speaking -> interrupt()
            else -> Unit
        }
        val from = langOf(seat)
        stopRequested = false
        autoListening = auto
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
            translateAndSpeak(seat, from, text, typed = false, asrMs = asrMs, audioSec = utterance.seconds)
        }
    }

    /**
     * Hands-free mode: listens again after every turn. With an [identifier] (and different languages at the
     * table) anyone may speak and the language picks the seat; without one the seats take turns.
     */
    fun setHandsFree(on: Boolean) {
        _handsFree.value = on
        when {
            on && isIdle() -> listenNext()
            !on && autoListening && state.value is EngineState.Listening -> interrupt()
        }
    }

    private fun listenNext() {
        if (!handsFree.value) return
        if (identifier != null && seats.map { it.lang }.distinct().size > 1) listenAnySeat() else startListening(nextSeat(), auto = true)
    }

    /** The seat after the last speaker (the first seat to begin with). */
    private fun nextSeat(): Int {
        val i = seats.indexOfFirst { it.index == lastSeat }
        return seats[(i + 1) % seats.size].index
    }

    /** The seat speaking [lang]; when several do, preferably not the last speaker. Unknown language: next seat. */
    private fun seatFor(lang: Lang?): Int {
        val speakers = seats.filter { it.lang == lang }
        return (speakers.firstOrNull { it.index != lastSeat } ?: speakers.firstOrNull())?.index ?: nextSeat()
    }

    private fun listenAnySeat() {
        stopRequested = false
        autoListening = true
        launchTurn(ANY_SEAT) {
            _state.value = EngineState.Listening(ANY_SEAT)
            val utterance = audio.capture { stopRequested }
            if (utterance == null) {
                _state.value = EngineState.NothingHeard(ANY_SEAT)
                return@launchTurn
            }
            _state.value = EngineState.Recognizing(ANY_SEAT)
            val detected = try {
                identifier?.identify(utterance)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null // fall back to taking turns
            }
            val seat = seatFor(detected)
            val from = langOf(seat)
            _state.value = EngineState.Recognizing(seat)
            val text = recognizer.transcribe(utterance, from)
            val asrMs = (clock() - utterance.endNanos) / 1_000_000
            if (text.isBlank()) {
                _state.value = EngineState.NothingHeard(ANY_SEAT)
                return@launchTurn
            }
            translateAndSpeak(seat, from, text, typed = false, asrMs = asrMs, audioSec = utterance.seconds)
        }
    }

    private fun isIdle() = state.value.let { it is EngineState.Idle || it is EngineState.NothingHeard || it is EngineState.Failed }

    /** Ends the current utterance now instead of waiting for silence. */
    fun stopListening() {
        stopRequested = true
    }

    /** Typed input (noisy places, misrecognised words): same pipeline without the microphone. */
    fun submitText(seat: Int, text: String) {
        if (text.isBlank()) return
        if (!isIdle()) interrupt()
        val from = langOf(seat)
        launchTurn(seat) { translateAndSpeak(seat, from, text.trim(), typed = true, asrMs = 0) }
    }

    /**
     * A phrasebook sentence: [texts] holds it in several languages, so known translations are used as they
     * are and only missing languages go through the model.
     */
    fun submitPhrase(seat: Int, texts: Map<Lang, String>) {
        val from = langOf(seat)
        val source = texts[from] ?: return
        if (!isIdle()) interrupt()
        launchTurn(seat) { translateAndSpeak(seat, from, source, typed = true, asrMs = 0, prepared = texts - from, phrase = true) }
    }

    /** Replaces a misrecognised sentence with [text] and translates it again (nothing is read aloud). */
    fun editTurn(id: Long, text: String) {
        val turn = turns.value.firstOrNull { it.id == id } ?: return
        if (text.isBlank() || text == turn.source) return
        _turns.update { list -> list.map { if (it.id == id) it.copy(source = text.trim(), translations = emptyMap(), backTranslations = emptyMap(), edited = true, phrase = false) else it } }
        scope.launch {
            val targets = seats.map { it.lang }.distinct().filter { it != turn.from && canTranslate(turn.from, it) }
            val translations = targets.associateWith { translator.translate(text.trim(), turn.from, it) }
            _turns.update { list -> list.map { if (it.id == id) it.copy(translations = translations) else it } }
            if (backTranslate) addBackTranslations(id)
        }
    }

    /**
     * Translates earlier turns into every language now at the table that they lack — after a seat changes
     * language, or once a missing pack is installed. Translations already made are kept, so switching
     * back and forth costs nothing. Directions without models are skipped.
     */
    fun catchUpTranslations() {
        catchUpJob?.cancel()
        catchUpJob = scope.launch {
            val langs = seats.map { it.lang }.distinct()
            for (turn in turns.value) {
                for (lang in langs) {
                    if (turn.textIn(lang) != null || !canTranslate(turn.from, lang)) continue
                    val text = try {
                        translator.translate(turn.source, turn.from, lang)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        continue // a missing or broken model only leaves this turn untranslated
                    }
                    _turns.update { list -> list.map { if (it.id == turn.id) it.copy(translations = it.translations + (lang to text)) else it } }
                }
            }
        }
    }

    /** Speaks one turn again in [lang] (the original if it was said in [lang]). */
    fun replay(turn: Turn, lang: Lang) {
        val text = turn.textIn(lang) ?: return
        if (autoListening && state.value is EngineState.Listening) interrupt() // hands-free waits for the replay
        if (!isIdle()) return
        launchTurn(turn.seat) {
            _state.value = EngineState.Speaking(turn.seat, lang)
            tts.speak(text, lang)
        }
    }

    /** Stops whatever is running (listening, translating or speaking). */
    fun interrupt() {
        stopRequested = true
        autoListening = false
        tts.stop()
        job?.cancel()
        _state.value = EngineState.Idle
    }

    fun clear() {
        _handsFree.value = false
        interrupt()
        catchUpJob?.cancel()
        _turns.value = emptyList()
    }

    private suspend fun translateAndSpeak(
        seat: Int,
        from: Lang,
        text: String,
        typed: Boolean,
        asrMs: Long,
        audioSec: Double = 0.0,
        prepared: Map<Lang, String> = emptyMap(),
        phrase: Boolean = false,
    ) {
        _state.value = EngineState.Translating(seat)
        val targets = seats.filter { it.index != seat }.map { it.lang }.distinct()
            .filter { it != from && (it in prepared || canTranslate(from, it)) }
        val t0 = clock()
        val translations = targets.associateWith { prepared[it] ?: translator.translate(text, from, it) }
        val mtMs = (clock() - t0) / 1_000_000
        val id = nextId++
        lastSeat = seat
        _turns.update { it + Turn(id, seat, from, text, translations, typed, Timings(asrMs, mtMs, audioSec), phrase = phrase) }
        val listening = seats.filter { it.index != seat && it.index !in mutedSeats }.map { it.lang }.toSet()
        for (lang in targets.filter { it in listening }) {
            _state.value = EngineState.Speaking(seat, lang)
            tts.speak(translations.getValue(lang), lang)
        }
        if (backTranslate && !phrase) addBackTranslations(id)
    }

    /** Fills [Turn.backTranslations]: each translation translated back into the language it came from. */
    private suspend fun addBackTranslations(id: Long) {
        val turn = turns.value.firstOrNull { it.id == id } ?: return
        val back = turn.translations
            .filterKeys { canTranslate(it, turn.from) }
            .mapValues { (lang, text) -> runCatching { translator.translate(text, lang, turn.from) }.getOrNull() }
            .filterValues { it != null }
            .mapValues { it.value!! }
        _turns.update { list -> list.map { if (it.id == id) it.copy(backTranslations = back) else it } }
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
                _handsFree.value = false // e.g. no microphone: do not retry in a loop
            }
            autoListening = false
            listenNext()
        }
    }

    private fun langOf(seat: Int) = seats.first { it.index == seat }.lang
}
