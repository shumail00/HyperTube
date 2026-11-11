package com.github.libretube.ui.components

import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Base64
import android.view.TextureView
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.dash.manifest.DashManifestParser
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import coil.compose.AsyncImage
import com.github.libretube.api.HyperStreamData
import com.github.libretube.api.YouTubeInnertubeClient
import com.github.libretube.ui.models.formatDuration
import com.github.libretube.ui.theme.IosCyanTint
import com.github.libretube.ui.theme.IosElectricBlue
import com.github.libretube.ui.theme.IosLabelPrimary
import com.github.libretube.ui.theme.IosLabelSecondary
import com.github.libretube.ui.theme.IosMintGreen
import com.github.libretube.ui.theme.IosOledBlack
import com.github.libretube.ui.theme.IosSurfacePrimary
import com.github.libretube.ui.theme.IosSurfaceSecondary
import kotlinx.coroutines.delay
import java.io.ByteArrayInputStream
import kotlin.math.max

@OptIn(UnstableApi::class)
private fun buildFallbackMediaSource(
    stream: HyperStreamData,
    directStreamUrl: String?,
    directAudioUrl: String?,
    isAudioOnly: Boolean
): MediaSource? {
    val targetUrl = directStreamUrl ?: stream.hlsUrl ?: directAudioUrl ?: return null
    val userAgent = YouTubeInnertubeClient.resolveUserAgentForUrl(targetUrl)
    val httpFactory = DefaultHttpDataSource.Factory()
        .setUserAgent(userAgent)
        .setAllowCrossProtocolRedirects(true)

    val isHls = targetUrl.contains(".m3u8") || stream.isLive
    if (isHls) {
        val mediaItem = MediaItem.Builder()
            .setUri(targetUrl.toUri())
            .setMimeType(MimeTypes.APPLICATION_M3U8)
            .build()
        return HlsMediaSource.Factory(httpFactory).createMediaSource(mediaItem)
    }

    val videoSource = ProgressiveMediaSource.Factory(httpFactory)
        .createMediaSource(MediaItem.fromUri(targetUrl.toUri()))

    val needsSeparateAudio = !isAudioOnly &&
        stream.muxedVideoStreams.none { it.url == targetUrl } &&
        !directAudioUrl.isNullOrBlank() &&
        directAudioUrl != targetUrl

    return if (needsSeparateAudio && directAudioUrl != null) {
        val audioAgent = YouTubeInnertubeClient.resolveUserAgentForUrl(directAudioUrl)
        val audioHttpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(audioAgent)
            .setAllowCrossProtocolRedirects(true)
        val audioSource = ProgressiveMediaSource.Factory(audioHttpFactory)
            .createMediaSource(MediaItem.fromUri(directAudioUrl.toUri()))
        MergingMediaSource(videoSource, audioSource)
    } else {
        videoSource
    }
}

/**
 * Extracts a vibrant ambient color from a tiny 32x18 sampled video frame so the
 * Ambient Mode aura matches the live video scene colors (like pink/magenta in YOASOBI Idol).
 */
private fun extractVibrantColor(bitmap: Bitmap): Color {
    val width = bitmap.width
    val height = bitmap.height
    if (width <= 0 || height <= 0) return Color(0xFFE05297)

    var rSum = 0f
    var gSum = 0f
    var bSum = 0f
    var weightSum = 0f

    val stepX = max(1, width / 8)
    val stepY = max(1, height / 6)

    for (y in 0 until height step stepY) {
        for (x in 0 until width step stepX) {
            val pixel = bitmap.getPixel(x, y)
            val r = ((pixel shr 16) and 0xFF) / 255f
            val g = ((pixel shr 8) and 0xFF) / 255f
            val b = (pixel and 0xFF) / 255f

            val maxC = maxOf(r, g, b)
            val minC = minOf(r, g, b)
            val saturation = if (maxC > 0.05f) (maxC - minC) / maxC else 0f
            val luma = 0.299f * r + 0.587f * g + 0.114f * b

            // Weight vibrant, non-black, non-white pixels more heavily
            val weight = if (luma in 0.12f..0.92f) (0.35f + saturation * 2.4f) else 0.1f
            rSum += r * weight
            gSum += g * weight
            bSum += b * weight
            weightSum += weight
        }
    }

    if (weightSum <= 0f) return Color(0xFFE05297)
    val finalR = (rSum / weightSum).coerceIn(0.15f, 1f)
    val finalG = (gSum / weightSum).coerceIn(0.12f, 1f)
    val finalB = (bSum / weightSum).coerceIn(0.15f, 1f)
    return Color(finalR, finalG, finalB)
}

private fun updateTextureViewScale(textureView: TextureView, videoWidth: Int, videoHeight: Int) {
    val viewWidth = textureView.width.toFloat()
    val viewHeight = textureView.height.toFloat()
    if (viewWidth <= 0f || viewHeight <= 0f || videoWidth <= 0 || videoHeight <= 0) return

    val videoAspect = videoWidth.toFloat() / videoHeight.toFloat()
    val viewAspect = viewWidth / viewHeight

    var scaleX = 1f
    var scaleY = 1f
    if (videoAspect > viewAspect) {
        scaleY = viewAspect / videoAspect
    } else {
        scaleX = videoAspect / viewAspect
    }

    val matrix = Matrix()
    matrix.setScale(scaleX, scaleY, viewWidth / 2f, viewHeight / 2f)
    textureView.setTransform(matrix)
}

@OptIn(UnstableApi::class)
@Composable
fun IosVideoPlayerStage(
    stream: HyperStreamData?,
    dashDataUri: String?,
    directStreamUrl: String?,
    directAudioUrl: String?,
    availableQualities: List<Int>,
    selectedQualityHeight: Int,
    playbackSpeed: Float,
    isAudioOnly: Boolean,
    isAmbientModeEnabled: Boolean = true,
    onToggleAmbientMode: () -> Unit = {},
    onAmbientColorChanged: (Color) -> Unit = {},
    startTimestampSeconds: Long,
    seekRequestSeconds: Long?,
    onSeekRequestConsumed: () -> Unit,
    onSelectQuality: (Int) -> Unit,
    onSelectSpeed: (Float) -> Unit,
    onToggleAudioOnly: () -> Unit,
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    showExternalScrubber: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val exoPlayer = remember {
        ExoPlayer.Builder(context)
            .setSeekBackIncrementMs(10_000L)
            .setSeekForwardIncrementMs(10_000L)
            .build()
            .apply {
                playWhenReady = true
            }
    }

    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(false) }
    var currentPosMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var controlsVisible by remember { mutableStateOf(false) }
    var lastLoadedVideoId by remember { mutableStateOf<String?>(null) }
    var usedFallbackForCurrentConfig by remember { mutableStateOf(false) }
    var textureViewRef by remember { mutableStateOf<TextureView?>(null) }
    var videoWidth by remember { mutableStateOf(16) }
    var videoHeight by remember { mutableStateOf(9) }
    var liveAmbientBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var targetAmbientColor by remember { mutableStateOf(Color(0xFFE05297)) }

    val animatedAmbientColor by animateColorAsState(
        targetValue = if (isAmbientModeEnabled && stream != null) targetAmbientColor else Color.Transparent,
        animationSpec = tween(durationMillis = 750),
        label = "ambient_scene_color"
    )

    LaunchedEffect(animatedAmbientColor) {
        onAmbientColorChanged(animatedAmbientColor)
    }

    DisposableEffect(exoPlayer, stream, directStreamUrl, directAudioUrl, isAudioOnly) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) {
                    durationMs = exoPlayer.duration.coerceAtLeast(0L)
                }
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    videoWidth = videoSize.width
                    videoHeight = videoSize.height
                    textureViewRef?.let { tv ->
                        updateTextureViewScale(tv, videoSize.width, videoSize.height)
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                if (!usedFallbackForCurrentConfig && stream != null) {
                    usedFallbackForCurrentConfig = true
                    val resumeMs = exoPlayer.currentPosition.coerceAtLeast(0L)
                    val fallbackSource = buildFallbackMediaSource(
                        stream = stream,
                        directStreamUrl = directStreamUrl,
                        directAudioUrl = directAudioUrl,
                        isAudioOnly = isAudioOnly
                    )
                    if (fallbackSource != null) {
                        exoPlayer.setMediaSource(fallbackSource, resumeMs)
                        exoPlayer.prepare()
                        exoPlayer.playWhenReady = true
                    }
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    LaunchedEffect(playbackSpeed) {
        exoPlayer.playbackParameters = PlaybackParameters(playbackSpeed)
    }

    LaunchedEffect(seekRequestSeconds) {
        if (seekRequestSeconds != null && seekRequestSeconds >= 0L) {
            exoPlayer.seekTo(seekRequestSeconds * 1000L)
            exoPlayer.playWhenReady = true
            onSeekRequestConsumed()
        }
    }

    LaunchedEffect(stream?.videoId, dashDataUri, directStreamUrl, isAudioOnly, selectedQualityHeight) {
        if (stream == null) {
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            lastLoadedVideoId = null
            liveAmbientBitmap = null
            return@LaunchedEffect
        }

        usedFallbackForCurrentConfig = false
        val isSameVideo = lastLoadedVideoId == stream.videoId
        val resumePositionMs = if (isSameVideo) {
            exoPlayer.currentPosition.coerceAtLeast(0L)
        } else {
            liveAmbientBitmap = null
            startTimestampSeconds * 1000L
        }
        lastLoadedVideoId = stream.videoId

        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
            .buildUpon()
            .apply {
                if (selectedQualityHeight > 0 && !isAudioOnly) {
                    setMaxVideoSize(Int.MAX_VALUE, selectedQualityHeight)
                } else {
                    clearVideoSizeConstraints()
                }
            }
            .build()

        val dashSource = if (!isAudioOnly && !dashDataUri.isNullOrBlank()) {
            runCatching {
                val rawXml = if (dashDataUri.startsWith("data:application/dash+xml")) {
                    val base64Part = dashDataUri.substringAfter("base64,")
                    String(Base64.decode(base64Part, Base64.DEFAULT), Charsets.UTF_8)
                } else {
                    dashDataUri
                }
                val manifest = DashManifestParser().parse(
                    "https://www.youtube.com".toUri(),
                    ByteArrayInputStream(rawXml.toByteArray(Charsets.UTF_8))
                )
                val userAgent = YouTubeInnertubeClient.resolveUserAgentForUrl(directStreamUrl ?: rawXml)
                val dashHttpFactory = DefaultHttpDataSource.Factory()
                    .setUserAgent(userAgent)
                    .setAllowCrossProtocolRedirects(true)
                DashMediaSource.Factory(dashHttpFactory).createMediaSource(manifest)
            }.getOrNull()
        } else {
            null
        }

        if (dashSource != null) {
            exoPlayer.setMediaSource(dashSource, resumePositionMs)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
        } else {
            val fallbackSource = buildFallbackMediaSource(
                stream = stream,
                directStreamUrl = directStreamUrl,
                directAudioUrl = directAudioUrl,
                isAudioOnly = isAudioOnly
            )
            if (fallbackSource != null) {
                usedFallbackForCurrentConfig = true
                exoPlayer.setMediaSource(fallbackSource, resumePositionMs)
                exoPlayer.prepare()
                exoPlayer.playWhenReady = true
            }
        }
    }

    // Poll playback position & sample live video frame for Real-Time Ambient Mode
    LaunchedEffect(isPlaying, stream?.videoId, isAmbientModeEnabled, isAudioOnly) {
        var tick = 0
        while (stream != null) {
            currentPosMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            val d = exoPlayer.duration
            if (d != C.TIME_UNSET && d > 0) {
                durationMs = d
            }

            if (isAmbientModeEnabled && !isAudioOnly && isPlaying && tick % 2 == 0) {
                val tv = textureViewRef
                if (tv != null && tv.isAvailable && tv.width > 0 && tv.height > 0) {
                    runCatching {
                        val smallBmp = tv.getBitmap(40, 24)
                        if (smallBmp != null) {
                            targetAmbientColor = extractVibrantColor(smallBmp)
                            liveAmbientBitmap = smallBmp.asImageBitmap()
                        }
                    }
                }
            }
            tick++
            delay(200L)
        }
    }

    // Auto-hide controls after 3.2 seconds when playing
    LaunchedEffect(controlsVisible, isPlaying) {
        if (controlsVisible && isPlaying) {
            delay(3200L)
            controlsVisible = false
        }
    }

    val containerShape = if (isFullscreen) RoundedCornerShape(0.dp) else RoundedCornerShape(26.dp)
    val totalDurationSec = if (stream != null) {
        (durationMs / 1000L).coerceAtLeast(stream.durationSeconds)
    } else 0L
    val currentSec = (currentPosMs / 1000L).coerceAtLeast(0L)
    val progressFraction = if (totalDurationSec > 0) {
        (currentPosMs.toFloat() / (totalDurationSec * 1000f)).coerceIn(0f, 1f)
    } else 0f

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Video Player Frame + Real-Time Ambient Glow Layer
        Box(
            modifier = Modifier
                .then(
                    if (isFullscreen) Modifier.fillMaxSize()
                    else Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                ),
            contentAlignment = Alignment.Center
        ) {
            // Layer 1: Real-Time Ambient Mode Halo bleeding outside the rounded video card
            if (isAmbientModeEnabled && !isFullscreen && stream != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = 1.07f
                            scaleY = 1.11f
                            alpha = 0.72f
                        }
                        .drawBehind {
                            drawRoundRect(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        animatedAmbientColor.copy(alpha = 0.62f),
                                        animatedAmbientColor.copy(alpha = 0.28f),
                                        Color.Transparent
                                    ),
                                    center = Offset(size.width / 2f, size.height / 2f),
                                    radius = size.width * 0.68f
                                ),
                                cornerRadius = CornerRadius(36.dp.toPx(), 36.dp.toPx())
                            )
                        }
                        .blur(46.dp)
                ) {
                    val bmp = liveAmbientBitmap
                    if (bmp != null) {
                        Image(
                            bitmap = bmp,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            filterQuality = FilterQuality.Medium,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(32.dp))
                        )
                    } else {
                        AsyncImage(
                            model = stream.thumbnailUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(32.dp))
                        )
                    }
                }
            }

            // Layer 2: Crisp Rounded Video Card
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(containerShape)
                    .background(IosOledBlack)
                    .border(
                        width = if (isFullscreen) 0.dp else 1.dp,
                        color = Color.White.copy(alpha = 0.14f),
                        shape = containerShape
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        controlsVisible = !controlsVisible
                    }
                    .testTag("video_player_stage")
            ) {
                if (stream != null) {
                    if (isAudioOnly) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = stream.thumbnailUrl,
                                contentDescription = stream.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer { alpha = 0.32f }
                            )
                            androidx.compose.animation.AnimatedVisibility(
                                visible = !controlsVisible,
                                enter = fadeIn(),
                                exit = fadeOut()
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(24.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(68.dp)
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(Color.White.copy(alpha = 0.12f))
                                            .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(20.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.GraphicEq,
                                            contentDescription = "Audio Stream Active",
                                            tint = Color.White,
                                            modifier = Modifier.size(34.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "Audio-Only Mode",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = IosLabelPrimary
                                    )
                                    Text(
                                        text = stream.uploaderName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = IosLabelSecondary
                                    )
                                }
                            }
                        }
                    } else {
                        AndroidView(
                            factory = { ctx ->
                                FrameLayout(ctx).apply {
                                    layoutParams = FrameLayout.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                    val tv = TextureView(ctx).apply {
                                        layoutParams = FrameLayout.LayoutParams(
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                            ViewGroup.LayoutParams.MATCH_PARENT
                                        )
                                        addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                                            updateTextureViewScale(this, videoWidth, videoHeight)
                                        }
                                    }
                                    addView(tv)
                                    textureViewRef = tv
                                    exoPlayer.setVideoTextureView(tv)
                                }
                            },
                            update = { container ->
                                val tv = container.getChildAt(0) as? TextureView
                                if (tv != null && textureViewRef !== tv) {
                                    textureViewRef = tv
                                    exoPlayer.setVideoTextureView(tv)
                                }
                                if (tv != null) {
                                    updateTextureViewScale(tv, videoWidth, videoHeight)
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Minimal Buffering Indicator
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isBuffering,
                        enter = fadeIn() + scaleIn(),
                        exit = fadeOut() + scaleOut(),
                        modifier = Modifier.align(Alignment.Center)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.65f)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.5.dp,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    // Minimal iOS Controls Overlay (Only appears on tap)
                    androidx.compose.animation.AnimatedVisibility(
                        visible = controlsVisible,
                        enter = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)),
                        exit = fadeOut(spring(stiffness = Spring.StiffnessMediumLow)),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Black.copy(alpha = 0.58f),
                                            Color.Black.copy(alpha = 0.18f),
                                            Color.Black.copy(alpha = 0.64f)
                                        )
                                    )
                                )
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            // Top-Right Minimal Pills: Ambient, Audio Only, Quality, Speed
                            Row(
                                modifier = Modifier.align(Alignment.TopEnd),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IosControlPill(
                                    label = if (isAmbientModeEnabled) "Ambient: ON" else "Ambient",
                                    isActive = isAmbientModeEnabled,
                                    onClick = onToggleAmbientMode,
                                    testTag = "ambient_mode_toggle_pill"
                                )

                                IosControlPill(
                                    label = if (isAudioOnly) "Audio Only: ON" else "Audio Only",
                                    isActive = isAudioOnly,
                                    onClick = onToggleAudioOnly,
                                    testTag = "audio_only_toggle_pill"
                                )

                                QualitySelectorPill(
                                    availableQualities = availableQualities,
                                    selectedHeight = selectedQualityHeight,
                                    onSelectQuality = onSelectQuality
                                )

                                SpeedSelectorPill(
                                    currentSpeed = playbackSpeed,
                                    onSelectSpeed = onSelectSpeed
                                )
                            }

                            // Center Transport Controls
                            Row(
                                modifier = Modifier.align(Alignment.Center),
                                horizontalArrangement = Arrangement.spacedBy(28.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                SpringIconButton(
                                    onClick = { exoPlayer.seekBack() },
                                    testTag = "player_rewind_button"
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Replay10,
                                        contentDescription = "Rewind 10 seconds",
                                        tint = Color.White,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }

                                SpringIconButton(
                                    onClick = {
                                        if (exoPlayer.isPlaying) {
                                            exoPlayer.pause()
                                        } else {
                                            exoPlayer.play()
                                        }
                                    },
                                    sizeDp = 62,
                                    backgroundColor = Color.White.copy(alpha = 0.22f),
                                    testTag = "player_play_pause_button"
                                ) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                        contentDescription = if (isPlaying) "Pause" else "Play",
                                        tint = Color.White,
                                        modifier = Modifier.size(34.dp)
                                    )
                                }

                                SpringIconButton(
                                    onClick = { exoPlayer.seekForward() },
                                    testTag = "player_forward_button"
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Forward10,
                                        contentDescription = "Forward 10 seconds",
                                        tint = Color.White,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }

                            // Bottom-Left Timecode & Bottom-Right Fullscreen inside overlay
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.BottomCenter),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "${formatDuration(currentSec)} / ${formatDuration(totalDurationSec)}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color.White.copy(alpha = 0.9f)
                                    )
                                    if (selectedQualityHeight > 0 && !isAudioOnly) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color.White.copy(alpha = 0.16f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = formatQualityBadge(selectedQualityHeight),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .clickable { onToggleFullscreen() }
                                        .testTag("player_fullscreen_button"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isFullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                                        contentDescription = if (isFullscreen) "Exit Fullscreen" else "Enter Fullscreen",
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            // In fullscreen mode, also show the glowing scrubber inside the bottom overlay
                            if (isFullscreen) {
                                GlowingIosScrubberBar(
                                    progressFraction = progressFraction,
                                    onSeekFraction = { frac ->
                                        if (totalDurationSec > 0) {
                                            val targetMs = (frac * totalDurationSec * 1000L).toLong()
                                            currentPosMs = targetMs
                                            exoPlayer.seekTo(targetMs)
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = 42.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // External Minimalist Glowing White Scrubber Bar directly below the Video Card (matching the user's mockup!)
        if (!isFullscreen && showExternalScrubber && stream != null) {
            GlowingIosScrubberBar(
                progressFraction = progressFraction,
                onSeekFraction = { frac ->
                    if (totalDurationSec > 0) {
                        val targetMs = (frac * totalDurationSec * 1000L).toLong()
                        currentPosMs = targetMs
                        exoPlayer.seekTo(targetMs)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp)
                    .testTag("player_scrubber_slider")
            )
        }
    }
}

/**
 * Minimalist iOS pill progress bar with a soft glowing white bloom at the playhead tip,
 * matching the user's mockup directly below the video player.
 */
@Composable
fun GlowingIosScrubberBar(
    progressFraction: Float,
    onSeekFraction: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(progressFraction) }
    val activeFraction = if (isDragging) dragFraction else progressFraction

    Box(
        modifier = modifier
            .height(28.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val frac = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                    onSeekFraction(frac)
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        dragFraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                    },
                    onDragEnd = {
                        onSeekFraction(dragFraction)
                        isDragging = false
                    },
                    onDragCancel = {
                        isDragging = false
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        dragFraction = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                        onSeekFraction(dragFraction)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isDragging) 11.dp else 8.dp)
        ) {
            val trackHeight = size.height
            val cornerRadius = CornerRadius(trackHeight / 2f, trackHeight / 2f)

            // Unfilled muted background track
            drawRoundRect(
                color = Color.White.copy(alpha = 0.24f),
                size = Size(size.width, trackHeight),
                cornerRadius = cornerRadius
            )

            val filledWidth = (size.width * activeFraction.coerceIn(0.015f, 1f))

            // Soft white outer bloom at the leading tip of the progress bar (matching mockup!)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.85f),
                        Color.White.copy(alpha = 0.35f),
                        Color.Transparent
                    ),
                    center = Offset(filledWidth, trackHeight / 2f),
                    radius = trackHeight * 2.8f
                ),
                center = Offset(filledWidth, trackHeight / 2f),
                radius = trackHeight * 2.8f
            )

            // Crisp pure white filled track
            drawRoundRect(
                color = Color.White,
                size = Size(filledWidth, trackHeight),
                cornerRadius = cornerRadius
            )
        }
    }
}

private fun formatQualityBadge(height: Int): String = when {
    height >= 2160 -> "${height}p 4K"
    height >= 1440 -> "${height}p QHD"
    height >= 1080 -> "${height}p FHD"
    height >= 720 -> "${height}p HD"
    else -> "${height}p"
}

private fun formatQualityMenuLabel(height: Int): String = when {
    height >= 2160 -> "${height}p (4K UHD)"
    height >= 1440 -> "${height}p (2K QHD)"
    height >= 1080 -> "${height}p (Full HD)"
    height >= 720 -> "${height}p (HD)"
    else -> "${height}p"
}

@Composable
private fun SpringIconButton(
    onClick: () -> Unit,
    sizeDp: Int = 48,
    backgroundColor: Color = Color.Black.copy(alpha = 0.5f),
    testTag: String,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "spring_btn_scale"
    )

    Box(
        modifier = Modifier
            .size(sizeDp.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(backgroundColor)
            .border(1.dp, Color.White.copy(alpha = 0.22f), CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun IosControlPill(
    label: String,
    isActive: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Box(
        modifier = Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(50))
            .background(
                if (isActive) Color.White.copy(alpha = 0.28f) else Color.Black.copy(alpha = 0.55f)
            )
            .border(
                width = 1.dp,
                color = if (isActive) Color.White.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.18f),
                shape = RoundedCornerShape(50)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )
    }
}

@Composable
private fun QualitySelectorPill(
    availableQualities: List<Int>,
    selectedHeight: Int,
    onSelectQuality: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .height(32.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.Black.copy(alpha = 0.55f))
                .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(50))
                .clickable { expanded = true }
                .padding(horizontal = 11.dp)
                .testTag("quality_selector_pill"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.HighQuality,
                contentDescription = "Video Quality",
                tint = Color.White,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = if (selectedHeight > 0) "${selectedHeight}p" else "Auto",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(Color(0xFF1C1C1E))
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        text = "Auto (Best Adaptive)",
                        color = if (selectedHeight == 0) IosElectricBlue else IosLabelPrimary,
                        fontWeight = if (selectedHeight == 0) FontWeight.Bold else FontWeight.Normal
                    )
                },
                onClick = {
                    onSelectQuality(0)
                    expanded = false
                }
            )
            availableQualities.forEach { q ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = formatQualityMenuLabel(q),
                            color = if (selectedHeight == q) IosElectricBlue else IosLabelPrimary,
                            fontWeight = if (selectedHeight == q) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    onClick = {
                        onSelectQuality(q)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun SpeedSelectorPill(
    currentSpeed: Float,
    onSelectSpeed: (Float) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

    Box {
        Row(
            modifier = Modifier
                .height(32.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.Black.copy(alpha = 0.55f))
                .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(50))
                .clickable { expanded = true }
                .padding(horizontal = 11.dp)
                .testTag("speed_selector_pill"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Speed,
                contentDescription = "Playback Speed",
                tint = Color.White,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = "${currentSpeed}x",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(Color(0xFF1C1C1E))
        ) {
            speeds.forEach { spd ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "${spd}x",
                            color = if (currentSpeed == spd) IosElectricBlue else IosLabelPrimary,
                            fontWeight = if (currentSpeed == spd) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    onClick = {
                        onSelectSpeed(spd)
                        expanded = false
                    }
                )
            }
        }
    }
}
