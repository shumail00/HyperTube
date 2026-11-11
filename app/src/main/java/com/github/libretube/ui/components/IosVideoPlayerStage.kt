package com.github.libretube.ui.components

import android.graphics.drawable.BitmapDrawable
import android.util.Base64
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.dash.manifest.DashManifestParser
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.github.libretube.api.HyperStreamData
import com.github.libretube.api.PIPED_USER_AGENT
import com.github.libretube.ui.models.formatDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
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
    val httpFactory = DefaultHttpDataSource.Factory()
        .setUserAgent(PIPED_USER_AGENT)
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
        val audioSource = ProgressiveMediaSource.Factory(httpFactory)
            .createMediaSource(MediaItem.fromUri(directAudioUrl.toUri()))
        MergingMediaSource(videoSource, audioSource)
    } else {
        videoSource
    }
}

/**
 * Samples the static thumbnail once in the background when a new video loads to obtain a
 * soft vignette tint color without running any per-frame GPU blur shaders or TextureView polling.
 */
private suspend fun sampleThumbnailVignetteColor(
    context: android.content.Context,
    thumbnailUrl: String
): Color = withContext(Dispatchers.IO) {
    if (thumbnailUrl.isBlank()) return@withContext Color(0xFF4F7CFF)
    runCatching {
        val loader = ImageLoader(context)
        val request = ImageRequest.Builder(context)
            .data(thumbnailUrl)
            .size(48, 27)
            .allowHardware(false)
            .build()
        val result = loader.execute(request)
        val bmp = (result as? SuccessResult)?.drawable?.let { it as? BitmapDrawable }?.bitmap
            ?: return@withContext Color(0xFF4F7CFF)

        var rSum = 0f
        var gSum = 0f
        var bSum = 0f
        var weightSum = 0f
        val stepX = max(1, bmp.width / 8)
        val stepY = max(1, bmp.height / 6)

        for (y in 0 until bmp.height step stepY) {
            for (x in 0 until bmp.width step stepX) {
                val pixel = bmp.getPixel(x, y)
                val r = ((pixel shr 16) and 0xFF) / 255f
                val g = ((pixel shr 8) and 0xFF) / 255f
                val b = (pixel and 0xFF) / 255f
                val maxC = maxOf(r, g, b)
                val minC = minOf(r, g, b)
                val sat = if (maxC > 0.05f) (maxC - minC) / maxC else 0f
                val luma = 0.299f * r + 0.587f * g + 0.114f * b
                val w = if (luma in 0.12f..0.90f) (0.3f + sat * 2.2f) else 0.08f
                rSum += r * w
                gSum += g * w
                bSum += b * w
                weightSum += w
            }
        }
        if (weightSum <= 0f) Color(0xFF4F7CFF)
        else Color(
            red = (rSum / weightSum).coerceIn(0.18f, 0.95f),
            green = (gSum / weightSum).coerceIn(0.16f, 0.95f),
            blue = (bSum / weightSum).coerceIn(0.20f, 0.98f)
        )
    }.getOrDefault(Color(0xFF4F7CFF))
}

@OptIn(UnstableApi::class)
@Composable
fun IosVideoPlayerStage(
    stream: HyperStreamData?,
    isLoadingStream: Boolean = false,
    dashDataUri: String?,
    directStreamUrl: String?,
    directAudioUrl: String?,
    selectedQualityHeight: Int,
    selectedQualityLabel: String,
    playbackSpeed: Float,
    isAudioOnly: Boolean,
    isAmbientModeEnabled: Boolean = true,
    onToggleAmbientMode: () -> Unit = {},
    startTimestampSeconds: Long,
    seekRequestSeconds: Long?,
    onSeekRequestConsumed: () -> Unit,
    onToggleAudioOnly: () -> Unit,
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    onQuickPlayDefault: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Single persistent ExoPlayer + PlayerView retained across video ID switches
    val exoPlayer = remember {
        ExoPlayer.Builder(context)
            .setSeekBackIncrementMs(10_000L)
            .setSeekForwardIncrementMs(10_000L)
            .build()
            .apply {
                playWhenReady = true
            }
    }

    val persistentPlayerView = remember {
        PlayerView(context).apply {
            useController = false
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            keepScreenOn = true
        }
    }

    DisposableEffect(exoPlayer, persistentPlayerView) {
        persistentPlayerView.player = exoPlayer
        onDispose {
            persistentPlayerView.player = null
            exoPlayer.release()
        }
    }

    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(false) }
    var lastLoadedVideoId by remember { mutableStateOf<String?>(null) }
    var usedFallbackForCurrentConfig by remember { mutableStateOf(false) }
    var vignetteTint by remember { mutableStateOf(Color(0xFF4F7CFF)) }

    LaunchedEffect(stream?.videoId, stream?.thumbnailUrl) {
        val thumb = stream?.thumbnailUrl.orEmpty()
        vignetteTint = if (thumb.isNotBlank()) {
            sampleThumbnailVignetteColor(context, thumb)
        } else {
            Color(0xFF4F7CFF)
        }
    }

    val animatedVignetteColor by animateColorAsState(
        targetValue = if (isAmbientModeEnabled && stream != null) vignetteTint else Color.Transparent,
        animationSpec = tween(durationMillis = 700),
        label = "vignette_color"
    )

    DisposableEffect(exoPlayer, stream, directStreamUrl, directAudioUrl, isAudioOnly) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
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
            return@LaunchedEffect
        }

        usedFallbackForCurrentConfig = false
        val isSameVideo = lastLoadedVideoId == stream.videoId
        val resumePositionMs = if (isSameVideo) {
            exoPlayer.currentPosition.coerceAtLeast(0L)
        } else {
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
                val dashHttpFactory = DefaultHttpDataSource.Factory()
                    .setUserAgent(PIPED_USER_AGENT)
                    .setAllowCrossProtocolRedirects(true)

                if (dashDataUri.startsWith("http://") || dashDataUri.startsWith("https://")) {
                    DashMediaSource.Factory(dashHttpFactory)
                        .createMediaSource(MediaItem.fromUri(dashDataUri.toUri()))
                } else {
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
                    DashMediaSource.Factory(dashHttpFactory).createMediaSource(manifest)
                }
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

    // Auto-hide controls after 3.2 seconds when playing
    LaunchedEffect(controlsVisible, isPlaying) {
        if (controlsVisible && isPlaying) {
            delay(3200L)
            controlsVisible = false
        }
    }

    val containerShape = if (isFullscreen) RoundedCornerShape(0.dp) else RoundedCornerShape(24.dp)

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 16:9 Video Container with Subtle Performant Radial Vignette (alpha <= 0.25f, zero GPU blur shaders)
        Box(
            modifier = Modifier
                .then(
                    if (isFullscreen) Modifier.fillMaxSize()
                    else Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                )
                .drawBehind {
                    if (!isFullscreen && animatedVignetteColor.alpha > 0.01f) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    animatedVignetteColor.copy(alpha = 0.25f),
                                    animatedVignetteColor.copy(alpha = 0.10f),
                                    Color.Transparent
                                ),
                                center = Offset(size.width / 2f, size.height / 2f),
                                radius = size.width * 0.62f
                            ),
                            center = Offset(size.width / 2f, size.height / 2f),
                            radius = size.width * 0.62f
                        )
                    }
                }
                .clip(containerShape)
                .background(Color(0xFF090A0D))
                .border(
                    width = if (isFullscreen) 0.dp else 0.5.dp,
                    color = Color.White.copy(alpha = 0.08f),
                    shape = containerShape
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    if (stream != null) {
                        controlsVisible = !controlsVisible
                    } else if (!isLoadingStream) {
                        onQuickPlayDefault()
                    }
                }
                .testTag("video_player_stage"),
            contentAlignment = Alignment.Center
        ) {
            // Persistent PlayerView kept mounted inside the hierarchy to prevent surface detachment
            AndroidView(
                factory = { ctx ->
                    FrameLayout(ctx).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        (persistentPlayerView.parent as? ViewGroup)?.removeView(persistentPlayerView)
                        addView(persistentPlayerView)
                    }
                },
                update = { container ->
                    if (persistentPlayerView.parent !== container) {
                        (persistentPlayerView.parent as? ViewGroup)?.removeView(persistentPlayerView)
                        container.addView(persistentPlayerView)
                    }
                    persistentPlayerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    persistentPlayerView.visibility =
                        if (stream != null && !isAudioOnly) android.view.View.VISIBLE else android.view.View.INVISIBLE
                },
                modifier = Modifier.fillMaxSize()
            )

            if (stream == null) {
                // Standby or Loading State inside the 16:9 Stage
                if (isLoadingStream) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(34.dp)
                        )
                        Text(
                            text = "Fetching Stream via Piped API…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SmartDisplay,
                            contentDescription = "HyperTube",
                            tint = Color.White.copy(alpha = 0.72f),
                            modifier = Modifier.size(38.dp)
                        )
                        Text(
                            text = "Tap to play or select a stream from the dock",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.64f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                // Audio-Only Backdrop
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
                                .graphicsLayer { alpha = 0.28f }
                        )
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color.White.copy(alpha = 0.12f))
                                    .border(0.5.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(20.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.GraphicEq,
                                    contentDescription = "Audio Stream Active",
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Audio-Only Stream",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )
                            Text(
                                text = stream.uploaderName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.68f)
                            )
                        }
                    }
                }

                // Buffering Spinner
                androidx.compose.animation.AnimatedVisibility(
                    visible = isBuffering,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut(),
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.60f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                // Minimal HyperOS Transport Overlay
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
                                        Color.Black.copy(alpha = 0.54f),
                                        Color.Black.copy(alpha = 0.16f),
                                        Color.Black.copy(alpha = 0.60f)
                                    )
                                )
                            )
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        // Top-Right Quick Toggles (Ambient, Audio-Only, Active Quality/Speed Badge)
                        Row(
                            modifier = Modifier.align(Alignment.TopEnd),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            HyperOsControlPill(
                                label = if (isAmbientModeEnabled) "Vignette ON" else "Vignette OFF",
                                isActive = isAmbientModeEnabled,
                                onClick = onToggleAmbientMode,
                                testTag = "ambient_mode_toggle_pill"
                            )

                            HyperOsControlPill(
                                label = if (isAudioOnly) "Audio ON" else "Audio Only",
                                isActive = isAudioOnly,
                                onClick = onToggleAudioOnly,
                                testTag = "audio_only_toggle_pill"
                            )

                            Box(
                                modifier = Modifier
                                    .height(30.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(Color.White.copy(alpha = 0.16f))
                                    .padding(horizontal = 11.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "$selectedQualityLabel • ${String.format(java.util.Locale.US, "%.2fx", playbackSpeed).trimEnd('0').trimEnd('.')}x",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White
                                )
                            }
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
                                    modifier = Modifier.size(26.dp)
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
                                sizeDp = 60,
                                backgroundColor = Color.White.copy(alpha = 0.22f),
                                testTag = "player_play_pause_button"
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
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
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }

                        // Bottom Fullscreen Button inside overlay
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
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

                        if (isFullscreen) {
                            CustomCapsuleScrubber(
                                exoPlayer = exoPlayer,
                                fallbackDurationSeconds = stream.durationSeconds,
                                isStreamLoaded = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.BottomCenter)
                                    .padding(end = 56.dp)
                            )
                        }
                    }
                }
            }
        }

        // Custom Capsule Scrubber below the video viewport (Decoupled position ticker!)
        if (!isFullscreen) {
            CustomCapsuleScrubber(
                exoPlayer = exoPlayer,
                fallbackDurationSeconds = stream?.durationSeconds ?: 0L,
                isStreamLoaded = stream != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("player_scrubber_slider")
            )
        }
    }
}

/**
 * Isolated leaf composable that polls ExoPlayer position with a throttled 400ms ticker.
 * Because state is read strictly inside this leaf composable, position ticks never trigger
 * recompositions of the parent video stage or screen layout.
 *
 * Features:
 * - Elapsed duration badge on the left (`01:24`)
 * - Rounded pill progress track with an animated active thumb pill
 * - Remaining duration badge on the right (`-02:18`)
 */
@Composable
fun CustomCapsuleScrubber(
    exoPlayer: ExoPlayer,
    fallbackDurationSeconds: Long,
    isStreamLoaded: Boolean,
    modifier: Modifier = Modifier
) {
    var currentPosMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(fallbackDurationSeconds * 1000L) }
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(exoPlayer, isStreamLoaded, fallbackDurationSeconds) {
        if (!isStreamLoaded) {
            currentPosMs = 0L
            durationMs = 0L
            return@LaunchedEffect
        }
        while (isActive) {
            if (!isDragging) {
                currentPosMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            }
            val playerDur = exoPlayer.duration
            durationMs = if (playerDur != C.TIME_UNSET && playerDur > 0L) {
                playerDur
            } else {
                (fallbackDurationSeconds * 1000L).coerceAtLeast(0L)
            }
            delay(400L)
        }
    }

    val totalSec = (durationMs / 1000L).coerceAtLeast(fallbackDurationSeconds).coerceAtLeast(0L)
    val liveFraction = if (totalSec > 0L) {
        (currentPosMs.toFloat() / (totalSec * 1000f)).coerceIn(0f, 1f)
    } else {
        0f
    }
    val displayedFraction by animateFloatAsState(
        targetValue = if (isDragging) dragFraction else liveFraction,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "scrubber_fraction"
    )

    val activeTrackHeight by animateDpAsState(
        targetValue = if (isDragging) 10.dp else 7.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "scrubber_track_height"
    )

    val thumbWidth by animateDpAsState(
        targetValue = if (isDragging) 24.dp else 16.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "scrubber_thumb_width"
    )

    val thumbHeight by animateDpAsState(
        targetValue = if (isDragging) 14.dp else 11.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "scrubber_thumb_height"
    )

    val activeSec = if (isDragging && totalSec > 0L) {
        (dragFraction * totalSec).toLong()
    } else {
        (currentPosMs / 1000L).coerceAtLeast(0L)
    }
    val remainingSec = (totalSec - activeSec).coerceAtLeast(0L)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Elapsed Duration Capsule Badge
        Box(
            modifier = Modifier
                .widthIn(min = 52.dp)
                .height(24.dp)
                .clip(RoundedCornerShape(50))
                .background(Color(0xFF1A1D24))
                .border(0.5.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(50))
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = formatDuration(activeSec),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = Color.White.copy(alpha = 0.88f)
            )
        }

        // Rounded Pill Progress Track + Animated Active Thumb Pill
        Box(
            modifier = Modifier
                .weight(1f)
                .height(28.dp)
                .pointerInput(isStreamLoaded, totalSec) {
                    if (!isStreamLoaded || totalSec <= 0L) return@pointerInput
                    detectTapGestures { offset ->
                        val frac = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                        val targetMs = (frac * totalSec * 1000L).toLong()
                        currentPosMs = targetMs
                        exoPlayer.seekTo(targetMs)
                    }
                }
                .pointerInput(isStreamLoaded, totalSec) {
                    if (!isStreamLoaded || totalSec <= 0L) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            dragFraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                        },
                        onDragEnd = {
                            val targetMs = (dragFraction * totalSec * 1000L).toLong()
                            currentPosMs = targetMs
                            exoPlayer.seekTo(targetMs)
                            isDragging = false
                        },
                        onDragCancel = {
                            isDragging = false
                        },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            dragFraction = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
            ) {
                val trackH = activeTrackHeight.toPx()
                val trackTop = (size.height - trackH) / 2f
                val trackCorner = CornerRadius(trackH / 2f, trackH / 2f)

                // Background capsule track
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.16f),
                    topLeft = Offset(0f, trackTop),
                    size = Size(size.width, trackH),
                    cornerRadius = trackCorner
                )

                if (isStreamLoaded) {
                    val filledW = (size.width * displayedFraction).coerceIn(trackH, size.width)

                    // Active filled progress track
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.92f),
                        topLeft = Offset(0f, trackTop),
                        size = Size(filledW, trackH),
                        cornerRadius = trackCorner
                    )

                    // Animated Active Thumb Pill
                    val pillW = thumbWidth.toPx()
                    val pillH = thumbHeight.toPx()
                    val centerX = (size.width * displayedFraction).coerceIn(pillW / 2f, size.width - pillW / 2f)
                    val pillTop = (size.height - pillH) / 2f

                    // Subtle halo around active thumb pill
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = if (isDragging) 0.45f else 0.25f),
                                Color.Transparent
                            ),
                            center = Offset(centerX, size.height / 2f),
                            radius = pillW * 1.1f
                        ),
                        center = Offset(centerX, size.height / 2f),
                        radius = pillW * 1.1f
                    )

                    drawRoundRect(
                        color = Color.White,
                        topLeft = Offset(centerX - pillW / 2f, pillTop),
                        size = Size(pillW, pillH),
                        cornerRadius = CornerRadius(pillH / 2f, pillH / 2f)
                    )
                }
            }
        }

        // Remaining Duration Capsule Badge
        Box(
            modifier = Modifier
                .widthIn(min = 56.dp)
                .height(24.dp)
                .clip(RoundedCornerShape(50))
                .background(Color(0xFF1A1D24))
                .border(0.5.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(50))
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "-${formatDuration(remainingSec)}",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = Color.White.copy(alpha = 0.65f)
            )
        }
    }
}

@Composable
private fun SpringIconButton(
    onClick: () -> Unit,
    sizeDp: Int = 48,
    backgroundColor: Color = Color.Black.copy(alpha = 0.50f),
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
            .border(0.5.dp, Color.White.copy(alpha = 0.22f), CircleShape)
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
private fun HyperOsControlPill(
    label: String,
    isActive: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Box(
        modifier = Modifier
            .height(30.dp)
            .clip(RoundedCornerShape(50))
            .background(
                if (isActive) Color.White.copy(alpha = 0.24f)
                else Color.Black.copy(alpha = 0.52f)
            )
            .border(
                width = 0.5.dp,
                color = if (isActive) Color.White.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.16f),
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
            color = Color.White
        )
    }
}
