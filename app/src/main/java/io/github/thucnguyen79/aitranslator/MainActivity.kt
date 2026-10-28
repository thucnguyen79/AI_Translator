package io.github.thucnguyen79.aitranslator

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import io.github.thucnguyen79.aitranslator.core.model.Lang
import io.github.thucnguyen79.aitranslator.ui.AboutScreen
import io.github.thucnguyen79.aitranslator.ui.ConversationScreen
import io.github.thucnguyen79.aitranslator.ui.PacksScreen

class MainActivity : ComponentActivity() {
    private var onMicGranted: (() -> Unit)? = null
    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) onMicGranted?.invoke()
        onMicGranted = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val container = (application as AiTranslatorApp).container
        val anyLanguageReady = Lang.entries.count { container.packs.isLanguageReady(it) } >= 2

        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                var tab by rememberSaveable { mutableIntStateOf(if (anyLanguageReady) 0 else 1) }
                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            listOf("💬" to "Hội thoại", "⬇" to "Gói ngôn ngữ", "ⓘ" to "Thông tin").forEachIndexed { i, (icon, label) ->
                                NavigationBarItem(
                                    selected = tab == i,
                                    onClick = { tab = i },
                                    icon = { Text(icon) },
                                    label = { Text(label) },
                                )
                            }
                        }
                    },
                ) { inner ->
                    // consumeWindowInsets: imePadding() below then only adds what the keyboard covers beyond the bars.
                    Box(Modifier.fillMaxSize().padding(inner).consumeWindowInsets(inner)) {
                        when (tab) {
                            0 -> ConversationScreen(container, withMic = ::withMicPermission, openPacks = { tab = 1 })
                            1 -> PacksScreen(container)
                            else -> AboutScreen(container)
                        }
                    }
                }
            }
        }
    }

    private fun withMicPermission(action: () -> Unit) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            action()
        } else {
            onMicGranted = action
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
}
