package com.github.libretube.ui.activities

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.libretube.ui.models.HyperTubeViewModel
import com.github.libretube.ui.screens.HyperTubeScreen
import com.github.libretube.ui.theme.HyperTubeTheme

class MainActivity : ComponentActivity() {
    private val viewModel: HyperTubeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        hideSystemBars()

        handleIncomingIntent(intent)

        setContent {
            HyperTubeTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()

                HyperTubeScreen(
                    uiState = uiState,
                    onLinkInputChange = viewModel::onLinkInputChange,
                    onPlayClicked = viewModel::loadFromRawInput,
                    onSelectVideoId = { videoId ->
                        viewModel.loadVideoById(videoId)
                    },
                    onOpenChannel = viewModel::openChannel,
                    onSelectSideBoxMode = viewModel::selectSideBoxMode,
                    onSelectQuality = viewModel::selectQuality,
                    onSelectSpeed = viewModel::setPlaybackSpeed,
                    onToggleAudioOnly = viewModel::toggleAudioOnly,
                    onToggleAmbientMode = viewModel::toggleAmbientMode,
                    onRemoveHistoryItem = viewModel::removeHistoryItem,
                    onClearHistory = viewModel::clearHistory,
                    onToggleAbout = viewModel::toggleAboutDialog
                )
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemBars()
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return

        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            ?: intent.getStringExtra(EXTRA_SHARED_LINK)
            ?: intent.dataString

        if (!sharedText.isNullOrBlank()) {
            viewModel.onLinkInputChange(sharedText)
            viewModel.loadFromRawInput(sharedText)
        }
    }

    companion object {
        const val EXTRA_SHARED_LINK = "extra_shared_youtube_link"
    }
}
