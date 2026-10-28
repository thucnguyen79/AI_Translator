package io.github.thucnguyen79.aitranslator.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.thucnguyen79.aitranslator.AppContainer

/** Credits and licences (CC-BY-SA and FunASR require attribution). */
@Composable
fun AboutScreen(container: AppContainer) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("AI Translator", style = MaterialTheme.typography.headlineSmall)
        Text("Dịch hội thoại offline cho người du lịch. Âm thanh và văn bản không rời khỏi máy.", fontSize = 14.sp)
        Text("Mô hình & giấy phép", style = MaterialTheme.typography.titleMedium)
        container.packs.catalog.packs.filter { it.license.isNotEmpty() }.forEach { pack ->
            Text("• ${title(pack)}: ${pack.license}", fontSize = 14.sp)
        }
        Text("Thư viện", style = MaterialTheme.typography.titleMedium)
        listOf(
            "sherpa-onnx (k2-fsa) — Apache-2.0",
            "Bergamot translator (Mozilla, bản fork Android của RTranslator) — MPL-2.0",
            "Marian NMT — MIT",
            "ONNX Runtime (Microsoft) — MIT",
            "vibert-capu (dragonSwing; ONNX: welcomyou) — CC-BY-SA-4.0",
            "Apache Commons Compress — Apache-2.0",
            "Jetpack Compose, AndroidX, Kotlin Coroutines — Apache-2.0",
        ).forEach { Text("• $it", fontSize = 14.sp) }
    }
}
