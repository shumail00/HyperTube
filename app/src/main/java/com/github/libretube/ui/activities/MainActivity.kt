package com.github.libretube.ui.activities

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
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

        handleIncomingIntent(intent)

        setContent {
            HyperTubeTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()

                HyperTubeScreen(
                    uiState = uiState,
                    onLinkInputChange = viewModel::onLinkInputChange,
                    onPlayClicked = viewModel::loadFromRawInput,
                    onSelectVideoId = { videoId ->
                        viewModel.onLinkInputChange("https://youtu.be/$videoId")
                        viewModel.loadVideoById(videoId)
                    },
                    onSelectQuality = viewModel::selectQuality,
                    onSelectSpeed = viewModel::setPlaybackSpeed,
                    onToggleAudioOnly = viewModel::toggleAudioOnly,
                    onSelectTab = viewModel::selectTab,
                    onRemoveHistoryItem = viewModel::removeHistoryItem,
                    onClearHistory = viewModel::clearHistory,
                    onToggleAbout = viewModel::toggleAboutDialog
                )
            }
        }
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
