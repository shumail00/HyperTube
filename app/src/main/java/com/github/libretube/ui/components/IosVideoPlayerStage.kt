package com.github.libretube.ui.components

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.github.libretube.api.HyperStreamData
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
    startTimestampSeconds: Long,
    seekRequestSeconds: Long?,
    onSeekRequestConsumed: () -> Unit,
    onSelectQuality: (Int) -> Unit,
    onSelectSpeed: (Float) -> Unit,
    onToggleAudioOnly: () -> Unit,
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
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
    var controlsVisible by remember { mutableStateOf(true) }
    var lastLoadedVideoId by remember { mutableStateOf<String?>(null) }

    DisposableEffect(exoPlayer) {
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
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // Update playback speed smoothly
    LaunchedEffect(playbackSpeed) {
        exoPlayer.playbackParameters = PlaybackParameters(playbackSpeed)
    }

    // Handle external chapter seek requests
    LaunchedEffect(seekRequestSeconds) {
        if (seekRequestSeconds != null && seekRequestSeconds >= 0L) {
            exoPlayer.seekTo(seekRequestSeconds * 1000L)
            exoPlayer.playWhenReady = true
            onSeekRequestConsumed()
        }
    }

    // Load or switch media source while preserving current position on quality/audio toggle
    LaunchedEffect(stream?.videoId, dashDataUri, directStreamUrl, isAudioOnly) {
        if (stream == null) {
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            lastLoadedVideoId = null
            return@LaunchedEffect
        }

        val isSameVideo = lastLoadedVideoId == stream.videoId
        val resumePositionMs = if (isSameVideo) {
            exoPlayer.currentPosition.coerceAtLeast(0L)
        } else {
            startTimestampSeconds * 1000L
        }
        lastLoadedVideoId = stream.videoId

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:135.0) Gecko/20100101 Firefox/135.0")
            .setAllowCrossProtocolRedirects(true)

        when {
            !isAudioOnly && !dashDataUri.isNullOrBlank() -> {
                val mediaItem = MediaItem.Builder()
                    .setUri(dashDataUri.toUri())
                    .setMimeType(MimeTypes.APPLICATION_MPD)
                    .build()
                val dashSource = DashMediaSource.Factory(httpDataSourceFactory)
                    .createMediaSource(mediaItem)
                exoPlayer.setMediaSource(dashSource, resumePositionMs)
                exoPlayer.prepare()
                exoPlayer.playWhenReady = true
            }

            !directStreamUrl.isNullOrBlank() -> {
                val isHls = directStreamUrl.contains(".m3u8") || stream.isLive
                if (isHls) {
                    val mediaItem = MediaItem.Builder()
                        .setUri(directStreamUrl.toUri())
                        .setMimeType(MimeTypes.APPLICATION_M3U8)
                        .build()
                    val hlsSource = HlsMediaSource.Factory(httpDataSourceFactory)
                        .createMediaSource(mediaItem)
                    exoPlayer.setMediaSource(hlsSource)
                } else {
                    val videoSource = ProgressiveMediaSource.Factory(httpDataSourceFactory)
                        .createMediaSource(MediaItem.fromUri(directStreamUrl.toUri()))

                    // Check if progressive video is video-only and needs merged audio
                    val needsSeparateAudio = !isAudioOnly &&
                        stream.muxedVideoStreams.none { it.url == directStreamUrl } &&
                        !directAudioUrl.isNullOrBlank() &&
                        directAudioUrl != directStreamUrl

                    if (needsSeparateAudio && directAudioUrl != null) {
                        val audioSource = ProgressiveMediaSource.Factory(httpDataSourceFactory)
                            .createMediaSource(MediaItem.fromUri(directAudioUrl.toUri()))
                        exoPlayer.setMediaSource(
                            MergingMediaSource(videoSource, audioSource),
                            resumePositionMs
                        )
                    } else {
                        exoPlayer.setMediaSource(videoSource, resumePositionMs)
                    }
                }
                exoPlayer.prepare()
                exoPlayer.playWhenReady = true
            }
        }
    }

    // Poll playback position for fluid iOS scrubber bar
    LaunchedEffect(isPlaying, stream?.videoId) {
        while (stream != null) {
            currentPosMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            val d = exoPlayer.duration
            if (d != C.TIME_UNSET && d > 0) {
                durationMs = d
            }
            delay(250L)
        }
    }

    // Auto-hide controls after 4 seconds when playing
    LaunchedEffect(controlsVisible, isPlaying) {
        if (controlsVisible && isPlaying) {
            delay(4000L)
            controlsVisible = false
        }
    }

    val containerShape = if (isFullscreen) RoundedCornerShape(0.dp) else RoundedCornerShape(24.dp)

    Box(
        modifier = modifier
            .then(
                if (isFullscreen) Modifier.fillMaxSize()
                else Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
            )
            .clip(containerShape)
            .background(IosOledBlack)
            .border(
                width = if (isFullscreen) 0.dp else 1.dp,
                color = Color.White.copy(alpha = 0.12f),
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
                // iOS Music / Podcast style artwork backdrop when Audio Only is enabled
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
                                .size(76.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .background(IosElectricBlue.copy(alpha = 0.2f))
                                .border(1.dp, IosElectricBlue.copy(alpha = 0.5f), RoundedCornerShape(22.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.GraphicEq,
                                contentDescription = "Audio Stream Active",
                                tint = IosCyanTint,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Lossless Audio Stream Mode",
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
            } else {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            player = exoPlayer
                        }
                    },
                    update = { playerView ->
                        playerView.player = exoPlayer
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Buffering Spinner
            AnimatedVisibility(
                visible = isBuffering,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(IosSurfacePrimary.copy(alpha = 0.78f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = IosElectricBlue,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            // iOS Glassmorphic Controls Overlay
            AnimatedVisibility(
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
                                    Color.Black.copy(alpha = 0.68f),
                                    Color.Black.copy(alpha = 0.25f),
                                    Color.Black.copy(alpha = 0.78f)
                                )
                            )
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    // Top Bar: Stream Title + Quality / Speed / Audio Only Pills
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stream.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = IosLabelPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 12.dp)
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Audio Only Pill
                            IosControlPill(
                                label = if (isAudioOnly) "Audio ON" else "Audio",
                                isActive = isAudioOnly,
                                onClick = onToggleAudioOnly,
                                testTag = "audio_only_toggle_pill"
                            )

                            // Quality Picker Pill
                            QualitySelectorPill(
                                availableQualities = availableQualities,
                                selectedHeight = selectedQualityHeight,
                                onSelectQuality = onSelectQuality
                            )

                            // Playback Speed Pill
                            SpeedSelectorPill(
                                currentSpeed = playbackSpeed,
                                onSelectSpeed = onSelectSpeed
                            )
                        }
                    }

                    // Center Transport Buttons (Rewind 10s, Play/Pause, Forward 10s)
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
                                modifier = Modifier.size(30.dp)
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
                            sizeDp = 64,
                            backgroundColor = IosElectricBlue.copy(alpha = 0.9f),
                            testTag = "player_play_pause_button"
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
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
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }

                    // Bottom Scrubber & Fullscreen Bar
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                    ) {
                        val totalDurationSec = (durationMs / 1000L).coerceAtLeast(stream.durationSeconds)
                        val currentSec = (currentPosMs / 1000L).coerceAtLeast(0L)
                        val sliderFraction = if (totalDurationSec > 0) {
                            (currentSec.toFloat() / totalDurationSec.toFloat()).coerceIn(0f, 1f)
                        } else 0f

                        Slider(
                            value = sliderFraction,
                            onValueChange = { fraction ->
                                if (totalDurationSec > 0) {
                                    val targetMs = (fraction * totalDurationSec * 1000L).toLong()
                                    currentPosMs = targetMs
                                    exoPlayer.seekTo(targetMs)
                                }
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = IosElectricBlue,
                                inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(26.dp)
                                .testTag("player_scrubber_slider")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "${formatDuration(currentSec)} / ${formatDuration(totalDurationSec)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = IosLabelPrimary
                                )
                                if (selectedQualityHeight > 0 && !isAudioOnly) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(IosMintGreen.copy(alpha = 0.2f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "${selectedQualityHeight}p HD",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = IosMintGreen
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
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpringIconButton(
    onClick: () -> Unit,
    sizeDp: Int = 48,
    backgroundColor: Color = IosSurfacePrimary.copy(alpha = 0.72f),
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
            .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
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
            .height(34.dp)
            .clip(RoundedCornerShape(50))
            .background(
                if (isActive) IosElectricBlue else IosSurfaceSecondary.copy(alpha = 0.85f)
            )
            .border(
                width = 1.dp,
                color = if (isActive) IosCyanTint.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.15f),
                shape = RoundedCornerShape(50)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
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
                .height(34.dp)
                .clip(RoundedCornerShape(50))
                .background(IosSurfaceSecondary.copy(alpha = 0.85f))
                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(50))
                .clickable { expanded = true }
                .padding(horizontal = 12.dp)
                .testTag("quality_selector_pill"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.HighQuality,
                contentDescription = "Video Quality",
                tint = IosCyanTint,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = if (selectedHeight > 0) "${selectedHeight}p" else "Auto",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(IosSurfacePrimary)
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
                            text = "${q}p ${if (q >= 1080) "Full HD" else if (q >= 720) "HD" else ""}".trim(),
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
                .height(34.dp)
                .clip(RoundedCornerShape(50))
                .background(IosSurfaceSecondary.copy(alpha = 0.85f))
                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(50))
                .clickable { expanded = true }
                .padding(horizontal = 12.dp)
                .testTag("speed_selector_pill"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Speed,
                contentDescription = "Playback Speed",
                tint = IosCyanTint,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "${currentSpeed}x",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(IosSurfacePrimary)
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
