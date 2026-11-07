package com.github.libretube.api

import android.net.Uri
import androidx.core.net.toUri
import com.github.libretube.api.poToken.PoTokenGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.VideoStream

object YouTubeLinkParser {
    private val VIDEO_ID_REGEX = Regex("^[a-zA-Z0-9_-]{11}$")
    private val URL_EXTRACT_REGEX = Regex("(https?://[^\\s]+)")

    data class ParsedLink(
        val videoId: String,
        val timestampSeconds: Long = 0L
    )

    fun parseInput(rawInput: String): ParsedLink? {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return null

        // Direct 11-char video ID
        if (VIDEO_ID_REGEX.matches(trimmed)) {
            return ParsedLink(videoId = trimmed)
        }

        // Extract URL if shared with surrounding text (e.g. "Check out this video https://youtu.be/...")
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

        var totalSeconds = 0L
        val hours = Regex("(\\d+)h").find(rawTime)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val minutes = Regex("(\\d+)m").find(rawTime)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val seconds = Regex("(\\d+)s").find(rawTime)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        totalSeconds = hours * 3600 + minutes * 60 + seconds
        return totalSeconds
    }
}

private fun VideoStream.toStreamFormat(isVideoOnly: Boolean) = StreamFormat(
    url = content,
    codec = codec,
    format = format?.toString(),
    height = height,
    width = width,
    quality = getResolution(),
    mimeType = format?.mimeType,
    bitrate = bitrate,
    initStart = initStart,
    initEnd = initEnd,
    indexStart = indexStart,
    indexEnd = indexEnd,
    fps = fps,
    contentLength = itagItem?.contentLength ?: 0L,
    videoOnly = isVideoOnly,
    itag = itagItem?.id
)

private fun AudioStream.toStreamFormat() = StreamFormat(
    url = content,
    format = format?.toString(),
    quality = "${averageBitrate}kbps",
    bitrate = bitrate,
    mimeType = format?.mimeType,
    initStart = initStart,
    initEnd = initEnd,
    indexStart = indexStart,
    indexEnd = indexEnd,
    contentLength = itagItem?.contentLength ?: 0L,
    codec = codec,
    audioTrackId = audioTrackId,
    audioTrackName = audioTrackName,
    audioLocale = audioLocale?.toLanguageTag(),
    audioTrackType = audioTrackType?.name,
    videoOnly = false,
    itag = itagItem?.id
)

object HyperTubeRepository {
    private const val YOUTUBE_BASE = "https://www.youtube.com"

    init {
        runCatching {
            YoutubeStreamExtractor.setPoTokenProvider(PoTokenGenerator())
        }
    }

    suspend fun fetchStream(videoId: String): HyperStreamData = withContext(Dispatchers.IO) {
        val url = "$YOUTUBE_BASE/watch?v=$videoId"
        val resp = StreamInfo.getInfo(NewPipeExtractorInstance.extractor, url)

        val muxed = resp.videoStreams
            .filter { !it.content.isNullOrBlank() && !it.content.startsWith("sabr://") }
            .map { it.toStreamFormat(isVideoOnly = false) }
            .sortedByDescending { it.height }

        val adaptiveVideos = resp.videoOnlyStreams
            .filter { !it.content.isNullOrBlank() && !it.content.startsWith("sabr://") }
            .map { it.toStreamFormat(isVideoOnly = true) }
            .sortedWith(compareByDescending<StreamFormat> { it.height }.thenByDescending { it.fps }.thenByDescending { it.bitrate })

        val audios = resp.audioStreams
            .filter { !it.content.isNullOrBlank() && !it.content.startsWith("sabr://") }
            .map { it.toStreamFormat() }
            .sortedByDescending { it.bitrate }

        val related = resp.relatedItems
            .filterIsInstance<StreamInfoItem>()
            .mapNotNull { item ->
                val relId = YouTubeLinkParser.parseInput(item.url)?.videoId ?: return@mapNotNull null
                RelatedStreamInfo(
                    videoId = relId,
                    title = item.name.orEmpty(),
                    uploaderName = item.uploaderName.orEmpty(),
                    uploaderVerified = item.isUploaderVerified,
                    thumbnailUrl = item.thumbnails.maxByOrNull { it.height }?.url.orEmpty(),
                    durationSeconds = item.duration,
                    views = item.viewCount,
                    uploadedDate = item.textualUploadDate
                        ?: item.uploadDate?.offsetDateTime()?.toLocalDate()?.toString().orEmpty()
                )
            }

        val chapters = resp.streamSegments.map {
            ChapterInfo(
                title = it.title.orEmpty(),
                startSeconds = it.startTimeSeconds.toLong(),
                previewUrl = it.previewUrl.orEmpty()
            )
        }

        val subtitles = resp.subtitles
            .filter { !it.content.isNullOrBlank() }
            .map {
                SubtitleTrack(
                    url = it.content,
                    mimeType = it.format?.mimeType ?: "application/ttml+xml",
                    languageName = it.displayLanguageName.orEmpty(),
                    languageCode = it.languageTag.orEmpty(),
                    isAutoGenerated = it.isAutoGenerated
                )
            }

        val uploadDateText = resp.textualUploadDate
            ?: runCatching { resp.uploadDate?.offsetDateTime()?.toLocalDate()?.toString() }.getOrNull().orEmpty()

        HyperStreamData(
            videoId = videoId,
            title = resp.name.orEmpty(),
            description = resp.description?.content.orEmpty(),
            uploaderName = resp.uploaderName.orEmpty(),
            uploaderAvatar = resp.uploaderAvatars.maxByOrNull { it.height }?.url.orEmpty(),
            uploaderSubscribers = resp.uploaderSubscriberCount,
            uploaderVerified = resp.isUploaderVerified,
            views = resp.viewCount,
            likes = resp.likeCount,
            durationSeconds = resp.duration,
            uploadDateText = uploadDateText,
            thumbnailUrl = resp.thumbnails.maxByOrNull { it.height }?.url
                ?: "https://i.ytimg.com/vi/$videoId/maxresdefault.jpg",
            hlsUrl = resp.hlsUrl?.takeIf { it.isNotBlank() },
            dashMpdUrl = resp.dashMpdUrl?.takeIf { it.isNotBlank() },
            isLive = resp.duration <= 0 && !resp.hlsUrl.isNullOrBlank(),
            muxedVideoStreams = muxed,
            adaptiveVideoStreams = adaptiveVideos,
            audioStreams = audios,
            chapters = chapters,
            relatedStreams = related,
            subtitles = subtitles
        )
    }
}
