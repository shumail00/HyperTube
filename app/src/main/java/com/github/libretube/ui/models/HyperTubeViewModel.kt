package com.github.libretube.ui.models

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.libretube.api.ChannelPageData
import com.github.libretube.api.DashManifestGenerator
import com.github.libretube.api.HyperStreamData
import com.github.libretube.api.HyperTubeRepository
import com.github.libretube.api.SearchResultsPage
import com.github.libretube.api.YouTubeLinkParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Locale

@Serializable
data class RecentStreamEntry(
    val videoId: String,
    val title: String,
    val uploaderName: String,
    val thumbnailUrl: String,
    val durationSeconds: Long,
    val watchedAtEpochMs: Long = System.currentTimeMillis()
)

enum class InspectorTab(val label: String) {
    OVERVIEW("Playlist"),
    CHAPTERS("Search"),
    UP_NEXT("Channel")
}

enum class SideBoxMode(val headerTitle: String) {
    PLAYLIST("Playlist"),
    SEARCH("Search"),
    CHANNEL("Channel")
}

data class HyperTubeUiState(
    val linkInput: String = "",
    val isLoading: Boolean = false,
    val isSideBoxLoading: Boolean = false,
    val errorMessage: String? = null,
    val currentStream: HyperStreamData? = null,
    val availableQualities: List<Int> = emptyList(),
    val selectedQualityHeight: Int = 0,
    val playbackSpeed: Float = 1.0f,
    val isAudioOnly: Boolean = false,
    val isAmbientModeEnabled: Boolean = true,
    val startTimestampSeconds: Long = 0L,
    val activeTab: InspectorTab = InspectorTab.OVERVIEW,
    val sideBoxMode: SideBoxMode = SideBoxMode.PLAYLIST,
    val searchResults: SearchResultsPage? = null,
    val currentChannelPage: ChannelPageData? = null,
    val recentStreams: List<RecentStreamEntry> = emptyList(),
    val showAboutDialog: Boolean = false,
    val dashDataUri: String? = null,
    val directStreamUrl: String? = null,
    val directAudioUrl: String? = null
)

class HyperTubeViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("hypertube_prefs", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private val _uiState = MutableStateFlow(
        HyperTubeUiState(
            recentStreams = loadSavedHistory()
        )
    )
    val uiState: StateFlow<HyperTubeUiState> = _uiState.asStateFlow()

    fun onLinkInputChange(newText: String) {
        _uiState.update { it.copy(linkInput = newText, errorMessage = null) }
    }

    fun toggleAboutDialog(show: Boolean) {
        _uiState.update { it.copy(showAboutDialog = show) }
    }

    fun toggleAmbientMode() {
        _uiState.update { it.copy(isAmbientModeEnabled = !it.isAmbientModeEnabled) }
    }

    fun selectTab(tab: InspectorTab) {
        val mappedMode = when (tab) {
            InspectorTab.OVERVIEW -> SideBoxMode.PLAYLIST
            InspectorTab.CHAPTERS -> SideBoxMode.SEARCH
            InspectorTab.UP_NEXT -> SideBoxMode.CHANNEL
        }
        selectSideBoxMode(mappedMode)
    }

    fun selectSideBoxMode(mode: SideBoxMode) {
        _uiState.update { it.copy(sideBoxMode = mode) }
        if (mode == SideBoxMode.CHANNEL && _uiState.value.currentChannelPage == null) {
            val stream = _uiState.value.currentStream
            if (stream != null && (stream.uploaderUrl.isNotBlank() || stream.uploaderName.isNotBlank())) {
                openChannel(stream.uploaderUrl.ifBlank { stream.uploaderName }, stream.uploaderName)
            }
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        _uiState.update { it.copy(playbackSpeed = speed) }
    }

    fun toggleAudioOnly() {
        val nextAudioOnly = !_uiState.value.isAudioOnly
        _uiState.update { state ->
            val stream = state.currentStream
            if (stream != null) {
                val mediaUrls = resolvePlaybackUrls(stream, state.selectedQualityHeight, nextAudioOnly)
                state.copy(
                    isAudioOnly = nextAudioOnly,
                    dashDataUri = mediaUrls.first,
                    directStreamUrl = mediaUrls.second,
                    directAudioUrl = mediaUrls.third
                )
            } else {
                state.copy(isAudioOnly = nextAudioOnly)
            }
        }
    }

    fun selectQuality(height: Int) {
        _uiState.update { state ->
            val stream = state.currentStream ?: return@update state
            val mediaUrls = resolvePlaybackUrls(stream, height, isAudioOnly = false)
            state.copy(
                isAudioOnly = false,
                selectedQualityHeight = height,
                dashDataUri = mediaUrls.first,
                directStreamUrl = mediaUrls.second,
                directAudioUrl = mediaUrls.third
            )
        }
    }

    /**
     * Unified handler for the Side Box query input:
     * - If the user pasted a YouTube link or 11-character video ID, plays the video directly.
     * - Otherwise, executes a live YouTube search and displays Video + Channel results in the Side Box.
     */
    fun loadFromRawInput(rawInput: String? = null) {
        val target = (rawInput ?: _uiState.value.linkInput).trim()
        if (target.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Type a search query or paste a YouTube link.") }
            return
        }

        if (YouTubeLinkParser.isLikelyYouTubeLinkOrId(target)) {
            val parsed = YouTubeLinkParser.parseInput(target)
            if (parsed != null) {
                _uiState.update {
                    it.copy(
                        linkInput = target,
                        errorMessage = null
                    )
                }
                loadVideoById(parsed.videoId, parsed.timestampSeconds)
                return
            }
        }

        // Treat as a YouTube search query
        performSearch(target)
    }

    fun performSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    linkInput = trimmed,
                    sideBoxMode = SideBoxMode.SEARCH,
                    isSideBoxLoading = true,
                    errorMessage = null
                )
            }

            runCatching {
                HyperTubeRepository.searchYouTube(trimmed)
            }.onSuccess { results ->
                _uiState.update {
                    it.copy(
                        isSideBoxLoading = false,
                        searchResults = results,
                        sideBoxMode = SideBoxMode.SEARCH
                    )
                }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isSideBoxLoading = false,
                        errorMessage = throwable.localizedMessage ?: "Search failed."
                    )
                }
            }
        }
    }

    fun openChannel(channelUrlOrId: String, channelName: String = "") {
        if (channelUrlOrId.isBlank() && channelName.isBlank()) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    sideBoxMode = SideBoxMode.CHANNEL,
                    isSideBoxLoading = true,
                    errorMessage = null
                )
            }

            runCatching {
                HyperTubeRepository.fetchChannelPage(
                    channelUrlOrId = channelUrlOrId.ifBlank { channelName },
                    fallbackName = channelName
                )
            }.onSuccess { channelPage ->
                _uiState.update {
                    it.copy(
                        isSideBoxLoading = false,
                        currentChannelPage = channelPage,
                        sideBoxMode = SideBoxMode.CHANNEL
                    )
                }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isSideBoxLoading = false,
                        errorMessage = throwable.localizedMessage ?: "Unable to load channel."
                    )
                }
            }
        }
    }

    fun loadVideoById(videoId: String, timestampSeconds: Long = 0L) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null,
                    startTimestampSeconds = timestampSeconds
                )
            }

            runCatching {
                HyperTubeRepository.fetchStream(videoId)
            }.onSuccess { streamData ->
                val allHeights = (streamData.adaptiveVideoStreams.map { it.height } +
                    streamData.muxedVideoStreams.map { it.height })
                    .filter { it > 0 }
                    .distinct()
                    .sortedDescending()

                val defaultQuality = allHeights.firstOrNull { it == 1080 }
                    ?: allHeights.firstOrNull { it == 720 }
                    ?: allHeights.firstOrNull { it <= 1080 }
                    ?: allHeights.firstOrNull()
                    ?: 0
                val isAudioOnly = _uiState.value.isAudioOnly
                val mediaUrls = resolvePlaybackUrls(streamData, defaultQuality, isAudioOnly)

                val updatedHistory = addStreamToHistory(streamData)

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        currentStream = streamData,
                        availableQualities = allHeights,
                        selectedQualityHeight = defaultQuality,
                        dashDataUri = mediaUrls.first,
                        directStreamUrl = mediaUrls.second,
                        directAudioUrl = mediaUrls.third,
                        recentStreams = updatedHistory
                    )
                }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = throwable.localizedMessage ?: "Failed to extract YouTube stream."
                    )
                }
            }
        }
    }

    fun removeHistoryItem(videoId: String) {
        val updated = _uiState.value.recentStreams.filterNot { it.videoId == videoId }
        saveHistory(updated)
        _uiState.update { it.copy(recentStreams = updated) }
    }

    fun clearHistory() {
        saveHistory(emptyList())
        _uiState.update { it.copy(recentStreams = emptyList()) }
    }

    private fun resolvePlaybackUrls(
        stream: HyperStreamData,
        targetHeight: Int,
        isAudioOnly: Boolean
    ): Triple<String?, String?, String?> {
        val bestAudioFormat = stream.audioStreams.maxByOrNull { it.bitrate }
        val bestAudio = bestAudioFormat?.url
        if (isAudioOnly && bestAudio != null) {
            return Triple(null, bestAudio, bestAudio)
        }

        if (stream.isLive && !stream.hlsUrl.isNullOrBlank()) {
            return Triple(null, stream.hlsUrl, null)
        }

        val filteredAdaptive = if (targetHeight > 0) {
            stream.adaptiveVideoStreams.filter { it.height == targetHeight }
                .ifEmpty { stream.adaptiveVideoStreams }
        } else {
            stream.adaptiveVideoStreams
        }

        val preferredAdaptiveVideo = filteredAdaptive.firstOrNull()
        val exactMuxedMatch = if (targetHeight > 0) {
            stream.muxedVideoStreams.firstOrNull { it.height == targetHeight }
        } else {
            null
        }
        val directVideo = preferredAdaptiveVideo?.url
            ?: exactMuxedMatch?.url
            ?: stream.muxedVideoStreams.firstOrNull()?.url
            ?: stream.hlsUrl
            ?: bestAudio

        val targetAgent = preferredAdaptiveVideo?.userAgent
        val matchingAudio = if (targetAgent != null) {
            stream.audioStreams.filter { it.userAgent == targetAgent }.maxByOrNull { it.bitrate }?.url
                ?: bestAudio
        } else {
            bestAudio
        }

        val dashXml = DashManifestGenerator.createDashXml(
            durationSeconds = stream.durationSeconds,
            videoStreams = filteredAdaptive,
            audioStreams = stream.audioStreams
        )

        return Triple(dashXml, directVideo, matchingAudio)
    }

    private fun addStreamToHistory(stream: HyperStreamData): List<RecentStreamEntry> {
        val entry = RecentStreamEntry(
            videoId = stream.videoId,
            title = stream.title,
            uploaderName = stream.uploaderName,
            thumbnailUrl = stream.thumbnailUrl,
            durationSeconds = stream.durationSeconds
        )
        val updated = (listOf(entry) + _uiState.value.recentStreams.filterNot { it.videoId == stream.videoId })
            .take(25)
        saveHistory(updated)
        return updated
    }

    private fun loadSavedHistory(): List<RecentStreamEntry> {
        val raw = prefs.getString("recent_streams_json", null)
        if (!raw.isNullOrBlank()) {
            runCatching {
                return json.decodeFromString<List<RecentStreamEntry>>(raw)
            }
        }
        return listOf(
            RecentStreamEntry(
                videoId = "ZRtdQ81jPUQ",
                title = "YOASOBI「アイドル」 Official Music Video",
                uploaderName = "Ayase / YOASOBI",
                thumbnailUrl = "https://i.ytimg.com/vi/ZRtdQ81jPUQ/hqdefault.jpg",
                durationSeconds = 226
            ),
            RecentStreamEntry(
                videoId = "LXb3EKWsInQ",
                title = "COSTA RICA IN 4K 60fps HDR (Ultra HD)",
                uploaderName = "Jacob + Katie Schwarz",
                thumbnailUrl = "https://i.ytimg.com/vi/LXb3EKWsInQ/hqdefault.jpg",
                durationSeconds = 314
            ),
            RecentStreamEntry(
                videoId = "aqz-KE-bpKQ",
                title = "Big Buck Bunny 60fps 4K - Official Blender Foundation Short Film",
                uploaderName = "Blender Foundation",
                thumbnailUrl = "https://i.ytimg.com/vi/aqz-KE-bpKQ/hqdefault.jpg",
                durationSeconds = 635
            )
        )
    }

    private fun saveHistory(entries: List<RecentStreamEntry>) {
        runCatching {
            prefs.edit().putString("recent_streams_json", json.encodeToString(entries)).apply()
        }
    }
}

fun formatDuration(seconds: Long): String {
    if (seconds <= 0L) return "LIVE"
    val hrs = seconds / 3600
    val mins = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hrs > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hrs, mins, secs)
    } else {
        String.format(Locale.US, "%02d:%02d", mins, secs)
    }
}

fun formatCompactCount(count: Long): String {
    if (count < 0) return "—"
    return when {
        count >= 1_000_000_000L -> String.format(Locale.US, "%.1fB", count / 1_000_000_000.0)
        count >= 1_000_000L -> String.format(Locale.US, "%.1fM", count / 1_000_000.0)
        count >= 1_000_000L -> String.format(Locale.US, "%.1fK", count / 1_000.0)
        count >= 1_000L -> String.format(Locale.US, "%.1fK", count / 1_000.0)
        else -> count.toString()
    }
}
