package com.github.libretube.ui.models

import android.app.Application
import android.content.Context
import androidx.core.text.HtmlCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.libretube.api.ChannelPageData
import com.github.libretube.api.DashManifestGenerator
import com.github.libretube.api.HyperStreamData
import com.github.libretube.api.HyperTubeRepository
import com.github.libretube.api.QualityStepOption
import com.github.libretube.api.SearchResultsPage
import com.github.libretube.api.YouTubeLinkParser
import com.github.libretube.api.buildDeduplicatedQualitySteps
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Locale
import kotlin.math.abs

val PLAYBACK_SPEED_STEPS = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

@Serializable
data class RecentStreamEntry(
    val videoId: String,
    val title: String,
    val uploaderName: String,
    val thumbnailUrl: String,
    val durationSeconds: Long,
    val watchedAtEpochMs: Long = System.currentTimeMillis()
)

enum class SideBoxMode(val headerTitle: String) {
    PLAYLIST("Playlist"),
    QUEUE("Queue"),
    SEARCH("Search")
}

data class HyperTubeUiState(
    val linkInput: String = "",
    val isLoading: Boolean = false,
    val isSideBoxLoading: Boolean = false,
    val errorMessage: String? = null,
    val currentStream: HyperStreamData? = null,
    val sanitizedDescription: String = "",
    val qualitySteps: List<QualityStepOption> = emptyList(),
    val selectedQualityIndex: Int = 0,
    val selectedQualityHeight: Int = 1080,
    val selectedQualityLabel: String = "1080p",
    val playbackSpeed: Float = 1.0f,
    val isAudioOnly: Boolean = false,
    val isAmbientModeEnabled: Boolean = true,
    val startTimestampSeconds: Long = 0L,
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

    fun selectSideBoxMode(mode: SideBoxMode) {
        _uiState.update {
            it.copy(
                sideBoxMode = mode,
                currentChannelPage = if (mode != SideBoxMode.SEARCH) null else it.currentChannelPage
            )
        }
    }

    fun clearChannelExplorer() {
        _uiState.update { it.copy(currentChannelPage = null) }
    }

    fun setPlaybackSpeed(speed: Float) {
        val snapped = PLAYBACK_SPEED_STEPS.minByOrNull { abs(it - speed) } ?: 1.0f
        _uiState.update { it.copy(playbackSpeed = snapped) }
    }

    fun setPlaybackSpeedByStepIndex(index: Int) {
        val clamped = index.coerceIn(0, PLAYBACK_SPEED_STEPS.lastIndex)
        _uiState.update { it.copy(playbackSpeed = PLAYBACK_SPEED_STEPS[clamped]) }
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

    fun selectQualityStepIndex(stepIndex: Int) {
        _uiState.update { state ->
            val stream = state.currentStream ?: return@update state
            val steps = state.qualitySteps
            if (steps.isEmpty()) return@update state
            val safeIndex = stepIndex.coerceIn(0, steps.lastIndex)
            val chosenStep = steps[safeIndex]
            val mediaUrls = resolvePlaybackUrls(stream, chosenStep.height, isAudioOnly = false)
            state.copy(
                isAudioOnly = false,
                selectedQualityIndex = safeIndex,
                selectedQualityHeight = chosenStep.height,
                selectedQualityLabel = chosenStep.label,
                dashDataUri = mediaUrls.first,
                directStreamUrl = mediaUrls.second,
                directAudioUrl = mediaUrls.third
            )
        }
    }

    fun selectQuality(height: Int) {
        val steps = _uiState.value.qualitySteps
        val idx = steps.indexOfFirst { it.height == height }
        if (idx >= 0) {
            selectQualityStepIndex(idx)
        }
    }

    fun loadFromRawInput(rawInput: String? = null) {
        val target = (rawInput ?: _uiState.value.linkInput).trim()
        if (target.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Enter a search query or paste a YouTube link.") }
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
                    currentChannelPage = null,
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
                        currentChannelPage = null,
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
                    sideBoxMode = SideBoxMode.SEARCH,
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
                        sideBoxMode = SideBoxMode.SEARCH
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
                val steps = buildDeduplicatedQualitySteps(
                    adaptiveStreams = streamData.adaptiveVideoStreams,
                    muxedStreams = streamData.muxedVideoStreams
                )

                val preferredIdx = when {
                    steps.isEmpty() -> 0
                    steps.indexOfFirst { it.height == 1080 } >= 0 -> steps.indexOfFirst { it.height == 1080 }
                    steps.indexOfFirst { it.height == 720 } >= 0 -> steps.indexOfFirst { it.height == 720 }
                    else -> steps.lastIndex
                }

                val selectedStep = steps.getOrNull(preferredIdx)
                val targetHeight = selectedStep?.height ?: 1080
                val targetLabel = selectedStep?.label ?: "Auto"

                val isAudioOnly = _uiState.value.isAudioOnly
                val mediaUrls = resolvePlaybackUrls(streamData, targetHeight, isAudioOnly)
                val cleanDescription = sanitizeHtmlDescription(streamData.description)
                val updatedHistory = addStreamToHistory(streamData)

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        currentStream = streamData,
                        sanitizedDescription = cleanDescription,
                        qualitySteps = steps,
                        selectedQualityIndex = preferredIdx,
                        selectedQualityHeight = targetHeight,
                        selectedQualityLabel = targetLabel,
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
                        errorMessage = throwable.localizedMessage ?: "Failed to load stream from Piped API."
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

        // Prefer 60fps adaptive stream at the selected resolution height
        val filteredAdaptive = if (targetHeight > 0) {
            stream.adaptiveVideoStreams
                .filter { it.height == targetHeight }
                .sortedWith(compareByDescending<com.github.libretube.api.StreamFormat> { it.fps }.thenByDescending { it.bitrate })
                .ifEmpty { stream.adaptiveVideoStreams }
        } else {
            stream.adaptiveVideoStreams
        }

        val preferredAdaptiveVideo = filteredAdaptive.firstOrNull()
        val exactMuxedMatch = if (targetHeight > 0) {
            stream.muxedVideoStreams
                .filter { it.height == targetHeight }
                .maxByOrNull { it.fps }
        } else {
            null
        }

        val directVideo = preferredAdaptiveVideo?.url
            ?: exactMuxedMatch?.url
            ?: stream.hlsUrl
            ?: stream.muxedVideoStreams.firstOrNull()?.url
            ?: bestAudio

        val dashXml = DashManifestGenerator.createDashXml(
            durationSeconds = stream.durationSeconds,
            videoStreams = filteredAdaptive,
            audioStreams = stream.audioStreams
        ) ?: stream.dashMpdUrl

        return Triple(dashXml, directVideo, bestAudio)
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

fun sanitizeHtmlDescription(rawText: String): String {
    if (rawText.isBlank()) return ""
    return HtmlCompat.fromHtml(rawText, HtmlCompat.FROM_HTML_MODE_COMPACT)
        .toString()
        .trim()
}

fun formatDuration(seconds: Long): String {
    if (seconds <= 0L) return "00:00"
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
        count >= 1_000L -> String.format(Locale.US, "%.1fK", count / 1_000.0)
        else -> count.toString()
    }
}
