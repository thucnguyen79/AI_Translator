package io.github.thucnguyen79.aitranslator.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.util.Locale
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
    val diagnostics by container.diagnostics.collectAsStateWithLifecycle()
    val preparing by container.preparing.collectAsStateWithLifecycle()
    var seatLangs by remember { mutableStateOf(engine.seats.map { it.lang }) }
    // Re-evaluated whenever a pack finishes installing.
    val ready = remember(packStates) { Lang.entries.associateWith { container.packs.isLanguageReady(it) } }
    var big by remember { mutableStateOf<BigText?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    /** The seat whose keyboard input is open: its half gets more room above the keyboard. */
    var typingSeat by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(ready) { container.preload() }

    big?.let { BigTextDialog(it) { big = null } }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Xoá hội thoại?") },
            text = { Text("Toàn bộ các câu trên màn hình sẽ bị xoá. Không thể hoàn tác.") },
            confirmButton = {
                TextButton(onClick = {
                    engine.clear()
                    confirmClear = false
                }) { Text("Xoá") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Huỷ") } },
        )
    }

    // imePadding: with edge-to-edge the keyboard would otherwise cover the input field and buttons.
    Column(Modifier.fillMaxSize().imePadding()) {
        for (seat in listOf(1, 0)) {
            val weight = when (typingSeat) {
                null -> 1f
                seat -> 1.6f
                else -> 0.6f
            }
            val modifier = Modifier.weight(weight).fillMaxWidth().let { if (seat == 1) it.rotate(180f) else it }
            SeatPanel(
                modifier = modifier,
                seat = seat,
                typing = typingSeat == seat,
                onTyping = { on -> typingSeat = if (on) seat else null },
                lang = seatLangs[seat],
                ready = ready.getValue(seatLangs[seat]) && ready.getValue(seatLangs[1 - seat]),
                otherLang = seatLangs[1 - seat],
                turns = turns,
                state = state,
                level = level,
                preparing = preparing,
                diagnostics = diagnostics,
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
                onOpen = { turn -> big = BigText.of(turn, seat, seatLangs[seat]) },
                onClear = { confirmClear = true },
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
    typing: Boolean,
    onTyping: (Boolean) -> Unit,
    lang: Lang,
    otherLang: Lang,
    ready: Boolean,
    turns: List<Turn>,
    state: EngineState,
    level: Float,
    preparing: Boolean,
    diagnostics: Boolean,
    container: AppContainer,
    onLang: (Lang) -> Unit,
    onMic: () -> Unit,
    onType: (String) -> Unit,
    onReplay: (Turn) -> Unit,
    onOpen: (Turn) -> Unit,
    onClear: () -> Unit,
    openPacks: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    LaunchedEffect(turns.size) { if (turns.isNotEmpty()) listState.animateScrollToItem(turns.lastIndex) }
    var route by remember(lang) { mutableStateOf<TtsRoute?>(null) }
    LaunchedEffect(lang) { route = runCatching { container.tts.route(lang) }.getOrNull() }

    Column(modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!typing) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Lang.entries.forEach { l -> FilterChip(selected = l == lang, onClick = { onLang(l) }, label = { Text(l.nativeName) }) }
            }
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
                Box(if (flipped) Modifier.rotate(180f) else Modifier) {
                    TurnBubble(turn, seat, lang, diagnostics, onReplay = { onReplay(turn) }, onOpen = { onOpen(turn) })
                }
            }
        }

        StatusLine(state, seat, level, preparing)

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
            TextButton(onClick = { onTyping(!typing) }) { Text(if (typing) "Ẩn bàn phím" else "⌨ Nhập") }
            // Only the phone holder's half (bottom) offers clearing, so the guest cannot wipe it by accident.
            if (seat == 0 && turns.isNotEmpty()) TextButton(onClick = onClear) { Text("🗑 Xoá") }
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

/** Tap a bubble to show it full screen (see [BigText]). */
@Composable
private fun TurnBubble(turn: Turn, seat: Int, lang: Lang, diagnostics: Boolean, onReplay: () -> Unit, onOpen: () -> Unit) {
    val mine = turn.seat == seat
    val main = if (mine) turn.source else turn.translations[lang] ?: turn.source
    val secondary = if (mine) turn.translations.values.joinToString("\n") else turn.source
    Box(Modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
        Column(
            Modifier
                .clip(MaterialTheme.shapes.medium)
                .background(if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer)
                .clickable(onClick = onOpen)
                .padding(10.dp),
        ) {
            Text(main, fontSize = if (mine) 16.sp else 22.sp)
            if (secondary.isNotEmpty()) Text(secondary, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (diagnostics) {
                val t = turn.timings
                Text(
                    if (turn.typed) "nhập phím · dịch ${t.mtMs} ms"
                    else "âm thanh %.1f s · nhận dạng %d ms · dịch %d ms".format(Locale.US, t.audioSec, t.asrMs, t.mtMs),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!mine && turn.translations.containsKey(lang)) {
                TextButton(onClick = onReplay, contentPadding = ButtonDefaults.TextButtonContentPadding) { Text("🔊 Nghe lại", fontSize = 13.sp) }
            }
        }
    }
}

/**
 * Full-screen large text: your own sentence shows its translation turned towards the person opposite;
 * someone else's sentence shows the translation in your language, turned towards you.
 */
private data class BigText(val text: String, val lang: Lang, val source: String, val flipped: Boolean) {
    companion object {
        fun of(turn: Turn, seat: Int, seatLang: Lang): BigText {
            val mine = turn.seat == seat
            return if (mine && turn.translations.isNotEmpty()) {
                val (lang, text) = turn.translations.entries.first()
                BigText(text, lang, turn.source, flipped = seat == 0)
            } else {
                BigText(turn.translations[seatLang] ?: turn.source, seatLang, turn.source, flipped = seat == 1)
            }
        }
    }
}

@Composable
private fun BigTextDialog(big: BigText, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var flipped by remember(big) { mutableStateOf(big.flipped) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(
                Modifier.fillMaxSize().let { if (flipped) it.rotate(180f) else it }.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(big.lang.nativeName, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    BasicText(
                        big.text,
                        style = MaterialTheme.typography.displayLarge.copy(color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center),
                        autoSize = TextAutoSize.StepBased(minFontSize = 20.sp, maxFontSize = 96.sp),
                    )
                }
                if (big.source != big.text) Text(big.source, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { flipped = !flipped }) { Text("⟲ Xoay") }
                    TextButton(onClick = {
                        val clipboard = context.getSystemService(ClipboardManager::class.java)
                        clipboard.setPrimaryClip(ClipData.newPlainText(big.lang.nativeName, big.text))
                        Toast.makeText(context, "Đã sao chép", Toast.LENGTH_SHORT).show()
                    }) { Text("⧉ Sao chép") }
                    Spacer(Modifier.weight(1f))
                    Button(onClick = onDismiss) { Text("Đóng") }
                }
            }
        }
    }
}

@Composable
private fun StatusLine(state: EngineState, seat: Int, level: Float, preparing: Boolean) {
    val text = when (state) {
        is EngineState.Listening -> if (state.seat == seat) "Đang nghe… nói xong sẽ tự dừng" else "Người kia đang nói…"
        is EngineState.Recognizing -> "Đang nhận dạng…"
        is EngineState.Translating -> "Đang dịch…"
        is EngineState.Speaking -> "Đang đọc bản dịch…"
        is EngineState.NothingHeard -> if (state.seat == seat) "Không nghe rõ, hãy nói lại" else ""
        is EngineState.Failed -> "Lỗi: ${state.message}"
        EngineState.Idle -> if (preparing) "Đang nạp mô hình…" else ""
    }
    Column(Modifier.fillMaxWidth()) {
        if (state is EngineState.Listening && state.seat == seat) {
            LinearProgressIndicator(progress = { level }, modifier = Modifier.fillMaxWidth().height(4.dp))
        }
        if (text.isNotEmpty()) Text(text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
