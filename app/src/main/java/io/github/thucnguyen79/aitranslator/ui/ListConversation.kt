package io.github.thucnguyen79.aitranslator.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.thucnguyen79.aitranslator.AppContainer
import io.github.thucnguyen79.aitranslator.core.engine.EngineState
import io.github.thucnguyen79.aitranslator.core.engine.Turn
import io.github.thucnguyen79.aitranslator.core.model.Lang
import java.util.Locale

/**
 * "Pass the phone" layout: one upright chat list both people read, each sentence with its translation, and a
 * microphone / keyboard per person labelled in that person's language.
 */
@Composable
internal fun ListConversation(
    container: AppContainer,
    state: EngineState,
    turns: List<Turn>,
    seatLangs: List<Lang>,
    names: List<String>,
    missing: List<Lang>,
    level: Float,
    preparing: Boolean,
    diagnostics: Boolean,
    muted: Set<Int>,
    handsFree: Boolean,
    onHandsFree: (Boolean) -> Unit,
    onLang: (seat: Int, Lang) -> Unit,
    onMic: (seat: Int) -> Unit,
    onType: (seat: Int, text: String) -> Unit,
    onOpen: (Turn) -> Unit,
    onEdit: (Turn) -> Unit,
    onPhrasebook: (seat: Int) -> Unit,
    onClear: () -> Unit,
    openPacks: () -> Unit,
) {
    val holder = SeatStrings.of(seatLangs[0])
    val ready = missing.isEmpty()
    var typingSeat by remember { mutableStateOf<Int?>(null) }
    var text by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    // Also when the last turn grows (late translation, back-translation), so its bottom stays in view.
    LaunchedEffect(turns.size, turns.lastOrNull()) { if (turns.isNotEmpty()) listState.animateScrollToItem(turns.lastIndex) }
    val label = { seat: Int -> names.getOrNull(seat)?.takeIf { it.isNotBlank() } ?: "${seat + 1}" }

    Column(Modifier.fillMaxSize().imePadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (typingSeat == null) {
            for (seat in seatLangs.indices) {
                Row(
                    Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).background(seatTint(seat)).padding(start = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(label(seat), fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.padding(end = 6.dp), color = seatColor(seat))
                    Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Lang.entries.forEach { l ->
                            FilterChip(selected = l == seatLangs[seat], onClick = { onLang(seat, l) }, label = { Text(l.nativeName) })
                        }
                    }
                    TextButton(onClick = { container.toggleMute(seat) }) { Text(if (seat in muted) "🔇" else "🔊", fontSize = 20.sp) }
                }
            }
        }
        if (!ready) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${holder.packMissing}: ${missing.joinToString { it.nativeName }}",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = openPacks) { Text(holder.openPacks, fontSize = 13.sp) }
            }
        }

        // The shared conversation sits on its own background, apart from each person's controls.
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainerHigh),
            state = listState,
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(turns, key = { it.id }) { turn ->
                ListBubble(turn, seatLangs, label(turn.seat), diagnostics, ready, onOpen = { onOpen(turn) }, onEdit = { onEdit(turn) })
            }
        }

        val speaking = listOfNotNull((state as? EngineState.Listening)?.seat, (state as? EngineState.NothingHeard)?.seat)
            .firstOrNull { it >= 0 } ?: 0
        StatusLine(state, speaking, level, preparing, SeatStrings.of(seatLangs[speaking]))

        typingSeat?.let { seat ->
            val s = SeatStrings.of(seatLangs[seat])
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("${s.placeholder} (${seatLangs[seat].nativeName})") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    onType(seat, text)
                    text = ""
                }),
            )
        }

        for (seat in seatLangs.indices) {
            val s = SeatStrings.of(seatLangs[seat])
            Row(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).background(seatTint(seat)),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { typingSeat = if (typingSeat == seat) null else seat }, enabled = ready) {
                    Text(if (typingSeat == seat) s.hideKeyboard else "⌨ ${s.type}", fontSize = 13.sp)
                }
                TextButton(onClick = { onPhrasebook(seat) }, modifier = Modifier.semantics { contentDescription = s.phrasebook }) {
                    Text("📖", fontSize = 18.sp)
                }
                MicButton(
                    state, seat, ready, s,
                    Modifier.weight(1f).height(if (seatLangs.size > 2) 44.dp else 52.dp),
                    onClick = { onMic(seat) },
                    label = "${label(seat)} · 🎤 ${s.speak} · ${seatLangs[seat].nativeName}",
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            if (handsFree) {
                Text("🎧 ${holder.handsFree}", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
            }
            TextButton(onClick = { onHandsFree(!handsFree) }, enabled = ready) {
                Text(if (handsFree) holder.handsFreeOff else "🎧 ${holder.handsFree}", fontSize = 13.sp)
            }
            if (turns.isNotEmpty()) TextButton(onClick = onClear) { Text("🗑 ${holder.clear}", fontSize = 13.sp) }
        }
    }
}

/**
 * The original sentence with its translation(s) below, aligned by speaker and tinted with the speaker's colour.
 * Tap to enlarge for the other person, long-press (or ✎) to correct it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ListBubble(turn: Turn, seatLangs: List<Lang>, speaker: String, diagnostics: Boolean, canEdit: Boolean, onOpen: () -> Unit, onEdit: () -> Unit) {
    val first = turn.seat == 0
    val others = seatLangs.distinct().filter { it != turn.from }.mapNotNull { lang -> turn.textIn(lang)?.let { lang to it } }
    val s = SeatStrings.of(turn.from)
    Box(Modifier.fillMaxWidth(), contentAlignment = if (first) Alignment.CenterEnd else Alignment.CenterStart) {
        Column(
            Modifier
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surface)
                .border(2.dp, seatColor(turn.seat).copy(alpha = 0.6f), MaterialTheme.shapes.medium)
                .combinedClickable(onClick = onOpen, onLongClick = if (canEdit) onEdit else null)
                .padding(10.dp),
        ) {
            if (seatLangs.size > 2 || speaker != "${turn.seat + 1}") Text(speaker, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = seatColor(turn.seat))
            Text(turn.source, fontSize = 17.sp)
            others.forEach { (lang, text) ->
                Text(text, fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                turn.backTranslations[lang]?.let { BackTranslation(it, s) }
            }
            if (diagnostics) {
                val t = turn.timings
                Text(
                    if (turn.typed) "⌨ · MT ${t.mtMs} ms" else "%.1f s · ASR %d ms · MT %d ms".format(Locale.US, t.audioSec, t.asrMs, t.mtMs),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (canEdit) {
                TextButton(onClick = onEdit, contentPadding = ButtonDefaults.TextButtonContentPadding) { Text("✎ ${s.edit}", fontSize = 13.sp) }
            }
        }
    }
}
