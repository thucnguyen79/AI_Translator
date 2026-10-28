package io.github.thucnguyen79.aitranslator.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.thucnguyen79.aitranslator.AppContainer
import io.github.thucnguyen79.aitranslator.core.engine.EngineState
import io.github.thucnguyen79.aitranslator.core.engine.Turn
import io.github.thucnguyen79.aitranslator.core.model.Lang
import io.github.thucnguyen79.aitranslator.core.tts.TtsRoute

/**
 * Two people facing each other across the phone: the top half is rotated 180° for the person opposite.
 * Each half shows the conversation in its own language and has its own microphone button.
 */
@Composable
fun ConversationScreen(container: AppContainer, withMic: (() -> Unit) -> Unit, openPacks: () -> Unit) {
    val engine = container.engine
    val state by engine.state.collectAsStateWithLifecycle()
    val turns by engine.turns.collectAsStateWithLifecycle()
    val packStates by container.packs.states.collectAsStateWithLifecycle()
    val level by container.micLevel.collectAsStateWithLifecycle()
    var seatLangs by remember { mutableStateOf(engine.seats.map { it.lang }) }
    // Re-evaluated whenever a pack finishes installing.
    val ready = remember(packStates) { Lang.entries.associateWith { container.packs.isLanguageReady(it) } }

    Column(Modifier.fillMaxSize()) {
        for (seat in listOf(1, 0)) {
            val modifier = Modifier.weight(1f).fillMaxWidth().let { if (seat == 1) it.rotate(180f) else it }
            SeatPanel(
                modifier = modifier,
                seat = seat,
                lang = seatLangs[seat],
                ready = ready.getValue(seatLangs[seat]) && ready.getValue(seatLangs[1 - seat]),
                otherLang = seatLangs[1 - seat],
                turns = turns,
                state = state,
                level = level,
                container = container,
                onLang = { lang ->
                    container.setSeatLang(seat, lang)
                    seatLangs = container.engine.seats.map { it.lang }
                },
                onMic = {
                    if (state is EngineState.Listening && (state as EngineState.Listening).seat == seat) engine.stopListening()
                    else withMic { engine.startListening(seat) }
                },
                onType = { text -> engine.submitText(seat, text) },
                onReplay = { turn -> engine.replay(turn, seatLangs[seat]) },
                openPacks = openPacks,
            )
            if (seat == 1) HorizontalDivider(thickness = 2.dp)
        }
    }
}

@Composable
private fun SeatPanel(
    modifier: Modifier,
    seat: Int,
    lang: Lang,
    otherLang: Lang,
    ready: Boolean,
    turns: List<Turn>,
    state: EngineState,
    level: Float,
    container: AppContainer,
    onLang: (Lang) -> Unit,
    onMic: () -> Unit,
    onType: (String) -> Unit,
    onReplay: (Turn) -> Unit,
    openPacks: () -> Unit,
) {
    var typing by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    LaunchedEffect(turns.size) { if (turns.isNotEmpty()) listState.animateScrollToItem(turns.lastIndex) }
    var route by remember(lang) { mutableStateOf<TtsRoute?>(null) }
    LaunchedEffect(lang) { route = runCatching { container.tts.route(lang) }.getOrNull() }

    Column(modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Lang.entries.forEach { l -> FilterChip(selected = l == lang, onClick = { onLang(l) }, label = { Text(l.nativeName) }) }
        }
        if (route?.offline == false) {
            Text("Máy chưa có giọng đọc offline cho ${lang.nativeName}", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        }

        if (!ready) {
            Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Cần tải gói ngôn ngữ ${lang.nativeName} và ${otherLang.nativeName}", textAlign = TextAlign.Center)
                TextButton(onClick = openPacks) { Text("Mở Gói ngôn ngữ") }
            }
            return@Column
        }

        // In the rotated (top) panel the list is turned back upright, laid out in reverse and only its items are
        // rotated: scrolling inside a 180° graphicsLayer flings in the wrong direction on release.
        val flipped = seat == 1
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().let { if (flipped) it.rotate(180f) else it },
            state = listState,
            reverseLayout = flipped,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(turns, key = { it.id }) { turn ->
                Box(if (flipped) Modifier.rotate(180f) else Modifier) { TurnBubble(turn, seat, lang, onReplay) }
            }
        }

        StatusLine(state, seat, level)

        if (typing) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Nhập câu (${lang.nativeName})") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    onType(text)
                    text = ""
                }),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { typing = !typing }) { Text(if (typing) "Ẩn bàn phím" else "⌨ Nhập") }
            val listening = state is EngineState.Listening && state.seat == seat
            val busyElsewhere = when (state) {
                is EngineState.Listening -> state.seat != seat
                is EngineState.Recognizing, is EngineState.Translating -> true
                else -> false
            }
            Button(
                onClick = onMic,
                enabled = !busyElsewhere,
                shape = CircleShape,
                modifier = Modifier.weight(1f).height(64.dp),
                colors = if (listening) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) else ButtonDefaults.buttonColors(),
            ) { Text(if (listening) "■ Dừng" else "🎤 Nói", fontSize = 20.sp) }
        }
    }
}

@Composable
private fun TurnBubble(turn: Turn, seat: Int, lang: Lang, onReplay: (Turn) -> Unit) {
    val mine = turn.seat == seat
    val main = if (mine) turn.source else turn.translations[lang] ?: turn.source
    val secondary = if (mine) turn.translations.values.joinToString("\n") else turn.source
    Box(Modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
        Column(
            Modifier
                .background(
                    if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                    MaterialTheme.shapes.medium,
                )
                .padding(10.dp),
        ) {
            Text(main, fontSize = if (mine) 16.sp else 22.sp)
            if (secondary.isNotEmpty()) Text(secondary, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!mine && turn.translations.containsKey(lang)) {
                TextButton(onClick = { onReplay(turn) }, contentPadding = ButtonDefaults.TextButtonContentPadding) { Text("🔊 Nghe lại", fontSize = 13.sp) }
            }
        }
    }
}

@Composable
private fun StatusLine(state: EngineState, seat: Int, level: Float) {
    val text = when (state) {
        is EngineState.Listening -> if (state.seat == seat) "Đang nghe… nói xong sẽ tự dừng" else "Người kia đang nói…"
        is EngineState.Recognizing -> "Đang nhận dạng…"
        is EngineState.Translating -> "Đang dịch…"
        is EngineState.Speaking -> "Đang đọc bản dịch…"
        is EngineState.NothingHeard -> if (state.seat == seat) "Không nghe rõ, hãy nói lại" else ""
        is EngineState.Failed -> "Lỗi: ${state.message}"
        EngineState.Idle -> ""
    }
    Column(Modifier.fillMaxWidth()) {
        if (state is EngineState.Listening && state.seat == seat) {
            LinearProgressIndicator(progress = { level }, modifier = Modifier.fillMaxWidth().height(4.dp))
        }
        if (text.isNotEmpty()) Text(text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
