package com.github.libretube.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

data class InnertubePlayerResult(
    val videoId: String,
    val title: String = "",
    val description: String = "",
    val author: String = "",
    val channelId: String = "",
    val viewCount: Long = 0L,
    val durationSeconds: Long = 0L,
    val isLive: Boolean = false,
    val thumbnailUrl: String = "",
    val hlsManifestUrl: String? = null,
    val dashManifestUrl: String? = null,
    val muxedStreams: List<StreamFormat> = emptyList(),
    val adaptiveVideoStreams: List<StreamFormat> = emptyList(),
    val audioStreams: List<StreamFormat> = emptyList(),
    val chapters: List<ChapterInfo> = emptyList()
)

object YouTubeInnertubeClient {
    const val USER_AGENT_ANDROID_VR =
        "com.google.android.apps.youtube.vr.oculus/1.61.48 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip"
    const val USER_AGENT_IOS =
        "com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_3_1 like Mac OS X;)"
    const val USER_AGENT_ANDROID =
        "com.google.android.youtube/20.10.38 (Linux; U; Android 14) gzip"

    private const val PLAYER_ENDPOINT = "https://www.youtube.com/youtubei/v1/player?prettyPrint=false"
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    private val RESOLUTION_REGEX = Regex("(\\d{3,4})p")

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    fun resolveUserAgentForUrl(url: String?): String {
        if (url.isNullOrBlank()) return USER_AGENT_ANDROID_VR
        return when {
            url.contains("c=IOS", ignoreCase = true) -> USER_AGENT_IOS
            url.contains("c=ANDROID_VR", ignoreCase = true) -> USER_AGENT_ANDROID_VR
            url.contains("c=ANDROID", ignoreCase = true) -> USER_AGENT_ANDROID
            else -> USER_AGENT_ANDROID_VR
        }
    }

    suspend fun fetchMultiClientStreams(videoId: String): InnertubePlayerResult? =
        withContext(Dispatchers.IO) {
            coroutineScope {
                val vrDeferred = async { runCatching { fetchAndroidVrClient(videoId) }.getOrNull() }
                val iosDeferred = async { runCatching { fetchIosClient(videoId) }.getOrNull() }

                val vrResult = vrDeferred.await()
                val iosResult = iosDeferred.await()

                val primary = vrResult ?: iosResult ?: return@coroutineScope null
                val secondary = if (primary === vrResult) iosResult else vrResult

                // Merge adaptive video streams, prioritizing ANDROID_VR first, then IOS for any missing heights
                val combinedAdaptive = mergeVideoStreams(
                    primary.adaptiveVideoStreams,
                    secondary?.adaptiveVideoStreams.orEmpty()
                )
                val combinedMuxed = (primary.muxedStreams + secondary?.muxedStreams.orEmpty())
                    .distinctBy { it.height }
                    .sortedByDescending { it.height }
                val combinedAudio = (primary.audioStreams + secondary?.audioStreams.orEmpty())
                    .distinctBy { "${it.itag}_${it.audioTrackId.orEmpty()}" }
                    .sortedByDescending { it.bitrate }

                primary.copy(
                    title = primary.title.ifBlank { secondary?.title.orEmpty() },
                    description = primary.description.ifBlank { secondary?.description.orEmpty() },
                    author = primary.author.ifBlank { secondary?.author.orEmpty() },
                    viewCount = if (primary.viewCount > 0) primary.viewCount else (secondary?.viewCount ?: 0L),
                    durationSeconds = if (primary.durationSeconds > 0) primary.durationSeconds else (secondary?.durationSeconds ?: 0L),
                    thumbnailUrl = primary.thumbnailUrl.ifBlank { secondary?.thumbnailUrl.orEmpty() },
                    hlsManifestUrl = primary.hlsManifestUrl ?: secondary?.hlsManifestUrl,
                    dashManifestUrl = primary.dashManifestUrl ?: secondary?.dashManifestUrl,
                    muxedStreams = combinedMuxed,
                    adaptiveVideoStreams = combinedAdaptive,
                    audioStreams = combinedAudio
                )
            }
        }

    private fun mergeVideoStreams(
        first: List<StreamFormat>,
        second: List<StreamFormat>
    ): List<StreamFormat> {
        val merged = mutableListOf<StreamFormat>()
        merged.addAll(first)
        for (candidate in second) {
            val alreadyHasSameItagAndClient = merged.any {
                it.itag == candidate.itag && it.height == candidate.height
            }
            if (!alreadyHasSameItagAndClient) {
                merged.add(candidate)
            }
        }
        return merged.sortedWith(
            compareByDescending<StreamFormat> { it.height }
                .thenByDescending { codecPriority(it.codec) }
                .thenByDescending { it.fps }
                .thenByDescending { it.bitrate }
        )
    }

    fun codecPriority(codec: String?): Int {
        val c = codec?.lowercase().orEmpty()
        return when {
            c.startsWith("avc1") -> 3 // Hardware decoded everywhere up to 1080p60
            c.startsWith("vp9") || c.startsWith("vp09") -> 2 // Great for 1440p / 4K
            c.startsWith("av01") -> 1
            else -> 0
        }
    }

    private fun fetchAndroidVrClient(videoId: String): InnertubePlayerResult? {
        val payload = """
            {
              "context": {
                "client": {
                  "clientName": "ANDROID_VR",
                  "clientVersion": "1.61.48",
                  "deviceMake": "Oculus",
                  "deviceModel": "Quest 3",
                  "androidSdkVersion": 32,
                  "osName": "Android",
                  "osVersion": "12L",
                  "hl": "en",
                  "gl": "US",
                  "utcOffsetMinutes": 0
                }
              },
              "videoId": "$videoId",
              "contentCheckOk": true,
              "racyCheckOk": true
            }
        """.trimIndent()

        return executePlayerRequest(videoId, payload, USER_AGENT_ANDROID_VR)
    }

    private fun fetchIosClient(videoId: String): InnertubePlayerResult? {
        val payload = """
            {
              "context": {
                "client": {
                  "clientName": "IOS",
                  "clientVersion": "20.10.4",
                  "deviceMake": "Apple",
                  "deviceModel": "iPhone16,2",
                  "osName": "iPhone",
                  "osVersion": "18.3.1.22D72",
                  "hl": "en",
                  "gl": "US",
                  "utcOffsetMinutes": 0
                }
              },
              "videoId": "$videoId",
              "contentCheckOk": true,
              "racyCheckOk": true
            }
        """.trimIndent()

        return executePlayerRequest(videoId, payload, USER_AGENT_IOS)
    }

    private fun executePlayerRequest(
        videoId: String,
        jsonBody: String,
        userAgent: String
    ): InnertubePlayerResult? {
        val request = Request.Builder()
            .url(PLAYER_ENDPOINT)
            .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
            .header("User-Agent", userAgent)
            .header("X-Goog-Api-Format-Version", "2")
            .header("Origin", "https://www.youtube.com")
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val bodyStr = response.body?.string() ?: return null
            val root = JsonHelper.json.parseToJsonElement(bodyStr).jsonObject

            val playability = root["playabilityStatus"]?.jsonObject
            val status = playability?.get("status")?.jsonPrimitive?.contentOrNull
            if (!status.equals("OK", ignoreCase = true)) {
                return null
            }

            val videoDetails = root["videoDetails"]?.jsonObject
            val streamingData = root["streamingData"]?.jsonObject ?: return null

            val title = videoDetails?.get("title")?.jsonPrimitive?.contentOrNull.orEmpty()
            val author = videoDetails?.get("author")?.jsonPrimitive?.contentOrNull.orEmpty()
            val channelId = videoDetails?.get("channelId")?.jsonPrimitive?.contentOrNull.orEmpty()
            val shortDescription = videoDetails?.get("shortDescription")?.jsonPrimitive?.contentOrNull.orEmpty()
            val viewCount = videoDetails?.get("viewCount")?.jsonPrimitive?.longOrNull ?: 0L
            val lengthSeconds = videoDetails?.get("lengthSeconds")?.jsonPrimitive?.longOrNull ?: 0L
            val isLive = videoDetails?.get("isLiveContent")?.jsonPrimitive?.booleanOrNull == true && lengthSeconds <= 0L

            val thumbnails = videoDetails
                ?.get("thumbnail")?.jsonObject
                ?.get("thumbnails")?.jsonArray
            val bestThumb = thumbnails
                ?.mapNotNull { it as? JsonObject }
                ?.maxByOrNull { it["height"]?.jsonPrimitive?.intOrNull ?: 0 }
                ?.get("url")?.jsonPrimitive?.contentOrNull
                .orEmpty()

            val hlsManifestUrl = streamingData["hlsManifestUrl"]?.jsonPrimitive?.contentOrNull
            val dashManifestUrl = streamingData["dashManifestUrl"]?.jsonPrimitive?.contentOrNull

            val muxedFormats = parseFormatsArray(
                formatsArray = streamingData["formats"]?.jsonArray,
                forceAdaptive = false,
                userAgent = userAgent
            ).filter { !it.videoOnly && it.height > 0 }

            val allAdaptive = parseFormatsArray(
                formatsArray = streamingData["adaptiveFormats"]?.jsonArray,
                forceAdaptive = true,
                userAgent = userAgent
            )

            val adaptiveVideos = allAdaptive
                .filter { it.videoOnly && it.height > 0 }
                .sortedWith(
                    compareByDescending<StreamFormat> { it.height }
                        .thenByDescending { codecPriority(it.codec) }
                        .thenByDescending { it.fps }
                        .thenByDescending { it.bitrate }
                )

            val audioStreams = allAdaptive
                .filter { !it.videoOnly && it.mimeType?.startsWith("audio/") == true }
                .sortedByDescending { it.bitrate }

            return InnertubePlayerResult(
                videoId = videoId,
                title = title,
                description = shortDescription,
                author = author,
                channelId = channelId,
                viewCount = viewCount,
                durationSeconds = lengthSeconds,
                isLive = isLive,
                thumbnailUrl = bestThumb,
                hlsManifestUrl = hlsManifestUrl,
                dashManifestUrl = dashManifestUrl,
                muxedStreams = muxedFormats,
                adaptiveVideoStreams = adaptiveVideos,
                audioStreams = audioStreams
            )
        }
    }

    private fun parseFormatsArray(
        formatsArray: JsonArray?,
        forceAdaptive: Boolean,
        userAgent: String
    ): List<StreamFormat> {
        if (formatsArray == null) return emptyList()
        val results = mutableListOf<StreamFormat>()

        for (element in formatsArray) {
            val obj = element as? JsonObject ?: continue
            val url = obj["url"]?.jsonPrimitive?.contentOrNull ?: continue
            if (url.isBlank() || url.startsWith("sabr://")) continue

            val itag = obj["itag"]?.jsonPrimitive?.intOrNull
            val rawMime = obj["mimeType"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val cleanMime = rawMime.substringBefore(";").trim()
            val codec = if (rawMime.contains("codecs=\"")) {
                rawMime.substringAfter("codecs=\"").substringBefore("\"")
            } else {
                null
            }

            val isVideo = cleanMime.startsWith("video/")
            val isAudio = cleanMime.startsWith("audio/")
            if (!isVideo && !isAudio) continue

            val rawWidth = obj["width"]?.jsonPrimitive?.intOrNull ?: 0
            val rawHeight = obj["height"]?.jsonPrimitive?.intOrNull ?: 0
            val qualityLabel = obj["qualityLabel"]?.jsonPrimitive?.contentOrNull
                ?: obj["quality"]?.jsonPrimitive?.contentOrNull
                .orEmpty()

            val normalizedHeight = normalizeResolutionHeight(rawWidth, rawHeight, qualityLabel)
            val fps = obj["fps"]?.jsonPrimitive?.intOrNull ?: 30
            val bitrate = obj["bitrate"]?.jsonPrimitive?.intOrNull
                ?: obj["averageBitrate"]?.jsonPrimitive?.intOrNull
                ?: 0
            val contentLength = obj["contentLength"]?.jsonPrimitive?.longOrNull ?: 0L

            val initRange = obj["initRange"]?.jsonObject
            val initStart = initRange?.get("start")?.jsonPrimitive?.intOrNull ?: -1
            val initEnd = initRange?.get("end")?.jsonPrimitive?.intOrNull ?: -1

            val indexRange = obj["indexRange"]?.jsonObject
            val indexStart = indexRange?.get("start")?.jsonPrimitive?.intOrNull ?: -1
            val indexEnd = indexRange?.get("end")?.jsonPrimitive?.intOrNull ?: -1

            val audioTrackObj = obj["audioTrack"]?.jsonObject
            val audioTrackId = audioTrackObj?.get("id")?.jsonPrimitive?.contentOrNull
            val audioTrackName = audioTrackObj?.get("displayName")?.jsonPrimitive?.contentOrNull
            val audioTrackType = obj["audioTrackType"]?.jsonPrimitive?.contentOrNull

            results.add(
                StreamFormat(
                    url = url,
                    format = cleanMime.substringAfter("/").uppercase(),
                    quality = qualityLabel.ifBlank { if (normalizedHeight > 0) "${normalizedHeight}p" else "${bitrate / 1000}kbps" },
                    mimeType = cleanMime,
                    codec = codec,
                    videoOnly = forceAdaptive && isVideo,
                    bitrate = bitrate,
                    initStart = initStart,
                    initEnd = initEnd,
                    indexStart = indexStart,
                    indexEnd = indexEnd,
                    width = if (rawWidth > 0) rawWidth else (normalizedHeight * 16) / 9,
                    height = normalizedHeight,
                    fps = fps,
                    contentLength = contentLength,
                    audioTrackId = audioTrackId,
                    audioTrackName = audioTrackName,
                    audioTrackType = audioTrackType,
                    itag = itag,
                    userAgent = userAgent
                )
            )
        }

        return results
    }

    fun normalizeResolutionHeight(width: Int, height: Int, qualityLabel: String?): Int {
        if (!qualityLabel.isNullOrBlank()) {
            RESOLUTION_REGEX.find(qualityLabel)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { parsed ->
                if (parsed > 0) return parsed
            }
        }
        if (height <= 0 && width <= 0) return 0
        // Use the shorter dimension for standard landscape/vertical comparison if needed
        val shortSide = if (width > 0 && height > 0) minOf(width, height) else maxOf(width, height)
        return when {
            shortSide >= 2000 -> 2160
            shortSide in 1300..1999 -> 1440
            shortSide in 950..1299 -> 1080
            shortSide in 650..949 -> 720
            shortSide in 420..649 -> 480
            shortSide in 300..419 -> 360
            shortSide in 200..299 -> 240
            shortSide in 100..199 -> 144
            else -> height
        }
    }
}
