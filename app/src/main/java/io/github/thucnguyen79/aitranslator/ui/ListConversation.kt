package io.github.thucnguyen79.aitranslator.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
    missing: List<Lang>,
    level: Float,
    preparing: Boolean,
    diagnostics: Boolean,
    muted: Set<Int>,
    onLang: (seat: Int, Lang) -> Unit,
    onMic: (seat: Int) -> Unit,
    onType: (seat: Int, text: String) -> Unit,
    onOpen: (Turn) -> Unit,
    onClear: () -> Unit,
    openPacks: () -> Unit,
) {
    val holder = SeatStrings.of(seatLangs[0])
    val ready = missing.isEmpty()
    var typingSeat by remember { mutableStateOf<Int?>(null) }
    var text by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    LaunchedEffect(turns.size) { if (turns.isNotEmpty()) listState.animateScrollToItem(turns.lastIndex) }

    Column(Modifier.fillMaxSize().imePadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (typingSeat == null) {
            for (seat in seatLangs.indices) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${seat + 1}", fontSize = 16.sp, modifier = Modifier.padding(end = 6.dp), color = seatColor(seat))
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
        HorizontalDivider()

        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(turns, key = { it.id }) { turn -> ListBubble(turn, seatLangs, diagnostics) { onOpen(turn) } }
        }

        val speaking = (state as? EngineState.Listening)?.seat
            ?: (state as? EngineState.NothingHeard)?.seat
            ?: 0
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
            val listening = state is EngineState.Listening && state.seat == seat
            val busyElsewhere = when (state) {
                is EngineState.Listening -> state.seat != seat
                is EngineState.Recognizing, is EngineState.Translating -> true
                else -> false
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { typingSeat = if (typingSeat == seat) null else seat }, enabled = ready) {
                    Text(if (typingSeat == seat) s.hideKeyboard else "⌨ ${s.type}", fontSize = 13.sp)
                }
                Button(
                    onClick = { onMic(seat) },
                    enabled = ready && !busyElsewhere,
                    shape = CircleShape,
                    modifier = Modifier.weight(1f).height(52.dp),
                    colors = when {
                        listening -> ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        seat == 1 -> ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                        else -> ButtonDefaults.buttonColors()
                    },
                ) {
                    Text(
                        if (listening) "■ ${s.stop}" else "🎤 ${s.speak} · ${seatLangs[seat].nativeName}",
                        fontSize = 16.sp,
                        maxLines = 1,
                    )
                }
            }
        }
        if (turns.isNotEmpty()) {
            TextButton(onClick = onClear, modifier = Modifier.align(Alignment.End)) { Text("🗑 ${holder.clear}", fontSize = 13.sp) }
        }
    }
}

@Composable
private fun seatColor(seat: Int) = if (seat == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary

/** The original sentence with its translation(s) below, aligned by speaker. Tap to enlarge for the other person. */
@Composable
private fun ListBubble(turn: Turn, seatLangs: List<Lang>, diagnostics: Boolean, onOpen: () -> Unit) {
    val first = turn.seat == 0
    val others = seatLangs.distinct().filter { it != turn.from }.mapNotNull { turn.textIn(it) }
    Box(Modifier.fillMaxWidth(), contentAlignment = if (first) Alignment.CenterEnd else Alignment.CenterStart) {
        Column(
            Modifier
                .clip(MaterialTheme.shapes.medium)
                .background(if (first) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer)
                .clickable(onClick = onOpen)
                .padding(10.dp),
        ) {
            Text(turn.source, fontSize = 17.sp)
            others.forEach { Text(it, fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (diagnostics) {
                val t = turn.timings
                Text(
                    if (turn.typed) "⌨ · MT ${t.mtMs} ms" else "%.1f s · ASR %d ms · MT %d ms".format(Locale.US, t.audioSec, t.asrMs, t.mtMs),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
