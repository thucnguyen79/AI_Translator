package io.github.thucnguyen79.aitranslator.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
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
import io.github.thucnguyen79.aitranslator.core.engine.ANY_SEAT
import io.github.thucnguyen79.aitranslator.core.engine.EngineState
import io.github.thucnguyen79.aitranslator.core.engine.Turn
import io.github.thucnguyen79.aitranslator.core.model.Lang
import io.github.thucnguyen79.aitranslator.core.tts.SupertonicTts
import io.github.thucnguyen79.aitranslator.core.tts.TtsRoute
import java.util.Locale

/**
 * Face to face: two people across the phone, the top half turned to the person opposite. Around the table:
 * 3–4 people, one area turned to each side (F6.2). Each area shows the conversation in its own language, with
 * buttons and messages in that language, and has its own microphone button. The list layout is in
 * [ListConversation].
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
    val seatLangs by container.seatLangs.collectAsStateWithLifecycle()
    val names by container.seatNames.collectAsStateWithLifecycle()
    val handsFree by container.handsFree.collectAsStateWithLifecycle()
    val layout by container.layout.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val phrases = remember { Phrasebook.load(context) }
    // Re-evaluated whenever a pack finishes installing.
    val ready = remember(packStates) { Lang.entries.associateWith { container.packs.isLanguageReady(it) } }
    val canDetect = remember(packStates) { container.activeLidPack() != null }
    var big by remember { mutableStateOf<BigText?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    /** The seat whose phrasebook is open. */
    var phrasebookSeat by remember { mutableStateOf<Int?>(null) }
    /** The turn being corrected (F6.5). */
    var editing by remember { mutableStateOf<Turn?>(null) }
    /** The seat whose keyboard input is open: its half gets more room above the keyboard. */
    var typingSeat by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(ready) {
        container.preload()
        engine.catchUpTranslations() // a newly installed pack can fill gaps in earlier turns
        container.refreshVoices() // the Supertonic pack may have just been installed
    }
    if (seatLangs.size < 2) return
    // Dialogs are turned towards their reader, except in the upright list layout.
    val dialogTurn = { seat: Int -> if (layout == ConversationLayout.LIST) 0 else seatTurn(seat, layout) }

    big?.let { b ->
        turns.firstOrNull { it.id == b.turnId }?.let { turn ->
            BigTextDialog(
                turn, b.readerSeat, seatLangs, degrees = seatTurn(b.readerSeat, layout),
                // Rotating hands the screen to the next person, in their language.
                onRotate = { big = b.copy(readerSeat = (b.readerSeat + 1) % seatLangs.size) },
            ) { big = null }
        }
    }
    phrasebookSeat?.let { seat ->
        PhrasebookDialog(
            phrases = phrases,
            lang = seatLangs[seat],
            otherLang = seatLangs.firstOrNull { it != seatLangs[seat] } ?: seatLangs[seat],
            turn = dialogTurn(seat),
            s = SeatStrings.of(seatLangs[seat]),
            onPick = { phrase ->
                phrasebookSeat = null
                engine.submitPhrase(seat, phrase.texts)
            },
            onDismiss = { phrasebookSeat = null },
        )
    }
    editing?.let { turn ->
        EditDialog(
            text = turn.source,
            s = SeatStrings.of(turn.from),
            turn = dialogTurn(turn.seat),
            onSave = { text ->
                engine.editTurn(turn.id, text)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
    if (confirmClear) {
        val s = SeatStrings.of(seatLangs[0])
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(s.clearTitle) },
            text = { Text(s.clearMessage) },
            confirmButton = {
                TextButton(onClick = {
                    container.clearConversation()
                    confirmClear = false
                }) { Text(s.clear) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(s.cancel) } },
        )
    }

    val onMic = { seat: Int ->
        if (state is EngineState.Listening && (state as EngineState.Listening).seat == seat) engine.stopListening()
        else withMic { engine.startListening(seat) }
    }
    val onHandsFree = { on: Boolean -> if (on) withMic { container.setHandsFree(true) } else container.setHandsFree(false) }
    if (layout == ConversationLayout.LIST) {
        ListConversation(
            container = container,
            state = state,
            turns = turns,
            seatLangs = seatLangs,
            names = names,
            missing = seatLangs.distinct().filterNot { ready.getValue(it) },
            level = level,
            preparing = preparing,
            diagnostics = diagnostics,
            muted = muted,
            handsFree = handsFree,
            onHandsFree = onHandsFree,
            onLang = container::setSeatLang,
            onMic = onMic,
            onType = engine::submitText,
            // In one shared list a sentence is enlarged for the other person.
            onOpen = { turn -> big = BigText(turn.id, readerSeat = if (turn.seat == 0) 1 else 0) },
            onEdit = { editing = it },
            onPhrasebook = { phrasebookSeat = it },
            onClear = { confirmClear = true },
            openPacks = openPacks,
        )
        return
    }

    @Composable
    fun Panel(seat: Int, modifier: Modifier) {
        val lang = seatLangs[seat]
        val others = seatLangs.filterIndexed { i, _ -> i != seat }
        // Your own sentences show what the person opposite reads.
        val opposite = seatLangs.getOrNull(if (seat < 2) 1 - seat else 5 - seat)?.takeIf { it != lang }
        SeatPanel(
            modifier = modifier,
            seat = seat,
            turn = seatTurn(seat, layout),
            typing = typingSeat == seat,
            onTyping = { on -> typingSeat = if (on) seat else null },
            lang = lang,
            otherLang = opposite ?: others.firstOrNull { it != lang } ?: lang,
            name = names[seat],
            missing = (others + lang).distinct().filterNot { ready.getValue(it) },
            turns = turns,
            state = state,
            level = level,
            preparing = preparing,
            diagnostics = diagnostics,
            muted = seat in muted,
            voicesVersion = voicesVersion,
            // Only the phone holder (seat 0) can clear or start hands-free, so guests cannot do it by accident.
            holder = if (seat == 0) HolderActions(handsFree, canDetect, onHandsFree) else null,
            onToggleMute = { container.toggleMute(seat) },
            container = container,
            onLang = { l -> container.setSeatLang(seat, l) },
            onMic = { onMic(seat) },
            onType = { text -> engine.submitText(seat, text) },
            onReplay = { turn -> engine.replay(turn, lang) },
            // Your own sentence is shown to the next person; someone else's sentence to you.
            onOpen = { turn -> big = BigText(turn.id, readerSeat = if (turn.seat == seat) (seat + 1) % seatLangs.size else seat) },
            onEdit = { editing = it },
            onPhrasebook = { phrasebookSeat = seat },
            onClear = { confirmClear = true },
            openPacks = openPacks,
        )
    }

    // imePadding: with edge-to-edge the keyboard would otherwise cover the input field and buttons.
    Column(Modifier.fillMaxSize().imePadding()) {
        fun weightOf(vararg seats: Int, base: Float) = when (val typing = typingSeat) {
            null -> base
            in seats -> base * 1.6f
            else -> base * 0.6f
        }
        val around = layout == ConversationLayout.AROUND && seatLangs.size > 2
        Panel(1, Modifier.weight(weightOf(1, base = if (around) 0.28f else 1f)).fillMaxWidth().turned(180))
        HorizontalDivider(thickness = 2.dp)
        if (around) {
            Row(Modifier.weight(weightOf(2, 3, base = 0.44f)).fillMaxWidth()) {
                Panel(2, Modifier.weight(1f).fillMaxHeight().turned(90))
                if (seatLangs.size > 3) Panel(3, Modifier.weight(1f).fillMaxHeight().turned(270))
            }
            HorizontalDivider(thickness = 2.dp)
        }
        Panel(0, Modifier.weight(weightOf(0, base = if (around) 0.28f else 1f)).fillMaxWidth())
    }
}

/** What only the phone holder's area offers: hands-free mode and clearing. */
private class HolderActions(val handsFree: Boolean, val canDetect: Boolean, val setHandsFree: (Boolean) -> Unit)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SeatPanel(
    modifier: Modifier,
    seat: Int,
    /** How far this area is turned (0, 90, 180, 270). */
    turn: Int,
    typing: Boolean,
    onTyping: (Boolean) -> Unit,
    lang: Lang,
    otherLang: Lang,
    name: String,
    /** Languages at the table whose packs are not installed: input stays disabled until then. */
    missing: List<Lang>,
    turns: List<Turn>,
    state: EngineState,
    level: Float,
    preparing: Boolean,
    diagnostics: Boolean,
    muted: Boolean,
    voicesVersion: Int,
    holder: HolderActions?,
    onToggleMute: () -> Unit,
    container: AppContainer,
    onLang: (Lang) -> Unit,
    onMic: () -> Unit,
    onType: (String) -> Unit,
    onReplay: (Turn) -> Unit,
    onOpen: (Turn) -> Unit,
    onEdit: (Turn) -> Unit,
    onPhrasebook: () -> Unit,
    onClear: () -> Unit,
    openPacks: () -> Unit,
) {
    val s = SeatStrings.of(lang)
    val ready = missing.isEmpty()
    var text by remember { mutableStateOf("") }
    var choosingLang by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    // Also when the last turn grows (late translation, back-translation), so its bottom stays in view.
    LaunchedEffect(turns.size, turns.lastOrNull()) { if (turns.isNotEmpty()) listState.animateScrollToItem(turns.lastIndex) }
    var route by remember(lang) { mutableStateOf<TtsRoute?>(null) }
    LaunchedEffect(lang, voicesVersion) { route = runCatching { container.tts.route(lang) }.getOrNull() }

    Column(modifier.background(seatTint(seat)).padding(horizontal = 10.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
        if (holder?.handsFree == true) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "🎧 ${if (holder.canDetect) s.handsFreeAnyone else s.handsFreeTurns}",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { holder.setHandsFree(false) }) { Text(s.handsFreeOff, fontSize = 13.sp) }
            }
        }

        // Sideways areas are narrow: typing and the speaker toggle move from the toolbar into the language picker.
        val narrow = turn % 180 == 90
        if (choosingLang) {
            // Chosen in place, so the choice is turned towards this person like the rest of the area.
            FlowRow(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Lang.entries.forEach { l ->
                    FilterChip(selected = l == lang, onClick = {
                        onLang(l)
                        choosingLang = false
                    }, label = { Text(l.nativeName, fontSize = 16.sp) })
                }
                if (narrow) {
                    ToolIcon(if (muted) "🔇" else "🔊", s.readAloud, onClick = onToggleMute)
                    ToolIcon(if (typing) "✕" else "⌨", if (typing) s.hideKeyboard else s.type, enabled = ready) {
                        onTyping(!typing)
                        choosingLang = false
                    }
                }
            }
        } else {
            // In the half-turned (top) area the list is turned back upright, laid out in reverse and only its items
            // are rotated: scrolling inside a 180° graphicsLayer flings in the wrong direction on release. Sideways
            // areas do not fling at all (see NoFling).
            val flipped = turn == 180
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth().let { if (flipped) it.rotate(180f) else it },
                state = listState,
                reverseLayout = flipped,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                flingBehavior = if (turn % 180 == 90) NoFling else ScrollableDefaults.flingBehavior(),
            ) {
                items(turns, key = { it.id }) { t ->
                    Box(if (flipped) Modifier.rotate(180f) else Modifier) {
                        TurnBubble(
                            t, seat, lang, otherLang, ready, diagnostics, s,
                            onReplay = { onReplay(t) }, onOpen = { onOpen(t) }, onEdit = { onEdit(t) },
                        )
                    }
                }
            }
        }

        StatusLine(state, seat, level, preparing, s, name)

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
        // One toolbar instead of a row of language chips: more room for the conversation.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                onClick = { choosingLang = !choosingLang },
                contentPadding = ButtonDefaults.TextButtonContentPadding,
                modifier = Modifier.semantics { contentDescription = s.language },
            ) { Text("${lang.nativeName} ▾", maxLines = 1) }
            if (!narrow || typing) ToolIcon(if (typing) "✕" else "⌨", if (typing) s.hideKeyboard else s.type, enabled = ready) { onTyping(!typing) }
            // Phrases carry their own translations, so they work before any pack is installed.
            ToolIcon("📖", s.phrasebook, onClick = onPhrasebook)
            // Read translations into this seat's language aloud, or only show them.
            if (!narrow) ToolIcon(if (muted) "🔇" else "🔊", s.readAloud, onClick = onToggleMute)
            if (holder != null) {
                Box {
                    ToolIcon("⋮", s.more) { menu = true }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("🎧 ${if (holder.handsFree) s.handsFreeOff else s.handsFree}") },
                            enabled = ready,
                            onClick = {
                                menu = false
                                holder.setHandsFree(!holder.handsFree)
                            },
                        )
                        DropdownMenuItem(text = { Text("🗑 ${s.clear}") }, enabled = turns.isNotEmpty(), onClick = {
                            menu = false
                            onClear()
                        })
                    }
                }
            }
            MicButton(state, seat, ready, s, Modifier.weight(1f).height(52.dp), onMic)
        }
    }
}

@Composable
private fun ToolIcon(icon: String, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(40.dp).semantics { contentDescription = label }) {
        Text(icon, fontSize = 20.sp)
    }
}

/** The seat's microphone: pressing it while listening ends the utterance; its label shrinks to fit. */
@Composable
internal fun MicButton(state: EngineState, seat: Int, ready: Boolean, s: SeatStrings, modifier: Modifier, onClick: () -> Unit, label: String? = null) {
    val listening = state is EngineState.Listening && state.seat == seat
    val busyElsewhere = when (state) {
        is EngineState.Listening -> state.seat != seat && state.seat != ANY_SEAT
        is EngineState.Recognizing, is EngineState.Translating -> true
        else -> false
    }
    Button(
        onClick = onClick,
        enabled = ready && !busyElsewhere,
        shape = CircleShape,
        modifier = modifier,
        contentPadding = ButtonDefaults.TextButtonContentPadding,
        colors = ButtonDefaults.buttonColors(containerColor = if (listening) MaterialTheme.colorScheme.error else seatColor(seat), contentColor = Color.White),
    ) {
        BasicText(
            if (listening) "■ ${s.stop}" else label ?: "🎤 ${s.speak}",
            style = TextStyle(color = LocalContentColor.current),
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = 20.sp),
        )
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
    onEdit: () -> Unit,
) {
    val mine = turn.seat == seat
    val inMyLang = turn.textIn(lang)
    val main = inMyLang ?: turn.source
    val secondary = (if (mine) listOfNotNull(turn.textIn(otherLang)) else listOf(turn.source)).filter { it != main }
    Box(Modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
        Column(
            Modifier
                .clip(MaterialTheme.shapes.medium)
                .background(if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                .clickable(onClick = onOpen)
                .padding(10.dp),
        ) {
            Text(main, fontSize = if (mine) 16.sp else 22.sp)
            secondary.forEach { Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            // What the other side read, translated back: lets the speaker spot a mistranslation (F4.5).
            if (mine) turn.backTranslations[otherLang]?.let { BackTranslation(it, s) }
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
            if (mine && canTranslate) {
                TextButton(onClick = onEdit, contentPadding = ButtonDefaults.TextButtonContentPadding) { Text("✎ ${s.edit}", fontSize = 13.sp) }
            }
        }
    }
}

@Composable
internal fun BackTranslation(text: String, s: SeatStrings) {
    Text("↩ ${s.backTranslation}: $text", fontSize = 12.sp, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Corrects a misrecognised sentence (F6.5), turned ([turn] degrees) towards the person who said it. */
@Composable
internal fun EditDialog(text: String, s: SeatStrings, turn: Int, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var value by remember { mutableStateOf(text) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.turned(turn).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("✎ ${s.edit}", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(value = value, onValueChange = { value = it }, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(s.cancel) }
                    TextButton(onClick = { onSave(value) }, enabled = value.isNotBlank()) { Text(s.save) }
                }
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
internal fun BigTextDialog(turn: Turn, readerSeat: Int, seatLangs: List<Lang>, degrees: Int, onRotate: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val lang = seatLangs[readerSeat]
    val s = SeatStrings.of(lang)
    val text = turn.textIn(lang) ?: turn.source
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().turned(degrees).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                    // Rotating hands the screen to the next person at the table, in their language.
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

/** What is happening, from [seat]'s point of view, after the seat's [name] when it has one. */
@Composable
internal fun StatusLine(state: EngineState, seat: Int, level: Float, preparing: Boolean, s: SeatStrings, name: String = "") {
    val mine = state.let { it is EngineState.Listening && (it.seat == seat || it.seat == ANY_SEAT) }
    val text = when (state) {
        is EngineState.Listening -> when (state.seat) {
            ANY_SEAT -> s.listeningAnyone
            seat -> s.listening
            else -> s.otherSpeaking
        }
        is EngineState.Recognizing -> s.recognizing
        is EngineState.Translating -> s.translating
        is EngineState.Speaking -> s.speakingTranslation
        is EngineState.NothingHeard -> if (state.seat == seat) s.nothingHeard else ""
        is EngineState.Failed -> "${s.error}: ${state.message}"
        EngineState.Idle -> if (preparing) s.preparing else ""
    }
    Column(Modifier.fillMaxWidth()) {
        if (mine) LinearProgressIndicator(progress = { level }, modifier = Modifier.fillMaxWidth().height(4.dp))
        if (text.isNotEmpty() || name.isNotBlank()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (name.isNotBlank()) Text(name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = seatColor(seat))
                Text(text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
