package io.github.thucnguyen79.aitranslator.ui

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.thucnguyen79.aitranslator.core.model.Lang

/** A ready-made travel sentence in all five languages (assets/phrases.tsv), translated by people. */
data class Phrase(val id: String, val category: String, val texts: Map<Lang, String>)

object Phrasebook {
    @Volatile private var cache: List<Phrase>? = null

    fun load(context: Context): List<Phrase> = cache ?: context.assets.open("phrases.tsv").bufferedReader().useLines { lines ->
        val rows = lines.filter { it.isNotBlank() }.toList()
        val header = rows.first().split('\t').map { it.trim() }
        val columns = Lang.entries.associateWith { header.indexOf(it.code) }
        rows.drop(1).map { line ->
            val cols = line.split('\t')
            Phrase(cols[0], cols[1], columns.mapValues { (_, i) -> cols.getOrElse(i) { "" }.trim() }.filterValues { it.isNotEmpty() })
        }
    }.also { cache = it }

    /** Category names as shown to the person using the phrasebook. */
    fun categoryName(category: String, lang: Lang): String {
        val names = CATEGORIES[category] ?: return category
        return names[lang.ordinal]
    }

    // vi, en, zh, ko, ja — same order as Lang.
    private val CATEGORIES = mapOf(
        "chao_hoi" to listOf("Chào hỏi", "Greetings", "问候", "인사", "あいさつ"),
        "an_uong" to listOf("Ăn uống", "Food & drink", "餐饮", "식사", "食事"),
        "di_lai" to listOf("Đi lại", "Getting around", "交通", "교통", "交通"),
        "khach_san" to listOf("Khách sạn", "Hotel", "酒店", "호텔", "ホテル"),
        "mua_sam" to listOf("Mua sắm", "Shopping", "购物", "쇼핑", "買い物"),
        "hoi_duong" to listOf("Hỏi đường", "Directions", "问路", "길 묻기", "道を聞く"),
        "khan_cap" to listOf("Khẩn cấp", "Emergency", "紧急", "긴급", "緊急"),
        "giao_tiep" to listOf("Giao tiếp", "Small talk", "交流", "대화", "会話"),
    )
}

/**
 * Picks a phrase for [lang]'s speaker; [flipped] turns the sheet towards the person across the table.
 * Each entry shows the sentence in the speaker's language and, smaller, in [otherLang].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PhrasebookDialog(
    phrases: List<Phrase>,
    lang: Lang,
    otherLang: Lang,
    flipped: Boolean,
    s: SeatStrings,
    onPick: (Phrase) -> Unit,
    onDismiss: () -> Unit,
) {
    val categories = remember(phrases) { phrases.map { it.category }.distinct() }
    var category by remember { mutableStateOf(categories.first()) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().let { if (flipped) it.rotate(180f) else it }.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("📖 ${s.phrasebook}", style = MaterialTheme.typography.titleLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    categories.forEach { c ->
                        FilterChip(selected = c == category, onClick = { category = c }, label = { Text(Phrasebook.categoryName(c, lang)) })
                    }
                }
                HorizontalDivider()
                LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                    items(phrases.filter { it.category == category }, key = { it.id }) { phrase ->
                        Column(
                            Modifier.fillMaxWidth().clickable { onPick(phrase) }.padding(vertical = 10.dp),
                        ) {
                            Text(phrase.texts[lang].orEmpty(), fontSize = 18.sp)
                            if (otherLang != lang) {
                                Text(phrase.texts[otherLang].orEmpty(), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        HorizontalDivider()
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = onDismiss) { Text(s.close) }
                }
            }
        }
    }
}
