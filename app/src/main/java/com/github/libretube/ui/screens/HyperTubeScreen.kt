package com.github.libretube.ui.screens

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.github.libretube.api.ChannelPageData
import com.github.libretube.api.ChapterInfo
import com.github.libretube.api.HyperStreamData
import com.github.libretube.api.RelatedStreamInfo
import com.github.libretube.api.SearchChannelItem
import com.github.libretube.ui.components.IosVideoPlayerStage
import com.github.libretube.ui.models.HyperTubeUiState
import com.github.libretube.ui.models.RecentStreamEntry
import com.github.libretube.ui.models.SideBoxMode
import com.github.libretube.ui.models.formatCompactCount
import com.github.libretube.ui.models.formatDuration
import com.github.libretube.ui.theme.IosCrimsonPink
import com.github.libretube.ui.theme.IosCyanTint
import com.github.libretube.ui.theme.IosElectricBlue
import com.github.libretube.ui.theme.IosLabelPrimary
import com.github.libretube.ui.theme.IosLabelSecondary
import com.github.libretube.ui.theme.IosLabelTertiary
import com.github.libretube.ui.theme.IosOledBlack

private val MinimalBoxSurface = Color(0xFF1B1B1D)
private val MinimalElevatedSurface = Color(0xFF262629)
private val MinimalBorderColor = Color.White.copy(alpha = 0.07f)

@Composable
fun HyperTubeScreen(
    uiState: HyperTubeUiState,
    onLinkInputChange: (String) -> Unit,
    onPlayClicked: (String?) -> Unit,
    onSelectVideoId: (String) -> Unit,
    onOpenChannel: (String, String) -> Unit,
    onSelectSideBoxMode: (SideBoxMode) -> Unit,
    onSelectQuality: (Int) -> Unit,
    onSelectSpeed: (Float) -> Unit,
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
    var liveAmbientColor by remember { mutableStateOf(Color(0xFFE05297)) }

    val smoothAmbientGlow by animateColorAsState(
        targetValue = if (uiState.isAmbientModeEnabled && uiState.currentStream != null) {
            liveAmbientColor
        } else {
            Color.Transparent
        },
        animationSpec = tween(800),
        label = "screen_ambient_glow"
    )

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

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(IosOledBlack)
            .drawBehind {
                // Soft ambient backdrop aura around the left video stage area (matching user's mockup!)
                if (smoothAmbientGlow.alpha > 0.01f) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                smoothAmbientGlow.copy(alpha = 0.34f),
                                smoothAmbientGlow.copy(alpha = 0.14f),
                                Color.Transparent
                            ),
                            center = Offset(size.width * 0.34f, size.height * 0.38f),
                            radius = size.width * 0.48f
                        ),
                        center = Offset(size.width * 0.34f, size.height * 0.38f),
                        radius = size.width * 0.48f
                    )
                }
            }
    ) {
        val isWideLayout = maxWidth >= 680.dp

        if (isFullscreen && uiState.currentStream != null) {
            IosVideoPlayerStage(
                stream = uiState.currentStream,
                dashDataUri = uiState.dashDataUri,
                directStreamUrl = uiState.directStreamUrl,
                directAudioUrl = uiState.directAudioUrl,
                availableQualities = uiState.availableQualities,
                selectedQualityHeight = uiState.selectedQualityHeight,
                playbackSpeed = uiState.playbackSpeed,
                isAudioOnly = uiState.isAudioOnly,
                isAmbientModeEnabled = uiState.isAmbientModeEnabled,
                onToggleAmbientMode = onToggleAmbientMode,
                onAmbientColorChanged = { liveAmbientColor = it },
                startTimestampSeconds = uiState.startTimestampSeconds,
                seekRequestSeconds = seekRequestSeconds,
                onSeekRequestConsumed = { seekRequestSeconds = null },
                onSelectQuality = onSelectQuality,
                onSelectSpeed = onSelectSpeed,
                onToggleAudioOnly = onToggleAudioOnly,
                isFullscreen = true,
                onToggleFullscreen = { isFullscreen = false },
                modifier = Modifier.fillMaxSize()
            )
        } else if (isWideLayout) {
            // Canonical 16:10 Tablet & Landscape Minimalist Layout following the user's mockup
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(22.dp)
                ) {
                    // LEFT COLUMN (~66%): Top Centered Title + Video w/ Ambient Mode + Glowing Scrubber + Bottom Details Box
                    Column(
                        modifier = Modifier
                            .weight(0.66f)
                            .fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Top Floating Title (matching "YOASOBI 「アイドル」 Official Music Video" in mockup)
                        Text(
                            text = uiState.currentStream?.title ?: "HyperTube",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = Color.White.copy(alpha = 0.94f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp, start = 16.dp, end = 16.dp)
                        )

                        // Video Stage + External Glowing White Scrubber Bar
                        MinimalVideoStageContainer(
                            uiState = uiState,
                            seekRequestSeconds = seekRequestSeconds,
                            onSeekRequestConsumed = { seekRequestSeconds = null },
                            onAmbientColorChanged = { liveAmbientColor = it },
                            onSelectQuality = onSelectQuality,
                            onSelectSpeed = onSelectSpeed,
                            onToggleAudioOnly = onToggleAudioOnly,
                            onToggleAmbientMode = onToggleAmbientMode,
                            onToggleFullscreen = { isFullscreen = true },
                            onQuickPlayId = onSelectVideoId
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Bottom Box: Video Details Box (matching the dark rounded bottom box in mockup)
                        MinimalVideoDetailsBottomBox(
                            stream = uiState.currentStream,
                            selectedQuality = uiState.selectedQualityHeight,
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

                    // RIGHT COLUMN (~34%): Top Centered Header ("Playlist" / "Search" / "Channel") + Side Box
                    Column(
                        modifier = Modifier
                            .weight(0.34f)
                            .fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Top Floating Header above Side Box (matching "Playlist" in mockup)
                        Text(
                            text = uiState.sideBoxMode.headerTitle,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = Color.White.copy(alpha = 0.94f),
                            maxLines = 1,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp)
                        )

                        // Side Box: Query / Video Search / Channel Explorer / Playlist
                        MinimalMultiPurposeSideBox(
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
                            onRemoveRecent = onRemoveHistoryItem,
                            onClearHistory = onClearHistory,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        )
                    }
                }

                // Bottom iOS Home Indicator Pill (matching the bottom center bar in the user's mockup!)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .width(134.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.38f))
                )
            }
        } else {
            // Compact Portrait Handheld Layout with the same minimalist iOS aesthetic & Ambient Mode
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = uiState.currentStream?.title ?: "HyperTube",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = Color.White.copy(alpha = 0.94f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    MinimalVideoStageContainer(
                        uiState = uiState,
                        seekRequestSeconds = seekRequestSeconds,
                        onSeekRequestConsumed = { seekRequestSeconds = null },
                        onAmbientColorChanged = { liveAmbientColor = it },
                        onSelectQuality = onSelectQuality,
                        onSelectSpeed = onSelectSpeed,
                        onToggleAudioOnly = onToggleAudioOnly,
                        onToggleAmbientMode = onToggleAmbientMode,
                        onToggleFullscreen = { isFullscreen = true },
                        onQuickPlayId = onSelectVideoId
                    )

                    MinimalVideoDetailsBottomBox(
                        stream = uiState.currentStream,
                        selectedQuality = uiState.selectedQualityHeight,
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
                            .height(126.dp)
                    )

                    MinimalMultiPurposeSideBox(
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
                        onRemoveRecent = onRemoveHistoryItem,
                        onClearHistory = onClearHistory,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .width(120.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.35f))
                )
            }
        }

        if (uiState.showAboutDialog) {
            DeveloperAboutModal(onDismiss = { onToggleAbout(false) })
        }
    }
}

@Composable
private fun MinimalVideoStageContainer(
    uiState: HyperTubeUiState,
    seekRequestSeconds: Long?,
    onSeekRequestConsumed: () -> Unit,
    onAmbientColorChanged: (Color) -> Unit,
    onSelectQuality: (Int) -> Unit,
    onSelectSpeed: (Float) -> Unit,
    onToggleAudioOnly: () -> Unit,
    onToggleAmbientMode: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onQuickPlayId: (String) -> Unit
) {
    AnimatedContent(
        targetState = uiState.currentStream != null,
        transitionSpec = {
            fadeIn(spring(stiffness = Spring.StiffnessMediumLow))
                .togetherWith(fadeOut(spring(stiffness = Spring.StiffnessMediumLow)))
        },
        label = "minimal_video_stage"
    ) { hasStream ->
        if (hasStream && uiState.currentStream != null) {
            IosVideoPlayerStage(
                stream = uiState.currentStream,
                dashDataUri = uiState.dashDataUri,
                directStreamUrl = uiState.directStreamUrl,
                directAudioUrl = uiState.directAudioUrl,
                availableQualities = uiState.availableQualities,
                selectedQualityHeight = uiState.selectedQualityHeight,
                playbackSpeed = uiState.playbackSpeed,
                isAudioOnly = uiState.isAudioOnly,
                isAmbientModeEnabled = uiState.isAmbientModeEnabled,
                onToggleAmbientMode = onToggleAmbientMode,
                onAmbientColorChanged = onAmbientColorChanged,
                startTimestampSeconds = uiState.startTimestampSeconds,
                seekRequestSeconds = seekRequestSeconds,
                onSeekRequestConsumed = onSeekRequestConsumed,
                onSelectQuality = onSelectQuality,
                onSelectSpeed = onSelectSpeed,
                onToggleAudioOnly = onToggleAudioOnly,
                isFullscreen = false,
                onToggleFullscreen = onToggleFullscreen,
                showExternalScrubber = true
            )
        } else {
            // Minimalist Standby Stage + Disabled Scrubber Placeholder matching mockup geometry
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(26.dp))
                        .background(MinimalBoxSurface)
                        .border(1.dp, MinimalBorderColor, RoundedCornerShape(26.dp))
                        .clickable {
                            if (!uiState.isLoading && uiState.recentStreams.isNotEmpty()) {
                                onQuickPlayId(uiState.recentStreams.first().videoId)
                            }
                        }
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (uiState.isLoading) {
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
                                text = "Loading Stream…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = IosLabelSecondary
                            )
                        }
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SmartDisplay,
                                contentDescription = "HyperTube",
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                text = "Tap to play or search / paste a link on the right",
                                style = MaterialTheme.typography.bodyMedium,
                                color = IosLabelSecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                // Subtle idle scrubber track matching the mockup
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp)
                        .height(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.14f))
                )
            }
        }
    }
}

/**
 * Bottom Box under the video player: displays video details, channel link (tappable to explore
 * channel in the Side Box), stats, chapters, and description.
 */
@Composable
private fun MinimalVideoDetailsBottomBox(
    stream: HyperStreamData?,
    selectedQuality: Int,
    onOpenChannel: () -> Unit,
    onSeekToSeconds: (Long) -> Unit,
    onShareLink: () -> Unit,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(MinimalBoxSurface)
            .border(1.dp, MinimalBorderColor, RoundedCornerShape(22.dp))
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
                // Top Row: Tappable Channel Chip + Stats + Share + About
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Tappable Channel Info -> opens Channel Page in the Side Box!
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable(onClick = onOpenChannel)
                            .padding(vertical = 4.dp, horizontal = 4.dp)
                            .testTag("explore_channel_button"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AsyncImage(
                            model = stream.uploaderAvatar,
                            contentDescription = stream.uploaderName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(MinimalElevatedSurface)
                        )

                        Column(modifier = Modifier.weight(1f, fill = false)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = stream.uploaderName,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = IosLabelPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (stream.uploaderVerified) {
                                    Icon(
                                        imageVector = Icons.Rounded.CheckCircle,
                                        contentDescription = "Verified",
                                        tint = Color.White.copy(alpha = 0.75f),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowForwardIos,
                                    contentDescription = "Explore Channel",
                                    tint = IosLabelTertiary,
                                    modifier = Modifier.size(11.dp)
                                )
                            }
                            if (stream.uploaderSubscribers > 0) {
                                Text(
                                    text = "${formatCompactCount(stream.uploaderSubscribers)} subscribers • Tap to view channel",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = IosLabelSecondary,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    // Minimalist Stats & Action Pills
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (stream.views > 0) {
                            MinimalMetaPill(
                                icon = Icons.Rounded.Visibility,
                                text = formatCompactCount(stream.views)
                            )
                        }
                        if (stream.likes > 0) {
                            MinimalMetaPill(
                                icon = Icons.Rounded.ThumbUp,
                                text = formatCompactCount(stream.likes)
                            )
                        }
                        if (selectedQuality > 0) {
                            Box(
                                modifier = Modifier
                                    .height(28.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(MinimalElevatedSurface)
                                    .padding(horizontal = 9.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${selectedQuality}p",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White.copy(alpha = 0.9f)
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(MinimalElevatedSurface)
                                .clickable(onClick = onShareLink)
                                .testTag("share_stream_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Share,
                                contentDescription = "Share",
                                tint = IosLabelPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(MinimalElevatedSurface)
                                .clickable(onClick = onOpenAbout)
                                .testTag("developer_badge_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Info,
                                contentDescription = "About HyperTube",
                                tint = IosLabelSecondary,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }

                // Chapters Row (if present)
                if (stream.chapters.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(stream.chapters) { chapter ->
                            MinimalChapterChip(
                                chapter = chapter,
                                onClick = { onSeekToSeconds(chapter.startSeconds) }
                            )
                        }
                    }
                }

                // Upload Date + Description
                val metaPrefix = if (stream.uploadDateText.isNotBlank()) "${stream.uploadDateText}  •  " else ""
                Text(
                    text = metaPrefix + stream.description.ifBlank { "No description provided." },
                    style = MaterialTheme.typography.bodyMedium,
                    color = IosLabelSecondary,
                    lineHeight = 19.sp
                )
            }
        } else {
            // Standby Video Details Box
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Video Details",
                        style = MaterialTheme.typography.titleSmall,
                        color = IosLabelPrimary
                    )
                    Text(
                        text = "Select a video or search in the side panel to view channel details, chapters, and description.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = IosLabelSecondary
                    )
                }

                Row(
                    modifier = Modifier
                        .height(32.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MinimalElevatedSurface)
                        .clickable(onClick = onOpenAbout)
                        .padding(horizontal = 12.dp)
                        .testTag("developer_badge_button"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = "Developer",
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "shumail0ahmed",
                        style = MaterialTheme.typography.labelMedium,
                        color = IosLabelPrimary
                    )
                }
            }
        }
    }
}

/**
 * Right-hand Side Box:
 * - Top unified iOS search / link query bar
 * - Mode switcher: Playlist | Search | Channel
 * - Displays Playlist (Up Next + Recent), YouTube Search Results, or Channel Page Explorer
 */
@Composable
private fun MinimalMultiPurposeSideBox(
    uiState: HyperTubeUiState,
    onLinkInputChange: (String) -> Unit,
    onSubmitQueryOrLink: () -> Unit,
    onPasteClipboard: () -> Unit,
    onSelectMode: (SideBoxMode) -> Unit,
    onSelectVideo: (String) -> Unit,
    onSelectChannel: (String, String) -> Unit,
    onRemoveRecent: (String) -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(MinimalBoxSurface)
            .border(1.dp, MinimalBorderColor, RoundedCornerShape(24.dp))
            .padding(14.dp)
            .testTag("multi_purpose_side_box"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Minimalist iOS Query / Link Input Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MinimalElevatedSurface)
                .padding(horizontal = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = "Search or Paste Link",
                tint = IosLabelSecondary,
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
                        text = "Search YouTube or paste link…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = IosLabelTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BasicTextField(
                    value = uiState.linkInput,
                    onValueChange = onLinkInputChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = IosLabelPrimary),
                    cursorBrush = SolidColor(Color.White),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = { onSubmitQueryOrLink() },
                        onGo = { onSubmitQueryOrLink() }
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
                        contentDescription = "Clear",
                        tint = IosLabelSecondary,
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
                    tint = IosLabelSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // 2. Minimal Mode Switcher Pills (Playlist | Search | Channel)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MinimalElevatedSurface.copy(alpha = 0.7f))
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            SideBoxMode.entries.forEach { mode ->
                val selected = uiState.sideBoxMode == mode
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (selected) Color.White.copy(alpha = 0.16f) else Color.Transparent
                        )
                        .clickable { onSelectMode(mode) }
                        .testTag("side_box_tab_${mode.name.lowercase()}"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = mode.headerTitle,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) Color.White else IosLabelSecondary
                    )
                }
            }
        }

        // Optional error toast inside Side Box
        AnimatedVisibility(visible = !uiState.errorMessage.isNullOrBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(IosCrimsonPink.copy(alpha = 0.14f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.ErrorOutline,
                    contentDescription = null,
                    tint = IosCrimsonPink,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = uiState.errorMessage.orEmpty(),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFFFFB3C1),
                    maxLines = 2
                )
            }
        }

        // 3. Content Area based on active SideBoxMode
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
                        modifier = Modifier.size(28.dp)
                    )
                }
            } else {
                when (uiState.sideBoxMode) {
                    SideBoxMode.PLAYLIST -> {
                        SideBoxPlaylistView(
                            relatedStreams = uiState.currentStream?.relatedStreams.orEmpty(),
                            recentStreams = uiState.recentStreams,
                            activeVideoId = uiState.currentStream?.videoId,
                            onSelectVideo = onSelectVideo,
                            onSelectChannel = onSelectChannel,
                            onRemoveRecent = onRemoveRecent,
                            onClearHistory = onClearHistory
                        )
                    }

                    SideBoxMode.SEARCH -> {
                        SideBoxSearchView(
                            searchResults = uiState.searchResults,
                            onSelectVideo = onSelectVideo,
                            onSelectChannel = onSelectChannel
                        )
                    }

                    SideBoxMode.CHANNEL -> {
                        SideBoxChannelExplorerView(
                            channelPage = uiState.currentChannelPage,
                            onSelectVideo = onSelectVideo
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SideBoxPlaylistView(
    relatedStreams: List<RelatedStreamInfo>,
    recentStreams: List<RecentStreamEntry>,
    activeVideoId: String?,
    onSelectVideo: (String) -> Unit,
    onSelectChannel: (String, String) -> Unit,
    onRemoveRecent: (String) -> Unit,
    onClearHistory: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 12.dp)
    ) {
        if (relatedStreams.isNotEmpty()) {
            item {
                Text(
                    text = "Up Next",
                    style = MaterialTheme.typography.labelMedium,
                    color = IosLabelSecondary,
                    modifier = Modifier.padding(vertical = 2.dp, horizontal = 2.dp)
                )
            }
            items(relatedStreams, key = { "rel_${it.videoId}" }) { item ->
                MinimalStreamRowCard(
                    videoId = item.videoId,
                    title = item.title,
                    uploaderName = item.uploaderName,
                    thumbnailUrl = item.thumbnailUrl,
                    durationSeconds = item.durationSeconds,
                    views = item.views,
                    isActive = item.videoId == activeVideoId,
                    onClick = { onSelectVideo(item.videoId) },
                    onChannelClick = if (item.uploaderUrl.isNotBlank() || item.uploaderName.isNotBlank()) {
                        { onSelectChannel(item.uploaderUrl.ifBlank { item.uploaderName }, item.uploaderName) }
                    } else null
                )
            }
        }

        if (recentStreams.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = if (relatedStreams.isNotEmpty()) 6.dp else 0.dp, bottom = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent Queue",
                        style = MaterialTheme.typography.labelMedium,
                        color = IosLabelSecondary
                    )
                    Text(
                        text = "Clear",
                        style = MaterialTheme.typography.labelMedium,
                        color = IosLabelTertiary,
                        modifier = Modifier
                            .clickable(onClick = onClearHistory)
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
            items(recentStreams, key = { "rec_${it.videoId}" }) { entry ->
                MinimalStreamRowCard(
                    videoId = entry.videoId,
                    title = entry.title,
                    uploaderName = entry.uploaderName,
                    thumbnailUrl = entry.thumbnailUrl,
                    durationSeconds = entry.durationSeconds,
                    views = -1L,
                    isActive = entry.videoId == activeVideoId,
                    onClick = { onSelectVideo(entry.videoId) },
                    onChannelClick = { onSelectChannel(entry.uploaderName, entry.uploaderName) },
                    onRemoveClick = { onRemoveRecent(entry.videoId) }
                )
            }
        }
    }
}

@Composable
private fun SideBoxSearchView(
    searchResults: com.github.libretube.api.SearchResultsPage?,
    onSelectVideo: (String) -> Unit,
    onSelectChannel: (String, String) -> Unit
) {
    if (searchResults == null || (searchResults.channels.isEmpty() && searchResults.videos.isEmpty())) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Type any query above and press Search to find videos & channels.",
                style = MaterialTheme.typography.bodyMedium,
                color = IosLabelSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(16.dp)
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 12.dp)
    ) {
        if (searchResults.channels.isNotEmpty()) {
            item {
                Text(
                    text = "Channels",
                    style = MaterialTheme.typography.labelMedium,
                    color = IosLabelSecondary,
                    modifier = Modifier.padding(horizontal = 2.dp)
                )
            }
            items(searchResults.channels.take(4), key = { "ch_${it.channelUrl}_${it.name}" }) { channel ->
                MinimalChannelRowCard(
                    channel = channel,
                    onClick = {
                        onSelectChannel(channel.channelUrl.ifBlank { channel.name }, channel.name)
                    }
                )
            }
        }

        if (searchResults.videos.isNotEmpty()) {
            item {
                Text(
                    text = "Videos",
                    style = MaterialTheme.typography.labelMedium,
                    color = IosLabelSecondary,
                    modifier = Modifier.padding(top = 4.dp, start = 2.dp)
                )
            }
            items(searchResults.videos, key = { "sr_${it.videoId}" }) { video ->
                MinimalStreamRowCard(
                    videoId = video.videoId,
                    title = video.title,
                    uploaderName = video.uploaderName,
                    thumbnailUrl = video.thumbnailUrl,
                    durationSeconds = video.durationSeconds,
                    views = video.views,
                    isActive = false,
                    onClick = { onSelectVideo(video.videoId) },
                    onChannelClick = {
                        onSelectChannel(video.uploaderUrl.ifBlank { video.uploaderName }, video.uploaderName)
                    }
                )
            }
        }
    }
}

@Composable
private fun SideBoxChannelExplorerView(
    channelPage: ChannelPageData?,
    onSelectVideo: (String) -> Unit
) {
    if (channelPage == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Tap any channel name in the video details or search results to explore its videos here.",
                style = MaterialTheme.typography.bodyMedium,
                color = IosLabelSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(16.dp)
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 12.dp)
    ) {
        // Channel Header Card
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MinimalElevatedSurface)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AsyncImage(
                        model = channelPage.avatarUrl,
                        contentDescription = channelPage.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MinimalBoxSurface)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = channelPage.name,
                                style = MaterialTheme.typography.titleSmall,
                                color = IosLabelPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (channelPage.verified) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = "Verified",
                                    tint = Color.White.copy(alpha = 0.8f),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                        if (channelPage.subscriberCount > 0) {
                            Text(
                                text = "${formatCompactCount(channelPage.subscriberCount)} subscribers",
                                style = MaterialTheme.typography.labelMedium,
                                color = IosLabelSecondary
                            )
                        }
                    }
                }

                if (channelPage.description.isNotBlank()) {
                    Text(
                        text = channelPage.description,
                        style = MaterialTheme.typography.labelMedium,
                        color = IosLabelSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        item {
            Text(
                text = "Channel Videos (${channelPage.videos.size})",
                style = MaterialTheme.typography.labelMedium,
                color = IosLabelSecondary,
                modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp)
            )
        }

        items(channelPage.videos, key = { "chv_${it.videoId}" }) { video ->
            MinimalStreamRowCard(
                videoId = video.videoId,
                title = video.title,
                uploaderName = channelPage.name,
                thumbnailUrl = video.thumbnailUrl,
                durationSeconds = video.durationSeconds,
                views = video.views,
                isActive = false,
                onClick = { onSelectVideo(video.videoId) }
            )
        }
    }
}

@Composable
private fun MinimalChannelRowCard(
    channel: SearchChannelItem,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MinimalElevatedSurface.copy(alpha = 0.75f))
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        AsyncImage(
            model = channel.avatarUrl,
            contentDescription = channel.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(MinimalBoxSurface)
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = channel.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = IosLabelPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (channel.verified) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.75f),
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
            if (channel.subscriberCount > 0) {
                Text(
                    text = "${formatCompactCount(channel.subscriberCount)} subscribers",
                    style = MaterialTheme.typography.labelMedium,
                    color = IosLabelSecondary
                )
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowForwardIos,
            contentDescription = "Open Channel",
            tint = IosLabelTertiary,
            modifier = Modifier.size(13.dp)
        )
    }
}

@Composable
private fun MinimalStreamRowCard(
    videoId: String,
    title: String,
    uploaderName: String,
    thumbnailUrl: String,
    durationSeconds: Long,
    views: Long,
    isActive: Boolean,
    onClick: () -> Unit,
    onChannelClick: (() -> Unit)? = null,
    onRemoveClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (isActive) Color.White.copy(alpha = 0.12f)
                else MinimalElevatedSurface.copy(alpha = 0.55f)
            )
            .clickable(onClick = onClick)
            .padding(8.dp)
            .testTag("stream_card_$videoId"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .width(104.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(10.dp))
                .background(IosOledBlack)
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
                    .clip(RoundedCornerShape(5.dp))
                    .background(Color.Black.copy(alpha = 0.78f))
                    .padding(horizontal = 5.dp, vertical = 1.dp)
            ) {
                Text(
                    text = formatDuration(durationSeconds),
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp),
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
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    lineHeight = 16.sp
                ),
                color = IosLabelPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            val subline = if (views > 0) {
                "$uploaderName • ${formatCompactCount(views)} views"
            } else {
                uploaderName
            }
            Text(
                text = subline,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
                color = IosLabelSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = if (onChannelClick != null) {
                    Modifier.clickable { onChannelClick() }
                } else {
                    Modifier
                }
            )
        }

        if (onRemoveClick != null) {
            IconButton(
                onClick = onRemoveClick,
                modifier = Modifier.size(26.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = "Remove",
                    tint = IosLabelTertiary,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}

@Composable
private fun MinimalMetaPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String
) {
    Row(
        modifier = Modifier
            .height(28.dp)
            .clip(RoundedCornerShape(50))
            .background(MinimalElevatedSurface)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = IosLabelSecondary,
            modifier = Modifier.size(13.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = IosLabelPrimary
        )
    }
}

@Composable
private fun MinimalChapterChip(
    chapter: ChapterInfo,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .height(28.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MinimalElevatedSurface)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = formatDuration(chapter.startSeconds),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.9f)
        )
        Text(
            text = chapter.title,
            style = MaterialTheme.typography.labelMedium,
            color = IosLabelSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DeveloperAboutModal(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(MinimalBoxSurface)
                .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(26.dp))
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MinimalElevatedSurface),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = "HyperTube",
                    tint = Color.White,
                    modifier = Modifier.size(30.dp)
                )
            }

            Text(
                text = "HyperTube",
                style = MaterialTheme.typography.headlineMedium,
                color = IosLabelPrimary
            )

            Text(
                text = "Crafted by shumail0ahmed",
                style = MaterialTheme.typography.titleSmall,
                color = Color.White.copy(alpha = 0.85f)
            )

            Text(
                text = "Minimalist iOS Cinema YouTube Player featuring Real-Time Ambient Mode, 144p–4K Adaptive DASH Streaming, Live YouTube Search, and Channel Page Exploration.",
                style = MaterialTheme.typography.bodyMedium,
                color = IosLabelSecondary,
                textAlign = TextAlign.Center
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background( Color.White.copy(alpha = 0.14f) )
                    .clickable(onClick = onDismiss)
                    .testTag("close_about_modal_button"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Done",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White
                )
            }
        }
    }
}

private fun readClipboardText(context: Context): String? {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    val clip = clipboard?.primaryClip ?: return null
    if (clip.itemCount <= 0) return null
    return clip.getItemAt(0).coerceToText(context)?.toString()?.trim()
}

private fun shareStreamLink(context: Context, stream: HyperStreamData) {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, stream.title)
        putExtra(Intent.EXTRA_TEXT, "${stream.title} - https://youtu.be/${stream.videoId}")
    }
    context.startActivity(Intent.createChooser(shareIntent, "Share Stream"))
}
