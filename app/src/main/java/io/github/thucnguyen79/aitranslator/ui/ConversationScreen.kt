package io.github.thucnguyen79.aitranslator.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.thucnguyen79.aitranslator.AppContainer
import io.github.thucnguyen79.aitranslator.ConversationLayout
import io.github.thucnguyen79.aitranslator.core.engine.EngineState
import io.github.thucnguyen79.aitranslator.core.engine.Turn
import io.github.thucnguyen79.aitranslator.core.model.Lang
import io.github.thucnguyen79.aitranslator.core.tts.SupertonicTts
import io.github.thucnguyen79.aitranslator.core.tts.TtsRoute
import java.util.Locale

/** Seat 1 sits across the table: its half (and anything shown to it) is rotated 180°. */
private fun isFlipped(seat: Int) = seat == 1

/**
 * Two people facing each other across the phone. Each half shows the conversation in its own language,
 * with buttons and messages in that language, and has its own microphone button.
 */
@Composable
fun ConversationScreen(container: AppContainer, withMic: (() -> Unit) -> Unit, openPacks: () -> Unit) {
    val engine = container.engine
    val state by engine.state.collectAsStateWithLifecycle()
    val turns by engine.turns.collectAsStateWithLifecycle()
    val packStates by container.packs.states.collectAsStateWithLifecycle()
    val level by container.micLevel.collectAsStateWithLifecycle()
    val diagnostics by container.diagnostics.collectAsStateWithLifecycle()
    val preparing by container.preparing.collectAsStateWithLifecycle()
    val muted by container.mutedSeats.collectAsStateWithLifecycle()
    val voicesVersion by container.voicesVersion.collectAsStateWithLifecycle()
    var seatLangs by remember { mutableStateOf(engine.seats.map { it.lang }) }
    // Re-evaluated whenever a pack finishes installing.
    val ready = remember(packStates) { Lang.entries.associateWith { container.packs.isLanguageReady(it) } }
    var big by remember { mutableStateOf<BigText?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    /** The seat whose keyboard input is open: its half gets more room above the keyboard. */
    var typingSeat by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(ready) {
        container.preload()
        engine.catchUpTranslations() // a newly installed pack can fill gaps in earlier turns
        container.refreshVoices() // the Supertonic pack may have just been installed
    }

    big?.let { b ->
        turns.firstOrNull { it.id == b.turnId }?.let { turn ->
            BigTextDialog(turn, b.readerSeat, seatLangs, onRotate = { big = b.copy(readerSeat = 1 - b.readerSeat) }) { big = null }
        }
    }
    if (confirmClear) {
        val s = SeatStrings.of(seatLangs[0])
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(s.clearTitle) },
            text = { Text(s.clearMessage) },
            confirmButton = {
                TextButton(onClick = {
                    engine.clear()
                    confirmClear = false
                }) { Text(s.clear) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(s.cancel) } },
        )
    }

    val onLang = { seat: Int, lang: Lang ->
        container.setSeatLang(seat, lang)
        seatLangs = container.engine.seats.map { it.lang }
    }
    val onMic = { seat: Int ->
        if (state is EngineState.Listening && (state as EngineState.Listening).seat == seat) engine.stopListening()
        else withMic { engine.startListening(seat) }
    }
    val layout by container.layout.collectAsStateWithLifecycle()
    if (layout == ConversationLayout.LIST) {
        ListConversation(
            container = container,
            state = state,
            turns = turns,
            seatLangs = seatLangs,
            missing = seatLangs.distinct().filterNot { ready.getValue(it) },
            level = level,
            preparing = preparing,
            diagnostics = diagnostics,
            muted = muted,
            onLang = onLang,
            onMic = onMic,
            onType = engine::submitText,
            // In one shared list a sentence is enlarged for the other person.
            onOpen = { turn -> big = BigText(turn.id, readerSeat = 1 - turn.seat) },
            onClear = { confirmClear = true },
            openPacks = openPacks,
        )
        return
    }

    // imePadding: with edge-to-edge the keyboard would otherwise cover the input field and buttons.
    Column(Modifier.fillMaxSize().imePadding()) {
        for (seat in listOf(1, 0)) {
            val weight = when (typingSeat) {
                null -> 1f
                seat -> 1.6f
                else -> 0.6f
            }
            val modifier = Modifier.weight(weight).fillMaxWidth().let { if (isFlipped(seat)) it.rotate(180f) else it }
            SeatPanel(
                modifier = modifier,
                seat = seat,
                typing = typingSeat == seat,
                onTyping = { on -> typingSeat = if (on) seat else null },
                lang = seatLangs[seat],
                otherLang = seatLangs[1 - seat],
                missing = listOf(seatLangs[seat], seatLangs[1 - seat]).distinct().filterNot { ready.getValue(it) },
                turns = turns,
                state = state,
                level = level,
                preparing = preparing,
                diagnostics = diagnostics,
                muted = seat in muted,
                voicesVersion = voicesVersion,
                onToggleMute = { container.toggleMute(seat) },
                container = container,
                onLang = { lang -> onLang(seat, lang) },
                onMic = { onMic(seat) },
                onType = { text -> engine.submitText(seat, text) },
                onReplay = { turn -> engine.replay(turn, seatLangs[seat]) },
                // Your own sentence is shown to the person opposite; someone else's sentence to you.
                onOpen = { turn -> big = BigText(turn.id, readerSeat = if (turn.seat == seat) 1 - seat else seat) },
                onClear = { confirmClear = true },
                openPacks = openPacks,
            )
            if (seat == 1) HorizontalDivider(thickness = 2.dp)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SeatPanel(
    modifier: Modifier,
    seat: Int,
    typing: Boolean,
    onTyping: (Boolean) -> Unit,
    lang: Lang,
    otherLang: Lang,
    /** Languages of this half or the other whose packs are not installed: input stays disabled until then. */
    missing: List<Lang>,
    turns: List<Turn>,
    state: EngineState,
    level: Float,
    preparing: Boolean,
    diagnostics: Boolean,
    muted: Boolean,
    voicesVersion: Int,
    onToggleMute: () -> Unit,
    container: AppContainer,
    onLang: (Lang) -> Unit,
    onMic: () -> Unit,
    onType: (String) -> Unit,
    onReplay: (Turn) -> Unit,
    onOpen: (Turn) -> Unit,
    onClear: () -> Unit,
    openPacks: () -> Unit,
) {
    val s = SeatStrings.of(lang)
    val ready = missing.isEmpty()
    var text by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    LaunchedEffect(turns.size) { if (turns.isNotEmpty()) listState.animateScrollToItem(turns.lastIndex) }
    var route by remember(lang) { mutableStateOf<TtsRoute?>(null) }
    LaunchedEffect(lang, voicesVersion) { route = runCatching { container.tts.route(lang) }.getOrNull() }

    Column(modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!typing) {
            // Wrapping instead of a horizontal scroll: in the rotated half a scroll flings the wrong way, and all
            // five languages plus the speaker toggle fit in two lines.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.Center) {
                Lang.entries.forEach { l -> FilterChip(selected = l == lang, onClick = { onLang(l) }, label = { Text(l.nativeName) }) }
                // Read translations into this seat's language aloud, or only show them.
                TextButton(onClick = onToggleMute, modifier = Modifier.semantics { contentDescription = s.readAloud }) {
                    Text(if (muted) "🔇" else "🔊", fontSize = 20.sp)
                }
            }
        }
        if (route?.offline == false && !muted) NoVoiceNotice(lang, s, container, openPacks)
        // The conversation stays visible without packs; only this side's input is disabled.
        if (!ready) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${s.packMissing}: ${missing.joinToString { it.nativeName }}",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = openPacks) { Text(s.openPacks, fontSize = 13.sp) }
            }
        }

        // In the rotated (top) panel the list is turned back upright, laid out in reverse and only its items are
        // rotated: scrolling inside a 180° graphicsLayer flings in the wrong direction on release.
        val flipped = isFlipped(seat)
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().let { if (flipped) it.rotate(180f) else it },
            state = listState,
            reverseLayout = flipped,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(turns, key = { it.id }) { turn ->
                Box(if (flipped) Modifier.rotate(180f) else Modifier) {
                    TurnBubble(turn, seat, lang, otherLang, ready, diagnostics, s, onReplay = { onReplay(turn) }, onOpen = { onOpen(turn) })
                }
            }
        }

        StatusLine(state, seat, level, preparing, s)

        if (typing && ready) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("${s.placeholder} (${lang.nativeName})") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    onType(text)
                    text = ""
                }),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { onTyping(!typing) }, enabled = ready) { Text(if (typing) s.hideKeyboard else "⌨ ${s.type}") }
            // Only the phone holder's half (bottom) offers clearing, so the guest cannot wipe it by accident.
            if (seat == 0 && turns.isNotEmpty()) TextButton(onClick = onClear) { Text("🗑 ${s.clear}") }
            val listening = state is EngineState.Listening && state.seat == seat
            val busyElsewhere = when (state) {
                is EngineState.Listening -> state.seat != seat
                is EngineState.Recognizing, is EngineState.Translating -> true
                else -> false
            }
            Button(
                onClick = onMic,
                enabled = ready && !busyElsewhere,
                shape = CircleShape,
                modifier = Modifier.weight(1f).height(64.dp),
                colors = if (listening) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) else ButtonDefaults.buttonColors(),
            ) { Text(if (listening) "■ ${s.stop}" else "🎤 ${s.speak}", fontSize = 20.sp, maxLines = 1) }
        }
    }
}

/**
 * One turn as read from [seat]: the sentence in [lang] on top, below it what the other side sees (for your
 * own sentences) or the original (for theirs) — never the same text twice. Tap to show it full screen.
 */
@Composable
private fun TurnBubble(
    turn: Turn,
    seat: Int,
    lang: Lang,
    otherLang: Lang,
    canTranslate: Boolean,
    diagnostics: Boolean,
    s: SeatStrings,
    onReplay: () -> Unit,
    onOpen: () -> Unit,
) {
    val mine = turn.seat == seat
    val inMyLang = turn.textIn(lang)
    val main = inMyLang ?: turn.source
    val secondary = (if (mine) listOfNotNull(turn.textIn(otherLang)) else listOf(turn.source)).filter { it != main }
    Box(Modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
        Column(
            Modifier
                .clip(MaterialTheme.shapes.medium)
                .background(if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer)
                .clickable(onClick = onOpen)
                .padding(10.dp),
        ) {
            Text(main, fontSize = if (mine) 16.sp else 22.sp)
            secondary.forEach { Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (inMyLang == null && canTranslate) {
                Text(s.translatingLater, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (diagnostics) {
                val t = turn.timings
                Text(
                    if (turn.typed) "⌨ · MT ${t.mtMs} ms"
                    else "%.1f s · ASR %d ms · MT %d ms".format(Locale.US, t.audioSec, t.asrMs, t.mtMs),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!mine && inMyLang != null) {
                TextButton(onClick = onReplay, contentPadding = ButtonDefaults.TextButtonContentPadding) { Text("🔊 ${s.replay}", fontSize = 13.sp) }
            }
        }
    }
}

/** No offline voice for [lang]: offer the system's voice data installer and, where it helps, the Supertonic pack. */
@Composable
internal fun NoVoiceNotice(lang: Lang, s: SeatStrings, container: AppContainer, openPacks: () -> Unit) {
    val context = LocalContext.current
    Column {
        Text(s.noOfflineVoice, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = {
                val install = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(install) }
                    .onFailure { runCatching { context.startActivity(Intent("com.android.settings.TTS_SETTINGS")) } }
            }) { Text(s.installVoice, fontSize = 13.sp) }
            if (lang in SupertonicTts.LANGS && !container.packs.isInstalled("tts-supertonic")) {
                TextButton(onClick = openPacks) { Text(s.backupVoice, fontSize = 13.sp) }
            }
        }
    }
}

/** A turn shown full screen to [readerSeat]: in that seat's language and turned towards it. */
internal data class BigText(val turnId: Long, val readerSeat: Int)

@Composable
internal fun BigTextDialog(turn: Turn, readerSeat: Int, seatLangs: List<Lang>, onRotate: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val lang = seatLangs[readerSeat]
    val s = SeatStrings.of(lang)
    val text = turn.textIn(lang) ?: turn.source
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(
                Modifier.fillMaxSize().let { if (isFlipped(readerSeat)) it.rotate(180f) else it }.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(lang.nativeName, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    BasicText(
                        text,
                        // Line height relative to the auto-sized font: Vietnamese stacked diacritics overlap with
                        // displayLarge's fixed 64sp line height once the text grows.
                        style = MaterialTheme.typography.displayLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            lineHeight = 1.3.em,
                        ),
                        autoSize = TextAutoSize.StepBased(minFontSize = 20.sp, maxFontSize = 96.sp),
                    )
                }
                if (turn.source != text) Text(turn.source, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    // Rotating hands the screen to the person on the other side, in their language.
                    TextButton(onClick = onRotate) { Text("⟲ ${s.rotate}") }
                    TextButton(onClick = {
                        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(lang.nativeName, text))
                        Toast.makeText(context, s.copied, Toast.LENGTH_SHORT).show()
                    }) { Text("⧉ ${s.copy}") }
                    Spacer(Modifier.weight(1f))
                    Button(onClick = onDismiss) { Text(s.close) }
                }
            }
        }
    }
}

@Composable
internal fun StatusLine(state: EngineState, seat: Int, level: Float, preparing: Boolean, s: SeatStrings) {
    val text = when (state) {
        is EngineState.Listening -> if (state.seat == seat) s.listening else s.otherSpeaking
        is EngineState.Recognizing -> s.recognizing
        is EngineState.Translating -> s.translating
        is EngineState.Speaking -> s.speakingTranslation
        is EngineState.NothingHeard -> if (state.seat == seat) s.nothingHeard else ""
        is EngineState.Failed -> "${s.error}: ${state.message}"
        EngineState.Idle -> if (preparing) s.preparing else ""
    }
    Column(Modifier.fillMaxWidth()) {
        if (state is EngineState.Listening && state.seat == seat) {
            LinearProgressIndicator(progress = { level }, modifier = Modifier.fillMaxWidth().height(4.dp))
        }
        if (text.isNotEmpty()) Text(text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
