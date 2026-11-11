package com.github.libretube.api

import android.net.Uri
import androidx.core.net.toUri
import com.github.libretube.api.poToken.PoTokenGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.VideoStream
import org.schabi.newpipe.extractor.channel.ChannelInfoItem

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
        if (VIDEO_ID_REGEX.matches(trimmed) && (trimmed.any { it.isDigit() } || trimmed.contains('-') || trimmed.contains('_'))) {
            return true
        }
        val lower = trimmed.lowercase()
        return lower.contains("youtube.com/") ||
            lower.contains("youtu.be/") ||
            lower.contains("youtube-nocookie.com/")
    }

    fun parseInput(rawInput: String): ParsedLink? {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return null

        // Direct 11-char video ID
        if (VIDEO_ID_REGEX.matches(trimmed)) {
            return ParsedLink(videoId = trimmed)
        }

        // Extract URL if shared with surrounding text
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

private fun VideoStream.toStreamFormat(isVideoOnly: Boolean): StreamFormat {
    val resLabel = getResolution().orEmpty()
    val normalizedHeight = YouTubeInnertubeClient.normalizeResolutionHeight(width, height, resLabel)
    return StreamFormat(
        url = content,
        codec = codec,
        format = format?.toString(),
        height = normalizedHeight,
        width = if (width > 0) width else (normalizedHeight * 16) / 9,
        quality = resLabel.ifBlank { "${normalizedHeight}p" },
        mimeType = format?.mimeType ?: "video/mp4",
        bitrate = bitrate,
        initStart = initStart,
        initEnd = initEnd,
        indexStart = indexStart,
        indexEnd = indexEnd,
        fps = if (fps > 0) fps else 30,
        contentLength = itagItem?.contentLength ?: 0L,
        videoOnly = isVideoOnly,
        itag = itagItem?.id,
        userAgent = YouTubeInnertubeClient.resolveUserAgentForUrl(content)
    )
}

private fun AudioStream.toStreamFormat() = StreamFormat(
    url = content,
    format = format?.toString(),
    quality = "${averageBitrate}kbps",
    bitrate = bitrate.coerceAtLeast(averageBitrate * 1000),
    mimeType = format?.mimeType ?: "audio/mp4",
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
    itag = itagItem?.id,
    userAgent = YouTubeInnertubeClient.resolveUserAgentForUrl(content)
)

private fun StreamInfoItem.toRelatedStreamInfo(): RelatedStreamInfo? {
    val relId = YouTubeLinkParser.parseInput(url)?.videoId ?: return null
    return RelatedStreamInfo(
        videoId = relId,
        title = name.orEmpty(),
        uploaderName = uploaderName.orEmpty(),
        uploaderUrl = uploaderUrl.orEmpty(),
        uploaderAvatar = uploaderAvatars.maxByOrNull { it.height }?.url.orEmpty(),
        uploaderVerified = isUploaderVerified,
        thumbnailUrl = thumbnails.maxByOrNull { it.height }?.url
            ?: "https://i.ytimg.com/vi/$relId/hqdefault.jpg",
        durationSeconds = duration,
        views = viewCount,
        uploadedDate = textualUploadDate
            ?: runCatching { uploadDate?.offsetDateTime()?.toLocalDate()?.toString() }.getOrNull().orEmpty()
    )
}

object HyperTubeRepository {
    private const val YOUTUBE_BASE = "https://www.youtube.com"

    init {
        runCatching {
            YoutubeStreamExtractor.setPoTokenProvider(PoTokenGenerator())
            YoutubeStreamExtractor.setFetchIosClient(true)
        }
    }

    suspend fun fetchStream(videoId: String): HyperStreamData = withContext(Dispatchers.IO) {
        coroutineScope {
            val url = "$YOUTUBE_BASE/watch?v=$videoId"

            val innertubeDeferred = async {
                runCatching { YouTubeInnertubeClient.fetchMultiClientStreams(videoId) }.getOrNull()
            }
            val newPipeDeferred = async {
                runCatching { StreamInfo.getInfo(NewPipeExtractorInstance.extractor, url) }.getOrNull()
            }

            val innertube = innertubeDeferred.await()
            val resp = newPipeDeferred.await()

            if (resp == null && innertube == null) {
                throw IllegalStateException("Unable to extract YouTube stream for video ID: $videoId")
            }

            val npMuxed = resp?.videoStreams.orEmpty()
                .filter { !it.content.isNullOrBlank() && !it.content.startsWith("sabr://") }
                .map { it.toStreamFormat(isVideoOnly = false) }
                .filter { it.height > 0 }

            val npAdaptive = resp?.videoOnlyStreams.orEmpty()
                .filter { !it.content.isNullOrBlank() && !it.content.startsWith("sabr://") }
                .map { it.toStreamFormat(isVideoOnly = true) }
                .filter { it.height > 0 }

            val npAudios = resp?.audioStreams.orEmpty()
                .filter { !it.content.isNullOrBlank() && !it.content.startsWith("sabr://") }
                .map { it.toStreamFormat() }

            val mergedAdaptive = buildList {
                addAll(innertube?.adaptiveVideoStreams.orEmpty())
                for (npStream in npAdaptive) {
                    if (none { it.height == npStream.height && it.itag == npStream.itag }) {
                        add(npStream)
                    }
                }
            }.sortedWith(
                compareByDescending<StreamFormat> { it.height }
                    .thenByDescending { YouTubeInnertubeClient.codecPriority(it.codec) }
                    .thenByDescending { it.fps }
                    .thenByDescending { it.bitrate }
            )

            val mergedMuxed = (innertube?.muxedStreams.orEmpty() + npMuxed)
                .distinctBy { it.height }
                .sortedByDescending { it.height }

            val mergedAudios = (innertube?.audioStreams.orEmpty() + npAudios)
                .distinctBy { "${it.userAgent}_${it.itag}_${it.audioTrackId.orEmpty()}" }
                .sortedByDescending { it.bitrate }

            val related = resp?.relatedItems.orEmpty()
                .filterIsInstance<StreamInfoItem>()
                .mapNotNull { it.toRelatedStreamInfo() }

            val chapters = resp?.streamSegments.orEmpty().map {
                ChapterInfo(
                    title = it.title.orEmpty(),
                    startSeconds = it.startTimeSeconds.toLong(),
                    previewUrl = it.previewUrl.orEmpty()
                )
            }

            val subtitles = resp?.subtitles.orEmpty()
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

            val uploadDateText = resp?.textualUploadDate
                ?: runCatching { resp?.uploadDate?.offsetDateTime()?.toLocalDate()?.toString() }.getOrNull().orEmpty()

            val resolvedDuration = when {
                (resp?.duration ?: 0L) > 0L -> resp!!.duration
                (innertube?.durationSeconds ?: 0L) > 0L -> innertube!!.durationSeconds
                else -> 0L
            }

            val resolvedHls = innertube?.hlsManifestUrl?.takeIf { it.isNotBlank() }
                ?: resp?.hlsUrl?.takeIf { it.isNotBlank() }

            val resolvedDash = innertube?.dashManifestUrl?.takeIf { it.isNotBlank() }
                ?: resp?.dashMpdUrl?.takeIf { it.isNotBlank() }

            val resolvedUploaderUrl = resp?.uploaderUrl?.takeIf { it.isNotBlank() }
                ?: innertube?.channelId?.takeIf { it.isNotBlank() }?.let { "$YOUTUBE_BASE/channel/$it" }
                .orEmpty()

            HyperStreamData(
                videoId = videoId,
                title = resp?.name?.takeIf { it.isNotBlank() } ?: innertube?.title.orEmpty(),
                description = resp?.description?.content?.takeIf { it.isNotBlank() } ?: innertube?.description.orEmpty(),
                uploaderName = resp?.uploaderName?.takeIf { it.isNotBlank() } ?: innertube?.author.orEmpty(),
                uploaderUrl = resolvedUploaderUrl,
                uploaderAvatar = resp?.uploaderAvatars?.maxByOrNull { it.height }?.url.orEmpty(),
                uploaderSubscribers = resp?.uploaderSubscriberCount ?: -1L,
                uploaderVerified = resp?.isUploaderVerified ?: false,
                views = if ((resp?.viewCount ?: 0L) > 0L) resp!!.viewCount else (innertube?.viewCount ?: 0L),
                likes = resp?.likeCount ?: -1L,
                durationSeconds = resolvedDuration,
                uploadDateText = uploadDateText,
                thumbnailUrl = resp?.thumbnails?.maxByOrNull { it.height }?.url
                    ?: innertube?.thumbnailUrl?.takeIf { it.isNotBlank() }
                    ?: "https://i.ytimg.com/vi/$videoId/maxresdefault.jpg",
                hlsUrl = resolvedHls,
                dashMpdUrl = resolvedDash,
                isLive = (resolvedDuration <= 0L && !resolvedHls.isNullOrBlank()) || (innertube?.isLive == true),
                muxedVideoStreams = mergedMuxed,
                adaptiveVideoStreams = mergedAdaptive,
                audioStreams = mergedAudios,
                chapters = chapters,
                relatedStreams = related,
                subtitles = subtitles
            )
        }
    }

    suspend fun searchYouTube(query: String): SearchResultsPage = withContext(Dispatchers.IO) {
        val service = NewPipeExtractorInstance.extractor
        val searchHandler = service.searchQHFactory.fromQuery(query)
        val searchInfo = SearchInfo.getInfo(service, searchHandler)

        val channels = searchInfo.relatedItems
            .filterIsInstance<ChannelInfoItem>()
            .map { ch ->
                SearchChannelItem(
                    channelId = ch.url?.substringAfterLast("/") ?: ch.name.orEmpty(),
                    channelUrl = ch.url.orEmpty(),
                    name = ch.name.orEmpty(),
                    avatarUrl = ch.thumbnails.maxByOrNull { it.height }?.url.orEmpty(),
                    subscriberCount = ch.subscriberCount,
                    videoCount = ch.streamCount,
                    description = ch.description.orEmpty(),
                    verified = ch.isVerified
                )
            }

        val videos = searchInfo.relatedItems
            .filterIsInstance<StreamInfoItem>()
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
        val service = NewPipeExtractorInstance.extractor
        val normalizedUrl = when {
            channelUrlOrId.startsWith("http://") || channelUrlOrId.startsWith("https://") -> channelUrlOrId
            channelUrlOrId.startsWith("UC") -> "$YOUTUBE_BASE/channel/$channelUrlOrId"
            channelUrlOrId.startsWith("@") -> "$YOUTUBE_BASE/$channelUrlOrId"
            else -> "$YOUTUBE_BASE/channel/$channelUrlOrId"
        }

        val info = runCatching { ChannelInfo.getInfo(service, normalizedUrl) }.getOrNull()
        if (info != null) {
            val videosTabHandler = info.tabs.firstOrNull { it.contentFilters.contains(ChannelTabs.VIDEOS) }
                ?: info.tabs.firstOrNull()
            val tabItems = if (videosTabHandler != null) {
                runCatching {
                    ChannelTabInfo.getInfo(service, videosTabHandler)
                        .relatedItems
                        .filterIsInstance<StreamInfoItem>()
                        .mapNotNull { it.toRelatedStreamInfo() }
                }.getOrDefault(emptyList())
            } else {
                emptyList()
            }

            return@withContext ChannelPageData(
                channelId = info.id.orEmpty(),
                channelUrl = info.url ?: normalizedUrl,
                name = info.name.orEmpty().ifBlank { fallbackName },
                avatarUrl = info.avatars.maxByOrNull { it.height }?.url.orEmpty(),
                bannerUrl = info.banners.maxByOrNull { it.height }?.url.orEmpty(),
                description = info.description.orEmpty(),
                subscriberCount = info.subscriberCount,
                verified = info.isVerified,
                videos = tabItems
            )
        }

        // Fallback: search by channel name if direct channel URL failed
        val searchFallback = searchYouTube(fallbackName.ifBlank { channelUrlOrId })
        val matchedChannel = searchFallback.channels.firstOrNull()
        ChannelPageData(
            channelId = matchedChannel?.channelId ?: channelUrlOrId,
            channelUrl = matchedChannel?.channelUrl ?: normalizedUrl,
            name = matchedChannel?.name?.ifBlank { fallbackName } ?: fallbackName,
            avatarUrl = matchedChannel?.avatarUrl.orEmpty(),
            description = matchedChannel?.description.orEmpty(),
            subscriberCount = matchedChannel?.subscriberCount ?: -1L,
            verified = matchedChannel?.verified ?: false,
            videos = searchFallback.videos
        )
    }
}
