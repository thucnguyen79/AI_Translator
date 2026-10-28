package io.github.thucnguyen79.aitranslator.spike.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.thucnguyen79.aitranslator.spike.SpikeApp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun Section(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChipRow(options: List<T>, selected: T?, label: (T) -> String, onSelect: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { o -> FilterChip(selected = o == selected, onClick = { onSelect(o) }, label = { Text(label(o)) }) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> MultiChipRow(options: List<T>, selected: Set<T>, label: (T) -> String, onToggle: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { o -> FilterChip(selected = o in selected, onClick = { onToggle(o) }, label = { Text(label(o)) }) }
    }
}

@Composable
fun Mono(text: String) {
    SelectionContainer { Text(text, fontFamily = FontFamily.Monospace, fontSize = 12.sp) }
}

/** Long-running task state that survives tab switches. */
class TaskState {
    var running by mutableStateOf(false)
        private set
    var progress by mutableStateOf(0f)
    var status by mutableStateOf("")
    var result by mutableStateOf("")
    private var job: Job? = null

    fun start(block: suspend TaskState.() -> Unit) {
        if (running) return
        running = true
        progress = 0f
        status = ""
        result = ""
        job = SpikeApp.scope.launch {
            try {
                block()
            } catch (e: kotlinx.coroutines.CancellationException) {
                status = "Đã huỷ"
            } catch (e: Throwable) {
                result = "Lỗi: ${e.message ?: e::class.simpleName}"
            } finally {
                running = false
            }
        }
    }

    fun cancel() {
        job?.cancel()
    }
}

@Composable
fun TaskView(state: TaskState) {
    if (state.running) LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
    if (state.status.isNotEmpty()) Text(state.status, style = MaterialTheme.typography.bodySmall, maxLines = 2)
    if (state.result.isNotEmpty()) Mono(state.result)
}
