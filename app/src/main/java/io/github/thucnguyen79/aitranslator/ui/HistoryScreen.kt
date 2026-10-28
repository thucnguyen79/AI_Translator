package io.github.thucnguyen79.aitranslator.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.thucnguyen79.aitranslator.AppContainer
import io.github.thucnguyen79.aitranslator.R
import io.github.thucnguyen79.aitranslator.SavedSession
import io.github.thucnguyen79.aitranslator.mentions
import java.text.DateFormat
import java.util.Date

/** Saved conversations (F9): search, read, share as text, delete; private mode stops saving. */
@Composable
fun HistoryScreen(container: AppContainer) {
    val sessions by container.history.sessions.collectAsStateWithLifecycle()
    val private by container.privateMode.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var openId by rememberSaveable { mutableStateOf<Long?>(null) }
    /** A session id to delete, or [ALL]. */
    var confirmDelete by remember { mutableStateOf<Long?>(null) }

    confirmDelete?.let { id ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(stringResource(if (id == ALL) R.string.history_delete_all_title else R.string.history_delete_title)) },
            text = { Text(stringResource(R.string.history_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    if (id == ALL) container.history.deleteAll() else container.history.delete(id)
                    if (id == ALL || id == openId) openId = null
                    confirmDelete = null
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    val open = sessions.firstOrNull { it.id == openId }
    if (open != null) {
        SessionDetail(open, query, onBack = { openId = null }, onDelete = { confirmDelete = open.id })
        return
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.history_private), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.history_private_desc), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(private, container::setPrivateMode)
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("🔍 ${stringResource(R.string.history_search)}") },
            singleLine = true,
        )
        val shown = sessions.filter { it.matches(query) }
        if (shown.isEmpty()) {
            Text(
                stringResource(if (sessions.isEmpty()) R.string.history_empty else R.string.history_no_match),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            items(shown, key = { it.id }) { session ->
                Column(Modifier.fillMaxWidth().clickable { openId = session.id }.padding(vertical = 10.dp)) {
                    Text(
                        "${formatDate(session.id)} · ${pluralStringResource(R.plurals.history_sentences, session.turns.size, session.turns.size)}",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(session.langs.joinToString(" · ") { it.nativeName }, fontWeight = FontWeight.Medium)
                    val preview = session.turns.firstOrNull { query.isBlank() || it.mentions(query) } ?: session.turns.first()
                    Text(preview.source, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                HorizontalDivider()
            }
        }
        if (sessions.isNotEmpty()) {
            TextButton(onClick = { confirmDelete = ALL }, modifier = Modifier.align(Alignment.End)) {
                Text("🗑 ${stringResource(R.string.history_delete_all)}")
            }
        }
    }
}

@Composable
private fun SessionDetail(session: SavedSession, query: String, onBack: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    val appName = stringResource(R.string.app_name)
    val shareTitle = stringResource(R.string.history_share)
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← ${stringResource(R.string.history_back)}") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_SUBJECT, "$appName — ${formatDate(session.id)}")
                    .putExtra(Intent.EXTRA_TEXT, session.toText(appName))
                context.startActivity(Intent.createChooser(send, shareTitle))
            }) { Text("⇪ $shareTitle") }
            TextButton(onClick = onDelete) { Text("🗑 ${stringResource(R.string.delete)}") }
        }
        Text(formatDate(session.id), style = MaterialTheme.typography.titleMedium)
        Text(session.langs.joinToString(" · ") { it.nativeName }, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider()
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(session.turns, key = { it.id }) { turn ->
                val hit = query.isNotBlank() && turn.mentions(query)
                Column(Modifier.fillMaxWidth()) {
                    Text(
                        "${turn.seat + 1} · ${turn.from.nativeName}",
                        fontSize = 12.sp,
                        color = seatColor(turn.seat),
                        fontWeight = if (hit) FontWeight.Bold else null,
                    )
                    Text(turn.source, fontSize = 17.sp, fontWeight = if (hit) FontWeight.SemiBold else null)
                    turn.translations.forEach { (lang, text) ->
                        Text("${lang.nativeName}: $text", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

private fun formatDate(ms: Long): String = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(ms))

private const val ALL = -1L
