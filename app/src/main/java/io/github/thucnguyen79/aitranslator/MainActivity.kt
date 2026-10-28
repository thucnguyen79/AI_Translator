package io.github.thucnguyen79.aitranslator

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import io.github.thucnguyen79.aitranslator.core.model.Lang
import io.github.thucnguyen79.aitranslator.ui.AboutScreen
import io.github.thucnguyen79.aitranslator.ui.ConversationScreen
import io.github.thucnguyen79.aitranslator.ui.PacksScreen

class MainActivity : ComponentActivity() {
    private enum class MicDialog { RATIONALE, DENIED }

    private var micDialog by mutableStateOf<MicDialog?>(null)
    private var pendingMicAction: (() -> Unit)? = null
    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val action = pendingMicAction
        pendingMicAction = null
        when {
            granted -> action?.invoke()
            // "Don't ask again" (or denied twice): only Settings can turn it back on.
            !shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) -> micDialog = MicDialog.DENIED
        }
    }
    private val container get() = (application as AiTranslatorApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val anyLanguageReady = Lang.entries.count { container.packs.isLanguageReady(it) } >= 2

        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                var tab by rememberSaveable { mutableIntStateOf(if (anyLanguageReady) 0 else 1) }
                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            listOf(
                                "💬" to R.string.tab_conversation,
                                "⬇" to R.string.tab_packs,
                                "ⓘ" to R.string.tab_about,
                            ).forEachIndexed { i, (icon, label) ->
                                NavigationBarItem(
                                    selected = tab == i,
                                    onClick = { tab = i },
                                    icon = { Text(icon) },
                                    label = { Text(stringResource(label)) },
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
                MicPermissionDialog()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // The user may have installed TTS voice data in system settings meanwhile.
        container.refreshVoices()
    }

    private fun withMicPermission(action: () -> Unit) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            action()
            return
        }
        pendingMicAction = action
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val askedBefore = prefs.getBoolean("mic_asked", false)
        micDialog = if (askedBefore && !shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
            MicDialog.DENIED
        } else {
            MicDialog.RATIONALE
        }
    }

    @Composable
    private fun MicPermissionDialog() {
        when (micDialog) {
            MicDialog.RATIONALE -> AlertDialog(
                onDismissRequest = { micDialog = null },
                title = { Text(stringResource(R.string.mic_title)) },
                text = { Text(stringResource(R.string.mic_rationale)) },
                confirmButton = {
                    TextButton(onClick = {
                        micDialog = null
                        getSharedPreferences("settings", MODE_PRIVATE).edit().putBoolean("mic_asked", true).apply()
                        micPermission.launch(Manifest.permission.RECORD_AUDIO)
                    }) { Text(stringResource(R.string.mic_continue)) }
                },
                dismissButton = { TextButton(onClick = { micDialog = null }) { Text(stringResource(R.string.cancel)) } },
            )
            MicDialog.DENIED -> AlertDialog(
                onDismissRequest = { micDialog = null },
                title = { Text(stringResource(R.string.mic_denied_title)) },
                text = { Text(stringResource(R.string.mic_denied_message)) },
                confirmButton = {
                    TextButton(onClick = {
                        micDialog = null
                        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
                    }) { Text(stringResource(R.string.open_settings)) }
                },
                dismissButton = { TextButton(onClick = { micDialog = null }) { Text(stringResource(R.string.cancel)) } },
            )
            null -> Unit
        }
    }
}
