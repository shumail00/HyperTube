package com.github.libretube.api

import android.net.Uri
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.min

object YouTubeLinkParser {
    private val VIDEO_ID_REGEX = Regex("^[a-zA-Z0-9_-]{11}$")
    private val URL_EXTRACT_REGEX = Regex("(https?://[^\\s]+)")

    data class ParsedLink(
        val videoId: String,
        val timestampSeconds: Long = 0L
    )

    fun isLikelyYouTubeLinkOrId(rawInput: String): Boolean {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return false
        if (VIDEO_ID_REGEX.matches(trimmed) &&
            (trimmed.any { it.isDigit() } || trimmed.contains('-') || trimmed.contains('_'))
        ) {
            return true
        }
        val lower = trimmed.lowercase()
        return lower.contains("youtube.com/") ||
            lower.contains("youtu.be/") ||
            lower.contains("youtube-nocookie.com/") ||
            lower.startsWith("/watch?v=")
    }

    fun parseInput(rawInput: String): ParsedLink? {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return null

        if (VIDEO_ID_REGEX.matches(trimmed)) {
            return ParsedLink(videoId = trimmed)
        }

        if (trimmed.startsWith("/watch")) {
            val id = trimmed.substringAfter("v=").substringBefore("&").take(11)
            if (id.length == 11) return ParsedLink(videoId = id)
        }

        val candidateUrl = URL_EXTRACT_REGEX.find(trimmed)?.value ?: trimmed
        val normalizedUrl = if (!candidateUrl.startsWith("http://") && !candidateUrl.startsWith("https://")) {
            "https://$candidateUrl"
        } else {
            candidateUrl
        }

        return runCatching {
            val uri = normalizedUrl.toUri()
            val timestamp = parseTimestamp(uri.getQueryParameter("t") ?: uri.getQueryParameter("start"))
            val videoId = extractVideoIdFromUri(uri) ?: return null
            if (videoId.isNotBlank()) ParsedLink(videoId, timestamp) else null
        }.getOrNull()
    }

    private fun extractVideoIdFromUri(uri: Uri): String? {
        val host = uri.host?.lowercase() ?: return null
        val pathSegments = uri.pathSegments

        if (host.contains("youtu.be")) {
            return pathSegments.firstOrNull()?.take(11)
        }

        uri.getQueryParameter("v")?.let { v ->
            if (v.isNotBlank()) return v.take(11)
        }

        if (pathSegments.size >= 2) {
            val prefix = pathSegments[0].lowercase()
            if (prefix in listOf("shorts", "live", "embed", "v", "e")) {
                return pathSegments[1].take(11)
            }
        }

        val lastSegment = uri.lastPathSegment
        if (lastSegment != null && VIDEO_ID_REGEX.matches(lastSegment)) {
            return lastSegment
        }

        return null
    }

    private fun parseTimestamp(rawTime: String?): Long {
        if (rawTime.isNullOrBlank()) return 0L
        rawTime.toLongOrNull()?.let { return it }

        val hours = Regex("(\\d+)h").find(rawTime)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val minutes = Regex("(\\d+)m").find(rawTime)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val seconds = Regex("(\\d+)s").find(rawTime)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        return hours * 3600 + minutes * 60 + seconds
    }
}

private val QUALITY_HEIGHT_REGEX = Regex("(\\d{3,4})p")

private fun normalizeHeight(width: Int, height: Int, qualityLabel: String?): Int {
    if (!qualityLabel.isNullOrBlank()) {
        val labelLower = qualityLabel.lowercase()
        if (labelLower.contains("4k") || labelLower.contains("2160")) return 2160
        if (labelLower.contains("2k") || labelLower.contains("1440")) return 1440
        QUALITY_HEIGHT_REGEX.find(labelLower)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let {
            if (it > 0) return it
        }
    }
    if (width <= 0 && height <= 0) return 0
    val shortEdge = if (width > 0 && height > 0) min(width, height) else height.coerceAtLeast(width)
    return when {
        shortEdge in 130..160 -> 144
        shortEdge in 220..260 -> 240
        shortEdge in 340..380 -> 360
        shortEdge in 460..500 -> 480
        shortEdge in 700..740 -> 720
        shortEdge in 1060..1100 -> 1080
        shortEdge in 1400..1480 -> 1440
        shortEdge in 2100..2200 -> 2160
        else -> shortEdge
    }
}

private fun codecPriority(codec: String?): Int {
    val c = codec?.lowercase().orEmpty()
    return when {
        c.startsWith("avc1") || c.contains("h264") -> 3
        c.startsWith("vp9") || c.startsWith("vp09") -> 2
        c.startsWith("av01") -> 1
        else -> 0
    }
}

private fun PipedStreamFormatDto.toStreamFormat(): StreamFormat? {
    val cleanUrl = url?.takeIf { it.isNotBlank() && !it.startsWith("sabr://") } ?: return null
    val resolvedHeight = normalizeHeight(width, height, quality)
    val resolvedFps = when {
        fps > 0 -> fps
        quality?.contains("60") == true -> 60
        else -> 30
    }
    return StreamFormat(
        url = cleanUrl,
        format = format,
        quality = quality.orEmpty().ifBlank { if (resolvedHeight > 0) "${resolvedHeight}p" else "" },
        mimeType = mimeType,
        codec = codec,
        videoOnly = videoOnly,
        bitrate = bitrate,
        initStart = initStart,
        initEnd = initEnd,
        indexStart = indexStart,
        indexEnd = indexEnd,
        width = if (width > 0) width else (resolvedHeight * 16) / 9,
        height = resolvedHeight,
        fps = resolvedFps,
        contentLength = contentLength,
        audioTrackId = audioTrackId,
        audioTrackName = audioTrackName,
        audioTrackType = audioTrackType,
        audioLocale = audioTrackLocale,
        itag = itag
    )
}

private fun PipedStreamItemDto.toRelatedStreamInfo(): RelatedStreamInfo? {
    val rawUrl = url ?: return null
    val videoId = when {
        rawUrl.startsWith("/watch?v=") -> rawUrl.substringAfter("/watch?v=").substringBefore("&").take(11)
        else -> YouTubeLinkParser.parseInput(rawUrl)?.videoId
    } ?: return null

    if (videoId.isBlank()) return null

    return RelatedStreamInfo(
        videoId = videoId,
        title = title ?: name.orEmpty(),
        uploaderName = uploaderName.orEmpty(),
        uploaderUrl = uploaderUrl.orEmpty(),
        uploaderAvatar = uploaderAvatar.orEmpty(),
        uploaderVerified = uploaderVerified || verified,
        thumbnailUrl = thumbnail?.takeIf { it.isNotBlank() }
            ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
        durationSeconds = duration.coerceAtLeast(0L),
        views = views.coerceAtLeast(0L),
        uploadedDate = uploadedDate.orEmpty()
    )
}

object HyperTubeRepository {
    @Volatile
    private var preferredBaseUrl: String = RetrofitInstance.PIPED_API_INSTANCES.first()

    private fun orderedInstances(): List<String> {
        val current = preferredBaseUrl
        return listOf(current) + RetrofitInstance.PIPED_API_INSTANCES.filterNot { it == current }
    }

    private suspend fun <T> executeWithPipedFailover(
        block: suspend (baseUrl: String) -> T
    ): T {
        var lastError: Throwable? = null
        for (base in orderedInstances()) {
            try {
                val result = block(base.trimEnd('/'))
                preferredBaseUrl = base
                return result
            } catch (t: Throwable) {
                lastError = t
            }
        }
        throw lastError ?: IllegalStateException("All Piped REST API instances failed.")
    }

    suspend fun fetchStream(videoId: String): HyperStreamData = withContext(Dispatchers.IO) {
        val resp = executeWithPipedFailover { baseUrl ->
            val streamResponse = RetrofitInstance.pipedApi.getStreamsByUrl("$baseUrl/streams/$videoId")
            if (streamResponse.title.isBlank() &&
                streamResponse.videoStreams.isEmpty() &&
                streamResponse.hls.isNullOrBlank()
            ) {
                throw IllegalStateException("Empty stream payload from $baseUrl")
            }
            streamResponse
        }

        val allVideoFormats = resp.videoStreams.mapNotNull { it.toStreamFormat() }
            .filter { it.height > 0 }

        val muxedStreams = allVideoFormats
            .filter { !it.videoOnly }
            .sortedWith(
                compareByDescending<StreamFormat> { it.height }
                    .thenByDescending { it.fps }
                    .thenByDescending { it.bitrate }
            )

        val adaptiveStreams = allVideoFormats
            .filter { it.videoOnly }
            .sortedWith(
                compareByDescending<StreamFormat> { it.height }
                    .thenByDescending { it.fps }
                    .thenByDescending { codecPriority(it.codec) }
                    .thenByDescending { it.bitrate }
            )

        val audioStreams = resp.audioStreams
            .mapNotNull { it.toStreamFormat() }
            .sortedByDescending { it.bitrate }

        val chapters = resp.chapters.map {
            ChapterInfo(
                title = it.title,
                startSeconds = it.start.coerceAtLeast(0L),
                previewUrl = it.image.orEmpty()
            )
        }

        val related = resp.relatedStreams.mapNotNull { it.toRelatedStreamInfo() }

        val subtitles = resp.subtitles.mapNotNull { sub ->
            val subUrl = sub.url?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            SubtitleTrack(
                url = subUrl,
                mimeType = sub.mimeType ?: "application/ttml+xml",
                languageName = sub.name.orEmpty(),
                languageCode = sub.code.orEmpty(),
                isAutoGenerated = sub.autoGenerated
            )
        }

        HyperStreamData(
            videoId = videoId,
            title = resp.title.ifBlank { "YouTube Video ($videoId)" },
            description = resp.description,
            uploaderName = resp.uploader,
            uploaderUrl = resp.uploaderUrl,
            uploaderAvatar = resp.uploaderAvatar.orEmpty(),
            uploaderSubscribers = resp.uploaderSubscriberCount,
            uploaderVerified = resp.uploaderVerified,
            views = resp.views,
            likes = resp.likes,
            durationSeconds = resp.duration.coerceAtLeast(0L),
            uploadDateText = resp.uploadDate.orEmpty(),
            thumbnailUrl = resp.thumbnailUrl.ifBlank {
                "https://i.ytimg.com/vi/$videoId/maxresdefault.jpg"
            },
            hlsUrl = resp.hls?.takeIf { it.isNotBlank() },
            dashMpdUrl = resp.dash?.takeIf { it.isNotBlank() },
            isLive = resp.livestream || (resp.duration <= 0L && !resp.hls.isNullOrBlank()),
            muxedVideoStreams = muxedStreams,
            adaptiveVideoStreams = adaptiveStreams,
            audioStreams = audioStreams,
            chapters = chapters,
            relatedStreams = related,
            subtitles = subtitles
        )
    }

    suspend fun searchYouTube(query: String): SearchResultsPage = withContext(Dispatchers.IO) {
        val resp = executeWithPipedFailover { baseUrl ->
            RetrofitInstance.pipedApi.searchByUrl(
                fullUrl = "$baseUrl/search",
                query = query,
                filter = "all"
            )
        }

        val channels = resp.items
            .filter { item ->
                item.type.equals("channel", ignoreCase = true) ||
                    item.url?.startsWith("/channel/") == true
            }
            .map { ch ->
                val rawUrl = ch.url.orEmpty()
                val channelId = rawUrl.substringAfterLast("/").ifBlank { ch.name.orEmpty() }
                SearchChannelItem(
                    channelId = channelId,
                    channelUrl = rawUrl,
                    name = ch.name ?: ch.title.orEmpty(),
                    avatarUrl = ch.thumbnail.orEmpty(),
                    subscriberCount = ch.subscribers,
                    videoCount = ch.videos,
                    description = ch.description.orEmpty(),
                    verified = ch.verified || ch.uploaderVerified
                )
            }

        val videos = resp.items
            .filter { item ->
                item.type.equals("stream", ignoreCase = true) ||
                    item.url?.startsWith("/watch") == true
            }
            .mapNotNull { it.toRelatedStreamInfo() }

        SearchResultsPage(
            query = query,
            channels = channels,
            videos = videos
        )
    }

    suspend fun fetchChannelPage(
        channelUrlOrId: String,
        fallbackName: String = ""
    ): ChannelPageData = withContext(Dispatchers.IO) {
        val cleanChannelId = channelUrlOrId
            .substringAfter("/channel/")
            .substringBefore("?")
            .substringBefore("/")
            .trim()

        if (cleanChannelId.startsWith("UC")) {
            val channelResp = runCatching {
                executeWithPipedFailover { baseUrl ->
                    RetrofitInstance.pipedApi.getChannelByUrl("$baseUrl/channel/$cleanChannelId")
                }
            }.getOrNull()

            if (channelResp != null) {
                return@withContext ChannelPageData(
                    channelId = channelResp.id ?: cleanChannelId,
                    channelUrl = "/channel/$cleanChannelId",
                    name = channelResp.name.ifBlank { fallbackName },
                    avatarUrl = channelResp.avatarUrl.orEmpty(),
                    bannerUrl = channelResp.bannerUrl.orEmpty(),
                    description = channelResp.description.orEmpty(),
                    subscriberCount = channelResp.subscriberCount,
                    verified = channelResp.verified,
                    videos = channelResp.relatedStreams.mapNotNull { it.toRelatedStreamInfo() }
                )
            }
        }

        // Fallback: search via Piped /search endpoint
        val searchFallback = searchYouTube(fallbackName.ifBlank { channelUrlOrId })
        val matchedChannel = searchFallback.channels.firstOrNull()
        ChannelPageData(
            channelId = matchedChannel?.channelId ?: cleanChannelId,
            channelUrl = matchedChannel?.channelUrl ?: channelUrlOrId,
            name = matchedChannel?.name?.ifBlank { fallbackName } ?: fallbackName,
            avatarUrl = matchedChannel?.avatarUrl.orEmpty(),
            description = matchedChannel?.description.orEmpty(),
            subscriberCount = matchedChannel?.subscriberCount ?: -1L,
            verified = matchedChannel?.verified ?: false,
            videos = searchFallback.videos
        )
    }
}
