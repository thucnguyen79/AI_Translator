package io.github.thucnguyen79.aitranslator.spike

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.github.thucnguyen79.aitranslator.spike.ui.AsrScreen
import io.github.thucnguyen79.aitranslator.spike.ui.ConversationScreen
import io.github.thucnguyen79.aitranslator.spike.ui.ModelsScreen
import io.github.thucnguyen79.aitranslator.spike.ui.MtScreen
import io.github.thucnguyen79.aitranslator.spike.ui.RecorderScreen
import io.github.thucnguyen79.aitranslator.spike.ui.TtsScreen

class MainActivity : ComponentActivity() {
    private val requestMic = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestMic.launch(Manifest.permission.RECORD_AUDIO)
        }

        val tabs = listOf("Mô hình", "Hội thoại", "ASR", "Dịch", "TTS", "Ghi âm")
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                var tab by rememberSaveable { mutableIntStateOf(0) }
                Scaffold(
                    topBar = {
                        PrimaryScrollableTabRow(selectedTabIndex = tab, modifier = Modifier.statusBarsPadding()) {
                            tabs.forEachIndexed { i, title ->
                                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(title) })
                            }
                        }
                    },
                ) { inner ->
                    Column(
                        Modifier.fillMaxSize().padding(inner).padding(horizontal = 12.dp).verticalScroll(rememberScrollState()),
                    ) {
                        when (tab) {
                            0 -> ModelsScreen()
                            1 -> ConversationScreen()
                            2 -> AsrScreen()
                            3 -> MtScreen()
                            4 -> TtsScreen()
                            5 -> RecorderScreen()
                        }
                    }
                }
            }
        }
    }
}
