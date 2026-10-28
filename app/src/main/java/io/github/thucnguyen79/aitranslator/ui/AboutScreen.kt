package io.github.thucnguyen79.aitranslator.ui

import android.app.ActivityManager
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.thucnguyen79.aitranslator.AppContainer
import io.github.thucnguyen79.aitranslator.AppTheme
import io.github.thucnguyen79.aitranslator.ConversationLayout
import io.github.thucnguyen79.aitranslator.R
import io.github.thucnguyen79.aitranslator.core.model.Lang
import io.github.thucnguyen79.aitranslator.core.tts.SUPERTONIC_VOICE
import java.util.Locale

/** Settings, diagnostics, credits and licences (CC-BY-SA and FunASR require attribution). */
@Composable
fun AboutScreen(container: AppContainer) {
    val diagnostics by container.diagnostics.collectAsStateWithLifecycle()
    val layout by container.layout.collectAsStateWithLifecycle()
    val seatCount by container.seatCount.collectAsStateWithLifecycle()
    val backTranslate by container.backTranslate.collectAsStateWithLifecycle()
    val speechRate by container.speechRate.collectAsStateWithLifecycle()
    val fontScale by container.fontScale.collectAsStateWithLifecycle()
    val theme by container.theme.collectAsStateWithLifecycle()
    val names by container.seatNames.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.about_tagline), fontSize = 14.sp)

        Text(stringResource(R.string.layout_title), style = MaterialTheme.typography.titleMedium)
        listOf(
            Triple(ConversationLayout.FACE_TO_FACE, R.string.layout_face, R.string.layout_face_desc),
            Triple(ConversationLayout.LIST, R.string.layout_list, R.string.layout_list_desc),
            Triple(ConversationLayout.AROUND, R.string.layout_around, R.string.layout_around_desc),
        ).forEach { (value, title, desc) ->
            Row(
                Modifier.selectable(selected = layout == value, onClick = { container.setLayout(value) }),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = layout == value, onClick = null)
                Column(Modifier.padding(start = 8.dp)) {
                    Text(stringResource(title))
                    Text(stringResource(desc), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        if (layout != ConversationLayout.FACE_TO_FACE) {
            Text(stringResource(R.string.seats_title), style = MaterialTheme.typography.titleMedium)
            Choices(listOf(2, 3, 4), seatCount, label = { "$it" }, onSelect = container::setSeatCount)
        }

        // Names (F1.3), shown next to each person's area, on the list's buttons and in history.
        Text(stringResource(R.string.names_title), style = MaterialTheme.typography.titleMedium)
        val people = if (layout == ConversationLayout.FACE_TO_FACE) 2 else seatCount
        for (seat in 0 until people) {
            OutlinedTextField(
                value = names[seat],
                onValueChange = { container.setSeatName(seat, it) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Text("●", color = seatColor(seat), fontSize = 20.sp) },
                placeholder = { Text(stringResource(R.string.name_hint, seat + 1)) },
            )
        }

        SwitchRow(stringResource(R.string.back_translate_title), stringResource(R.string.back_translate_desc), backTranslate, container::setBackTranslate)

        Text(stringResource(R.string.speech_rate_title), style = MaterialTheme.typography.titleMedium)
        val rates = listOf(0.75f to R.string.speech_rate_slow, 1f to R.string.speech_rate_normal, 1.25f to R.string.speech_rate_fast)
        Choices(rates.map { it.first }, speechRate, label = { rate -> stringResource(rates.first { it.first == rate }.second) }, onSelect = container::setSpeechRate)

        Voices(container)

        Text(stringResource(R.string.font_size_title), style = MaterialTheme.typography.titleMedium)
        val sizes = listOf(0.9f to "A-", 1f to "A", 1.2f to "A+", 1.4f to "A++")
        Choices(sizes.map { it.first }, fontScale, label = { size -> sizes.first { it.first == size }.second }, onSelect = container::setFontScale)

        Text(stringResource(R.string.theme_title), style = MaterialTheme.typography.titleMedium)
        val themes = listOf(AppTheme.SYSTEM to R.string.theme_system, AppTheme.LIGHT to R.string.theme_light, AppTheme.DARK to R.string.theme_dark)
        Choices(themes.map { it.first }, theme, label = { t -> stringResource(themes.first { it.first == t }.second) }, onSelect = container::setTheme)

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.diagnostics_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.diagnostics_desc), fontSize = 13.sp)
            }
            Switch(diagnostics, container::setDiagnostics)
        }
        if (diagnostics) Diagnostics(container)

        Text(stringResource(R.string.models_licences), style = MaterialTheme.typography.titleMedium)
        container.packs.catalog.packs.filter { it.license.isNotEmpty() }.forEach { pack ->
            Text("• ${title(pack)}: ${pack.license}", fontSize = 14.sp)
        }
        Text(stringResource(R.string.libraries), style = MaterialTheme.typography.titleMedium)
        listOf(
            "sherpa-onnx (k2-fsa) — Apache-2.0",
            "Bergamot translator (Mozilla; Android fork by RTranslator) — MPL-2.0",
            "Marian NMT — MIT",
            "ONNX Runtime (Microsoft) — MIT",
            "vibert-capu (dragonSwing; ONNX: welcomyou) — CC-BY-SA-4.0",
            "Supertonic 3 (Supertone) — OpenRAIL-M",
            "Apache Commons Compress — Apache-2.0",
            "Jetpack Compose, AndroidX, Kotlin Coroutines — Apache-2.0",
        ).forEach { Text("• $it", fontSize = 14.sp) }
    }
}

/** Choice of voice per language (F5.3): automatic, one of the phone's offline voices, or Supertonic. */
@Composable
private fun Voices(container: AppContainer) {
    val context = LocalContext.current
    val choice by container.voiceChoice.collectAsStateWithLifecycle()
    val voicesVersion by container.voicesVersion.collectAsStateWithLifecycle()
    val greeting = remember { Phrasebook.load(context).first().texts }
    Text(stringResource(R.string.voices_title), style = MaterialTheme.typography.titleMedium)
    Lang.entries.forEach { lang ->
        val options by produceState(emptyList<String>(), voicesVersion) { value = runCatching { container.tts.voiceOptions(lang) }.getOrDefault(emptyList()) }
        var open by remember { mutableStateOf(false) }
        val auto = stringResource(R.string.voice_auto)
        val supertonic = stringResource(R.string.voice_supertonic)
        val label = { voice: String? -> when (voice) { null -> auto; SUPERTONIC_VOICE -> supertonic; else -> voice } }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(lang.nativeName, modifier = Modifier.weight(0.35f))
            Box(Modifier.weight(0.65f)) {
                OutlinedButton(onClick = { open = true }, enabled = options.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                    Text(if (options.isEmpty()) stringResource(R.string.voice_none) else "${label(choice[lang])} ▾", maxLines = 1, fontSize = 13.sp)
                }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    (listOf<String?>(null) + options).forEach { voice ->
                        DropdownMenuItem(text = { Text(label(voice), fontSize = 14.sp) }, onClick = {
                            open = false
                            container.setVoice(lang, voice)
                        })
                    }
                }
            }
            IconButton(onClick = { greeting[lang]?.let { container.previewVoice(lang, it) } }, modifier = Modifier.semantics { contentDescription = "▶" }) {
                Text("▶", fontSize = 18.sp)
            }
        }
    }
}

/** A row of chips, one per option; the selected one is highlighted. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> Choices(options: List<T>, selected: T, label: @Composable (T) -> String, onSelect: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option -> FilterChip(selected = option == selected, onClick = { onSelect(option) }, label = { Text(label(option)) }) }
    }
}

@Composable
private fun SwitchRow(title: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(desc, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, onChange)
    }
}

@Composable
private fun Diagnostics(container: AppContainer) {
    val context = LocalContext.current
    val turns by container.engine.turns.collectAsStateWithLifecycle()
    val loadTimes by container.loadTimes.collectAsStateWithLifecycle()
    val mem = ActivityManager.MemoryInfo().also { context.getSystemService(ActivityManager::class.java).getMemoryInfo(it) }
    val spoken = turns.filterNot { it.typed }
    val voicesVersion by container.voicesVersion.collectAsStateWithLifecycle()
    // How each language is read aloud: SYSTEM (offline voice), SUPERTONIC, or SYSTEM_ONLINE (needs network).
    val routes by produceState(emptyMap<Lang, String>(), voicesVersion) {
        value = Lang.entries.associateWith { lang -> runCatching { container.tts.route(lang).name }.getOrElse { "error: ${it.message}" } }
    }
    val notLoaded = stringResource(R.string.diag_not_loaded)
    val loadTitle = stringResource(R.string.diag_load)
    val session = stringResource(R.string.diag_session, turns.size, spoken.size, turns.size - spoken.size)

    val lines = buildList {
        val soc = if (Build.VERSION.SDK_INT >= 31) "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}" else Build.HARDWARE
        add("${Build.MANUFACTURER} ${Build.MODEL} · $soc")
        add("Android ${Build.VERSION.RELEASE} · ${Runtime.getRuntime().availableProcessors()} CPU · RAM %.1f GB".format(Locale.US, mem.totalMem / 1e9))
        add("")
        add("TTS:")
        routes.forEach { (lang, route) -> add("  %-4s %s".format(Locale.US, lang.code, route)) }
        add("")
        add(loadTitle)
        if (loadTimes.isEmpty()) add("  $notLoaded")
        loadTimes.forEach { (name, ms) -> add("  %-12s %6d ms".format(Locale.US, name, ms)) }
        add("")
        add(session)
        if (spoken.isNotEmpty()) {
            add("  audio avg   %6.1f s".format(Locale.US, spoken.map { it.timings.audioSec }.average()))
            add("  ASR avg     %6.0f ms · max %d ms".format(Locale.US, spoken.map { it.timings.asrMs }.average(), spoken.maxOf { it.timings.asrMs }))
        }
        if (turns.isNotEmpty()) {
            add("  MT avg      %6.0f ms · max %d ms".format(Locale.US, turns.map { it.timings.mtMs }.average(), turns.maxOf { it.timings.mtMs }))
        }
    }
    Text(lines.joinToString("\n"), fontFamily = FontFamily.Monospace, fontSize = 12.sp)
}
