package com.github.libretube.ui.screens

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.github.libretube.api.ChannelPageData
import com.github.libretube.api.ChapterInfo
import com.github.libretube.api.HyperStreamData
import com.github.libretube.api.QualityStepOption
import com.github.libretube.api.RelatedStreamInfo
import com.github.libretube.api.SearchChannelItem
import com.github.libretube.ui.components.IosVideoPlayerStage
import com.github.libretube.ui.models.HyperTubeUiState
import com.github.libretube.ui.models.PLAYBACK_SPEED_STEPS
import com.github.libretube.ui.models.RecentStreamEntry
import com.github.libretube.ui.models.SideBoxMode
import com.github.libretube.ui.models.formatCompactCount
import com.github.libretube.ui.models.formatDuration
import com.github.libretube.ui.models.sanitizeHtmlDescription
import java.util.Locale
import kotlin.math.roundToInt

// HyperOS Tablet Design Tokens
private val HyperSlateCanvas = Color(0xFF0F1115)
private val HyperMetadataPillSurface = Color(0xFF1A1D24)
private val HyperRightDockSurface = Color(0xFF16191F)
private val HyperCardElevated = Color(0xFF20242D)
private val HyperAcrylicBorder = Color.White.copy(alpha = 0.06f)
private val HyperAccentBlue = Color(0xFF4F8CFF)
private val HyperErrorCoral = Color(0xFFFF5A79)

@Composable
fun HyperTubeScreen(
    uiState: HyperTubeUiState,
    onLinkInputChange: (String) -> Unit,
    onPlayClicked: (String?) -> Unit,
    onSelectVideoId: (String) -> Unit,
    onOpenChannel: (String, String) -> Unit,
    onClearChannelExplorer: () -> Unit,
    onSelectSideBoxMode: (SideBoxMode) -> Unit,
    onSelectQualityStepIndex: (Int) -> Unit,
    onSelectSpeedStepIndex: (Int) -> Unit,
    onToggleAudioOnly: () -> Unit,
    onToggleAmbientMode: () -> Unit,
    onRemoveHistoryItem: (String) -> Unit,
    onClearHistory: () -> Unit,
    onToggleAbout: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var isFullscreen by remember { mutableStateOf(false) }
    var seekRequestSeconds by remember { mutableStateOf<Long?>(null) }

    if (isFullscreen) {
        BackHandler {
            isFullscreen = false
        }
    }

    if (uiState.showAboutDialog) {
        BackHandler {
            onToggleAbout(false)
        }
    }

    if (uiState.currentChannelPage != null && uiState.sideBoxMode == SideBoxMode.SEARCH) {
        BackHandler {
            onClearChannelExplorer()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HyperSlateCanvas)
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        if (isFullscreen && uiState.currentStream != null) {
            IosVideoPlayerStage(
                stream = uiState.currentStream,
                isLoadingStream = uiState.isLoading,
                dashDataUri = uiState.dashDataUri,
                directStreamUrl = uiState.directStreamUrl,
                directAudioUrl = uiState.directAudioUrl,
                selectedQualityHeight = uiState.selectedQualityHeight,
                selectedQualityLabel = uiState.selectedQualityLabel,
                playbackSpeed = uiState.playbackSpeed,
                isAudioOnly = uiState.isAudioOnly,
                isAmbientModeEnabled = uiState.isAmbientModeEnabled,
                onToggleAmbientMode = onToggleAmbientMode,
                startTimestampSeconds = uiState.startTimestampSeconds,
                seekRequestSeconds = seekRequestSeconds,
                onSeekRequestConsumed = { seekRequestSeconds = null },
                onToggleAudioOnly = onToggleAudioOnly,
                isFullscreen = true,
                onToggleFullscreen = { isFullscreen = false },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Fixed Landscape-Optimized Multi-Pane 16:10 Tablet Shell (Left 65% / Right 35%)
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // LEFT PANE (~65% width): Video Stage + Custom Capsule Scrubber + Metadata & Discrete Sliders Pill
                Column(
                    modifier = Modifier
                        .weight(0.65f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    IosVideoPlayerStage(
                        stream = uiState.currentStream,
                        isLoadingStream = uiState.isLoading,
                        dashDataUri = uiState.dashDataUri,
                        directStreamUrl = uiState.directStreamUrl,
                        directAudioUrl = uiState.directAudioUrl,
                        selectedQualityHeight = uiState.selectedQualityHeight,
                        selectedQualityLabel = uiState.selectedQualityLabel,
                        playbackSpeed = uiState.playbackSpeed,
                        isAudioOnly = uiState.isAudioOnly,
                        isAmbientModeEnabled = uiState.isAmbientModeEnabled,
                        onToggleAmbientMode = onToggleAmbientMode,
                        startTimestampSeconds = uiState.startTimestampSeconds,
                        seekRequestSeconds = seekRequestSeconds,
                        onSeekRequestConsumed = { seekRequestSeconds = null },
                        onToggleAudioOnly = onToggleAudioOnly,
                        isFullscreen = false,
                        onToggleFullscreen = { isFullscreen = true },
                        onQuickPlayDefault = {
                            if (uiState.recentStreams.isNotEmpty()) {
                                onSelectVideoId(uiState.recentStreams.first().videoId)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Metadata, Sanitized Description & Discrete Step Sliders Pill
                    HyperMetadataAndControlsPill(
                        stream = uiState.currentStream,
                        sanitizedDescription = uiState.sanitizedDescription,
                        qualitySteps = uiState.qualitySteps,
                        selectedQualityIndex = uiState.selectedQualityIndex,
                        selectedQualityLabel = uiState.selectedQualityLabel,
                        playbackSpeed = uiState.playbackSpeed,
                        onSelectQualityStepIndex = onSelectQualityStepIndex,
                        onSelectSpeedStepIndex = onSelectSpeedStepIndex,
                        onOpenChannel = {
                            val s = uiState.currentStream
                            if (s != null) {
                                onOpenChannel(s.uploaderUrl.ifBlank { s.uploaderName }, s.uploaderName)
                            }
                        },
                        onSeekToSeconds = { sec -> seekRequestSeconds = sec },
                        onShareLink = {
                            uiState.currentStream?.let { shareStreamLink(context, it) }
                        },
                        onOpenAbout = { onToggleAbout(true) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                }

                // RIGHT PANE (~35% width): Elevated Capsule Dock (Search Bar + Segmented Switches + LazyColumn)
                HyperRightNavigationDock(
                    uiState = uiState,
                    onLinkInputChange = onLinkInputChange,
                    onSubmitQueryOrLink = {
                        focusManager.clearFocus()
                        onPlayClicked(null)
                    },
                    onPasteClipboard = {
                        val clip = readClipboardText(context)
                        if (!clip.isNullOrBlank()) {
                            onLinkInputChange(clip)
                            focusManager.clearFocus()
                            onPlayClicked(clip)
                        }
                    },
                    onSelectMode = onSelectSideBoxMode,
                    onSelectVideo = onSelectVideoId,
                    onSelectChannel = onOpenChannel,
                    onClearChannelExplorer = onClearChannelExplorer,
                    onRemoveRecent = onRemoveHistoryItem,
                    onClearHistory = onClearHistory,
                    modifier = Modifier
                        .weight(0.35f)
                        .fillMaxHeight()
                )
            }
        }

        if (uiState.showAboutDialog) {
            DeveloperAboutModal(onDismiss = { onToggleAbout(false) })
        }
    }
}

/**
 * Low-contrast acrylic Metadata & Description Pill (`Color(0xFF1A1D24)`, border `0.5.dp` solid
 * `Color.White.copy(alpha = 0.06f)`, `RoundedCornerShape(20.dp)`):
 * - Clean video title, channel badge, subscriber count, and metrics
 * - Discrete Step Sliders for Video Quality (`144p`..`1080p60`/`4K`) and Playback Speed (`0.5x`..`2.0x`)
 * - HTML-sanitized description via `HtmlCompat.fromHtml(..., FROM_HTML_MODE_COMPACT).toString().trim()`
 */
@Composable
private fun HyperMetadataAndControlsPill(
    stream: HyperStreamData?,
    sanitizedDescription: String,
    qualitySteps: List<QualityStepOption>,
    selectedQualityIndex: Int,
    selectedQualityLabel: String,
    playbackSpeed: Float,
    onSelectQualityStepIndex: (Int) -> Unit,
    onSelectSpeedStepIndex: (Int) -> Unit,
    onOpenChannel: () -> Unit,
    onSeekToSeconds: (Long) -> Unit,
    onShareLink: () -> Unit,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(HyperMetadataPillSurface)
            .border(0.5.dp, HyperAcrylicBorder, RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("video_details_bottom_box")
    ) {
        if (stream != null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Clean Video Title + Action Buttons Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = stream.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 20.sp
                        ),
                        color = Color.White.copy(alpha = 0.95f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 10.dp)
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (stream.views > 0) {
                            HyperMetricBadge(
                                icon = Icons.Rounded.Visibility,
                                text = formatCompactCount(stream.views)
                            )
                        }
                        if (stream.likes > 0) {
                            HyperMetricBadge(
                                icon = Icons.Rounded.ThumbUp,
                                text = formatCompactCount(stream.likes)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(HyperCardElevated)
                                .clickable(onClick = onShareLink)
                                .testTag("share_stream_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Share,
                                contentDescription = "Share Video Link",
                                tint = Color.White.copy(alpha = 0.88f),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(HyperCardElevated)
                                .clickable(onClick = onOpenAbout)
                                .testTag("developer_badge_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Info,
                                contentDescription = "About HyperTube",
                                tint = Color.White.copy(alpha = 0.72f),
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }

                // Channel Badge + Subscriber Count Row
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(HyperCardElevated.copy(alpha = 0.75f))
                        .border(0.5.dp, HyperAcrylicBorder, RoundedCornerShape(14.dp))
                        .clickable(onClick = onOpenChannel)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("explore_channel_button"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AsyncImage(
                        model = stream.uploaderAvatar,
                        contentDescription = stream.uploaderName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f))
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = stream.uploaderName,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = Color.White.copy(alpha = 0.94f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (stream.uploaderVerified) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = "Verified Channel",
                                tint = HyperAccentBlue,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                        if (stream.uploaderSubscribers > 0) {
                            Text(
                                text = "• ${formatCompactCount(stream.uploaderSubscribers)} subs",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.62f),
                                maxLines = 1
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForwardIos,
                            contentDescription = "Explore Channel",
                            tint = Color.White.copy(alpha = 0.45f),
                            modifier = Modifier.size(10.dp)
                        )
                    }
                }

                // Discrete Step Sliders Row: Video Quality Picker + Playback Speed Slider
                DiscreteQualityAndSpeedSliders(
                    qualitySteps = qualitySteps,
                    selectedQualityIndex = selectedQualityIndex,
                    selectedQualityLabel = selectedQualityLabel,
                    playbackSpeed = playbackSpeed,
                    onSelectQualityStepIndex = onSelectQualityStepIndex,
                    onSelectSpeedStepIndex = onSelectSpeedStepIndex
                )

                // Chapters Row (if available)
                if (stream.chapters.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(stream.chapters) { chapter ->
                            HyperChapterChip(
                                chapter = chapter,
                                onClick = { onSeekToSeconds(chapter.startSeconds) }
                            )
                        }
                    }
                }

                // HTML-Sanitized Description
                val datePrefix = if (stream.uploadDateText.isNotBlank()) "${stream.uploadDateText}  •  " else ""
                val displayDescription = sanitizedDescription.ifBlank {
                    sanitizeHtmlDescription(stream.description)
                }.ifBlank { "No description provided." }

                Text(
                    text = datePrefix + displayDescription,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp
                    ),
                    color = Color.White.copy(alpha = 0.68f)
                )
            }
        } else {
            // Standby Metadata & Discrete Sliders Preview
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            text = "HyperTube Tablet Stage",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = Color.White.copy(alpha = 0.92f)
                        )
                        Text(
                            text = "Select a track from Playlist, Queue, or Search in the right dock to begin.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.60f)
                        )
                    }

                    Row(
                        modifier = Modifier
                            .height(30.dp)
                            .clip(RoundedCornerShape(50))
                            .background(HyperCardElevated)
                            .border(0.5.dp, HyperAcrylicBorder, RoundedCornerShape(50))
                            .clickable(onClick = onOpenAbout)
                            .padding(horizontal = 12.dp)
                            .testTag("developer_badge_button"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = "Developer",
                            tint = HyperAccentBlue,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "shumail0ahmed",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.90f)
                        )
                    }
                }

                DiscreteQualityAndSpeedSliders(
                    qualitySteps = qualitySteps,
                    selectedQualityIndex = selectedQualityIndex,
                    selectedQualityLabel = selectedQualityLabel,
                    playbackSpeed = playbackSpeed,
                    onSelectQualityStepIndex = onSelectQualityStepIndex,
                    onSelectSpeedStepIndex = onSelectSpeedStepIndex
                )
            }
        }
    }
}

/**
 * Discrete Step Sliders for Video Quality (`steps = (availableStreams.size - 2).coerceAtLeast(0)`,
 * `0` = lowest `144p` to `max` = highest `1080p60` / `4K`) and Playback Speed (`0.5x`..`2.0x`).
 */
@Composable
private fun DiscreteQualityAndSpeedSliders(
    qualitySteps: List<QualityStepOption>,
    selectedQualityIndex: Int,
    selectedQualityLabel: String,
    playbackSpeed: Float,
    onSelectQualityStepIndex: (Int) -> Unit,
    onSelectSpeedStepIndex: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Video Quality Discrete Step Slider
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(HyperCardElevated.copy(alpha = 0.72f))
                .border(0.5.dp, HyperAcrylicBorder, RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.HighQuality,
                        contentDescription = "Quality",
                        tint = HyperAccentBlue,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "Quality",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.75f)
                    )
                }

                Text(
                    text = if (qualitySteps.isNotEmpty()) {
                        qualitySteps.getOrNull(selectedQualityIndex)?.label ?: selectedQualityLabel
                    } else {
                        selectedQualityLabel
                    },
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White,
                    modifier = Modifier.testTag("active_quality_label")
                )
            }

            val maxQualityIdx = (qualitySteps.size - 1).coerceAtLeast(1)
            val qualitySliderSteps = (qualitySteps.size - 2).coerceAtLeast(0)
            val safeSelectedIdx = selectedQualityIndex.coerceIn(0, qualitySteps.lastIndex.coerceAtLeast(0))

            Slider(
                value = safeSelectedIdx.toFloat(),
                onValueChange = { rawValue ->
                    if (qualitySteps.size > 1) {
                        onSelectQualityStepIndex(rawValue.roundToInt().coerceIn(0, qualitySteps.lastIndex))
                    }
                },
                enabled = qualitySteps.size > 1,
                valueRange = 0f..maxQualityIdx.toFloat(),
                steps = qualitySliderSteps,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = HyperAccentBlue,
                    inactiveTrackColor = Color.White.copy(alpha = 0.16f),
                    activeTickColor = Color.White.copy(alpha = 0.7f),
                    inactiveTickColor = Color.White.copy(alpha = 0.35f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(26.dp)
                    .testTag("discrete_quality_slider")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = qualitySteps.firstOrNull()?.label ?: "144p",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = Color.White.copy(alpha = 0.48f)
                )
                Text(
                    text = qualitySteps.lastOrNull()?.label ?: "1080p60",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = Color.White.copy(alpha = 0.48f)
                )
            }
        }

        // 2. Playback Speed Discrete Snap Slider (0.5x, 0.75x, 1.0x, 1.25x, 1.5x, 2.0x)
        val currentSpeedIdx = PLAYBACK_SPEED_STEPS.indexOfFirst { it == playbackSpeed }
            .let { if (it >= 0) it else 2 }
        val speedMaxIdx = PLAYBACK_SPEED_STEPS.lastIndex
        val speedSliderSteps = (PLAYBACK_SPEED_STEPS.size - 2).coerceAtLeast(0)

        Column(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(HyperCardElevated.copy(alpha = 0.72f))
                .border(0.5.dp, HyperAcrylicBorder, RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Speed,
                        contentDescription = "Playback Speed",
                        tint = HyperAccentBlue,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "Speed",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.75f)
                    )
                }

                Text(
                    text = formatSpeedLabel(playbackSpeed),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White,
                    modifier = Modifier.testTag("active_speed_label")
                )
            }

            Slider(
                value = currentSpeedIdx.toFloat(),
                onValueChange = { rawValue ->
                    onSelectSpeedStepIndex(rawValue.roundToInt().coerceIn(0, speedMaxIdx))
                },
                valueRange = 0f..speedMaxIdx.toFloat(),
                steps = speedSliderSteps,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = HyperAccentBlue,
                    inactiveTrackColor = Color.White.copy(alpha = 0.16f),
                    activeTickColor = Color.White.copy(alpha = 0.7f),
                    inactiveTickColor = Color.White.copy(alpha = 0.35f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(26.dp)
                    .testTag("discrete_speed_slider")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "0.5x",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = Color.White.copy(alpha = 0.48f)
                )
                Text(
                    text = "1.0x",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = Color.White.copy(alpha = 0.48f)
                )
                Text(
                    text = "2.0x",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = Color.White.copy(alpha = 0.48f)
                )
            }
        }
    }
}

private fun formatSpeedLabel(speed: Float): String {
    val formatted = String.format(Locale.US, "%.2f", speed).trimEnd('0').trimEnd('.')
    return "${formatted}x"
}

/**
 * Right Pane (Queue & Navigation Dock):
 * - Container: Elevated capsule surface (`Color(0xFF16191F)`, `RoundedCornerShape(24.dp)`)
 * - Padding: `16.dp` interior padding all around
 * - Top Dock: Minimal search bar with clear button and query action + Segmented pill switches (`Playlist`, `Queue`, `Search`) with smooth animated indicator
 * - List View: Minimalist `LazyColumn` for video items with rounded `14.dp` corners and soft pressed feedback
 */
@Composable
private fun HyperRightNavigationDock(
    uiState: HyperTubeUiState,
    onLinkInputChange: (String) -> Unit,
    onSubmitQueryOrLink: () -> Unit,
    onPasteClipboard: () -> Unit,
    onSelectMode: (SideBoxMode) -> Unit,
    onSelectVideo: (String) -> Unit,
    onSelectChannel: (String, String) -> Unit,
    onClearChannelExplorer: () -> Unit,
    onRemoveRecent: (String) -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(HyperRightDockSurface)
            .border(0.5.dp, HyperAcrylicBorder, RoundedCornerShape(24.dp))
            .padding(16.dp)
            .testTag("multi_purpose_side_box"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Minimal Search / Link Bar with Clear Button & Query Action
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(HyperCardElevated)
                .border(0.5.dp, HyperAcrylicBorder, RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = "Search or Play Link",
                tint = Color.White.copy(alpha = 0.65f),
                modifier = Modifier
                    .size(18.dp)
                    .clickable { onSubmitQueryOrLink() }
            )

            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                if (uiState.linkInput.isEmpty()) {
                    Text(
                        text = "Search videos or paste YouTube link…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.38f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BasicTextField(
                    value = uiState.linkInput,
                    onValueChange = onLinkInputChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                    cursorBrush = SolidColor(HyperAccentBlue),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = { onSubmitQueryOrLink() },
                        onGo = { onSubmitQueryOrLink() },
                        onDone = { onSubmitQueryOrLink() }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("youtube_link_input")
                )
            }

            if (uiState.linkInput.isNotEmpty()) {
                IconButton(
                    onClick = { onLinkInputChange("") },
                    modifier = Modifier
                        .size(28.dp)
                        .testTag("clear_link_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Clear Query",
                        tint = Color.White.copy(alpha = 0.65f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            IconButton(
                onClick = onPasteClipboard,
                modifier = Modifier
                    .size(28.dp)
                    .testTag("paste_clipboard_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.ContentPaste,
                    contentDescription = "Paste Clipboard",
                    tint = Color.White.copy(alpha = 0.65f),
                    modifier = Modifier.size(15.dp)
                )
            }
        }

        // 2. Segmented Pill Switches (Playlist, Queue, Search) with Smooth Animated Indicator Selection
        HyperSegmentedPillSwitch(
            selectedMode = uiState.sideBoxMode,
            onSelectMode = onSelectMode
        )

        // Error Banner (if any)
        AnimatedVisibility(visible = !uiState.errorMessage.isNullOrBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(HyperErrorCoral.copy(alpha = 0.14f))
                    .border(0.5.dp, HyperErrorCoral.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.ErrorOutline,
                    contentDescription = null,
                    tint = HyperErrorCoral,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = uiState.errorMessage.orEmpty(),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFFFFB8C6),
                    maxLines = 2
                )
            }
        }

        // 3. Minimalist LazyColumn Content Area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (uiState.isSideBoxLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.2.dp,
                        modifier = Modifier.size(26.dp)
                    )
                }
            } else {
                when (uiState.sideBoxMode) {
                    SideBoxMode.PLAYLIST -> {
                        PlaylistDockList(
                            recentStreams = uiState.recentStreams,
                            activeVideoId = uiState.currentStream?.videoId,
                            onSelectVideo = onSelectVideo,
                            onRemoveRecent = onRemoveRecent,
                            onClearHistory = onClearHistory
                        )
                    }

                    SideBoxMode.QUEUE -> {
                        QueueDockList(
                            relatedStreams = uiState.currentStream?.relatedStreams.orEmpty(),
                            recentStreams = uiState.recentStreams,
                            activeVideoId = uiState.currentStream?.videoId,
                            onSelectVideo = onSelectVideo
                        )
                    }

                    SideBoxMode.SEARCH -> {
                        if (uiState.currentChannelPage != null) {
                            ChannelExplorerList(
                                channelPage = uiState.currentChannelPage,
                                activeVideoId = uiState.currentStream?.videoId,
                                onBackToSearch = onClearChannelExplorer,
                                onSelectVideo = onSelectVideo
                            )
                        } else {
                            SearchDockList(
                                searchResults = uiState.searchResults,
                                activeVideoId = uiState.currentStream?.videoId,
                                onSelectVideo = onSelectVideo,
                                onSelectChannel = onSelectChannel
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HyperSegmentedPillSwitch(
    selectedMode: SideBoxMode,
    onSelectMode: (SideBoxMode) -> Unit
) {
    val modes = SideBoxMode.entries
    val selectedIdx = modes.indexOf(selectedMode).coerceAtLeast(0)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(HyperCardElevated.copy(alpha = 0.85f))
            .border(0.5.dp, HyperAcrylicBorder, RoundedCornerShape(12.dp))
            .padding(3.dp)
    ) {
        val segmentWidth = maxWidth / modes.size
        val indicatorOffset by animateDpAsState(
            targetValue = segmentWidth * selectedIdx,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium
            ),
            label = "segmented_pill_offset"
        )

        // Smooth Animated Pill Indicator
        Box(
            modifier = Modifier
                .offset(x = indicatorOffset)
                .width(segmentWidth)
                .fillMaxHeight()
                .clip(RoundedCornerShape(9.dp))
                .background(Color.White.copy(alpha = 0.14f))
                .border(0.5.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(9.dp))
        )

        Row(modifier = Modifier.fillMaxSize()) {
            modes.forEach { mode ->
                val isSelected = mode == selectedMode
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) Color.White else Color.White.copy(alpha = 0.56f),
                    label = "segment_text_color"
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(9.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onSelectMode(mode) }
                        .testTag("side_box_tab_${mode.name.lowercase()}"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = mode.headerTitle,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        color = textColor
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistDockList(
    recentStreams: List<RecentStreamEntry>,
    activeVideoId: String?,
    onSelectVideo: (String) -> Unit,
    onRemoveRecent: (String) -> Unit,
    onClearHistory: () -> Unit
) {
    if (recentStreams.isEmpty()) {
        DockEmptyPlaceholder("Your saved playlist & recent streams will appear here.")
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 8.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Saved & Recent (${recentStreams.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.55f)
                )
                Text(
                    text = "Clear",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.55f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onClearHistory() }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                        .testTag("clear_history_button")
                )
            }
        }

        items(recentStreams, key = { "playlist_${it.videoId}" }) { item ->
            HyperDockVideoCard(
                title = item.title,
                subtitle = item.uploaderName,
                thumbnailUrl = item.thumbnailUrl,
                durationSeconds = item.durationSeconds,
                isPlayingNow = item.videoId == activeVideoId,
                onClick = { onSelectVideo(item.videoId) },
                onRemove = { onRemoveRecent(item.videoId) },
                testTag = "history_card_${item.videoId}"
            )
        }
    }
}

@Composable
private fun QueueDockList(
    relatedStreams: List<RelatedStreamInfo>,
    recentStreams: List<RecentStreamEntry>,
    activeVideoId: String?,
    onSelectVideo: (String) -> Unit
) {
    if (relatedStreams.isEmpty() && recentStreams.isEmpty()) {
        DockEmptyPlaceholder("Play any video to populate the Up Next queue.")
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 8.dp)
    ) {
        if (relatedStreams.isNotEmpty()) {
            item {
                Text(
                    text = "Up Next (${relatedStreams.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.55f),
                    modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp)
                )
            }
            items(relatedStreams, key = { "queue_rel_${it.videoId}" }) { rel ->
                val meta = buildString {
                    append(rel.uploaderName)
                    if (rel.views > 0) append(" • ${formatCompactCount(rel.views)} views")
                }
                HyperDockVideoCard(
                    title = rel.title,
                    subtitle = meta,
                    thumbnailUrl = rel.thumbnailUrl,
                    durationSeconds = rel.durationSeconds,
                    isPlayingNow = rel.videoId == activeVideoId,
                    onClick = { onSelectVideo(rel.videoId) },
                    testTag = "related_card_${rel.videoId}"
                )
            }
        } else {
            items(recentStreams, key = { "queue_fallback_${it.videoId}" }) { item ->
                HyperDockVideoCard(
                    title = item.title,
                    subtitle = item.uploaderName,
                    thumbnailUrl = item.thumbnailUrl,
                    durationSeconds = item.durationSeconds,
                    isPlayingNow = item.videoId == activeVideoId,
                    onClick = { onSelectVideo(item.videoId) },
                    testTag = "queue_card_${item.videoId}"
                )
            }
        }
    }
}

@Composable
private fun SearchDockList(
    searchResults: com.github.libretube.api.SearchResultsPage?,
    activeVideoId: String?,
    onSelectVideo: (String) -> Unit,
    onSelectChannel: (String, String) -> Unit
) {
    if (searchResults == null) {
        DockEmptyPlaceholder("Type a search query above to find videos and channels via Piped.")
        return
    }

    if (searchResults.channels.isEmpty() && searchResults.videos.isEmpty()) {
        DockEmptyPlaceholder("No results found for \"${searchResults.query}\".")
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 8.dp)
    ) {
        if (searchResults.channels.isNotEmpty()) {
            items(searchResults.channels.take(3), key = { "ch_${it.channelId}" }) { channel ->
                HyperChannelRowCard(
                    channel = channel,
                    onClick = { onSelectChannel(channel.channelUrl.ifBlank { channel.channelId }, channel.name) }
                )
            }
        }

        items(searchResults.videos, key = { "sr_${it.videoId}" }) { video ->
            val subtitle = buildString {
                append(video.uploaderName)
                if (video.views > 0) append(" • ${formatCompactCount(video.views)} views")
            }
            HyperDockVideoCard(
                title = video.title,
                subtitle = subtitle,
                thumbnailUrl = video.thumbnailUrl,
                durationSeconds = video.durationSeconds,
                isPlayingNow = video.videoId == activeVideoId,
                onClick = { onSelectVideo(video.videoId) },
                testTag = "search_video_${video.videoId}"
            )
        }
    }
}

@Composable
private fun ChannelExplorerList(
    channelPage: ChannelPageData,
    activeVideoId: String?,
    onBackToSearch: () -> Unit,
    onSelectVideo: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 8.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(HyperCardElevated)
                    .border(0.5.dp, HyperAcrylicBorder, RoundedCornerShape(14.dp))
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                        .clickable(onClick = onBackToSearch),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back to Search",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }

                AsyncImage(
                    model = channelPage.avatarUrl,
                    contentDescription = channelPage.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                )

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = channelPage.name,
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (channelPage.verified) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = "Verified",
                                tint = HyperAccentBlue,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                    if (channelPage.subscriberCount > 0) {
                        Text(
                            text = "${formatCompactCount(channelPage.subscriberCount)} subscribers",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.62f)
                        )
                    }
                }
            }
        }

        items(channelPage.videos, key = { "ch_vid_${it.videoId}" }) { video ->
            val subtitle = buildString {
                if (video.views > 0) append("${formatCompactCount(video.views)} views")
                if (video.uploadedDate.isNotBlank()) {
                    if (isNotEmpty()) append(" • ")
                    append(video.uploadedDate)
                }
            }.ifBlank { channelPage.name }

            HyperDockVideoCard(
                title = video.title,
                subtitle = subtitle,
                thumbnailUrl = video.thumbnailUrl,
                durationSeconds = video.durationSeconds,
                isPlayingNow = video.videoId == activeVideoId,
                onClick = { onSelectVideo(video.videoId) },
                testTag = "channel_video_${video.videoId}"
            )
        }
    }
}

/**
 * Minimalist horizontal thumbnail + metadata row card with rounded `14.dp` corners,
 * soft pressed feedback, and zero clutter.
 */
@Composable
private fun HyperDockVideoCard(
    title: String,
    subtitle: String,
    thumbnailUrl: String,
    durationSeconds: Long,
    isPlayingNow: Boolean,
    onClick: () -> Unit,
    onRemove: (() -> Unit)? = null,
    testTag: String
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "dock_card_scale"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(14.dp))
            .background(
                when {
                    isPlayingNow -> Color.White.copy(alpha = 0.13f)
                    isPressed -> HyperCardElevated.copy(alpha = 0.95f)
                    else -> HyperCardElevated.copy(alpha = 0.62f)
                }
            )
            .border(
                width = 0.5.dp,
                color = if (isPlayingNow) HyperAccentBlue.copy(alpha = 0.55f) else HyperAcrylicBorder,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(8.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .width(102.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Black)
        ) {
            AsyncImage(
                model = thumbnailUrl,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.76f))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Text(
                    text = formatDuration(durationSeconds),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = Color.White
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 12.5.sp,
                    lineHeight = 16.sp,
                    fontWeight = if (isPlayingNow) FontWeight.SemiBold else FontWeight.Medium
                ),
                color = Color.White.copy(alpha = 0.94f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.56f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (onRemove != null) {
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(26.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = "Remove Item",
                    tint = Color.White.copy(alpha = 0.42f),
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}

@Composable
private fun HyperChannelRowCard(
    channel: SearchChannelItem,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(HyperCardElevated.copy(alpha = 0.72f))
            .border(0.5.dp, HyperAcrylicBorder, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .testTag("search_channel_${channel.channelId}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        AsyncImage(
            model = channel.avatarUrl,
            contentDescription = channel.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f))
        )

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = channel.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (channel.verified) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = "Verified",
                        tint = HyperAccentBlue,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
            if (channel.subscriberCount > 0) {
                Text(
                    text = "${formatCompactCount(channel.subscriberCount)} subscribers",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.58f)
                )
            }
        }

        Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowForwardIos,
            contentDescription = "Open Channel",
            tint = Color.White.copy(alpha = 0.45f),
            modifier = Modifier.size(12.dp)
        )
    }
}

@Composable
private fun HyperMetricBadge(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String
) {
    Row(
        modifier = Modifier
            .height(28.dp)
            .clip(RoundedCornerShape(50))
            .background(HyperCardElevated)
            .border(0.5.dp, HyperAcrylicBorder, RoundedCornerShape(50))
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.70f),
            modifier = Modifier.size(13.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.88f)
        )
    }
}

@Composable
private fun HyperChapterChip(
    chapter: ChapterInfo,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .height(26.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(HyperCardElevated)
            .border(0.5.dp, HyperAcrylicBorder, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = formatDuration(chapter.startSeconds),
            style = MaterialTheme.typography.labelSmall,
            color = HyperAccentBlue
        )
        Text(
            text = chapter.title,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.88f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DockEmptyPlaceholder(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.50f)
        )
    }
}

@Composable
private fun DeveloperAboutModal(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(HyperMetadataPillSurface)
                .border(0.5.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(24.dp))
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "HyperTube",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White
            )
            Text(
                text = "Crafted by shumail0ahmed",
                style = MaterialTheme.typography.bodyMedium,
                color = HyperAccentBlue
            )
            Text(
                text = "Lean 16:10 HyperOS tablet YouTube player powered strictly by the Piped REST API and Media3 ExoPlayer.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.72f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.14f))
                    .clickable(onClick = onDismiss)
                    .testTag("close_about_modal_button"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Done",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White
                )
            }
        }
    }
}

private fun readClipboardText(context: Context): String? {
    return runCatching {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val item = clipboard?.primaryClip?.getItemAt(0)
        item?.coerceToText(context)?.toString()?.trim()
    }.getOrNull()
}

private fun shareStreamLink(context: Context, stream: HyperStreamData) {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, stream.title)
        putExtra(Intent.EXTRA_TEXT, "${stream.title}\nhttps://youtu.be/${stream.videoId}")
    }
    context.startActivity(Intent.createChooser(shareIntent, "Share YouTube Link"))
}
