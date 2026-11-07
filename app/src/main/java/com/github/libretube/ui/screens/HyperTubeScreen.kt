package com.github.libretube.ui.screens

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlayCircleFilled
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.github.libretube.api.ChapterInfo
import com.github.libretube.api.HyperStreamData
import com.github.libretube.api.RelatedStreamInfo
import com.github.libretube.ui.components.IosVideoPlayerStage
import com.github.libretube.ui.models.HyperTubeUiState
import com.github.libretube.ui.models.InspectorTab
import com.github.libretube.ui.models.RecentStreamEntry
import com.github.libretube.ui.models.formatCompactCount
import com.github.libretube.ui.models.formatDuration
import com.github.libretube.ui.theme.IosCrimsonPink
import com.github.libretube.ui.theme.IosCyanTint
import com.github.libretube.ui.theme.IosElectricBlue
import com.github.libretube.ui.theme.IosIndigoAccent
import com.github.libretube.ui.theme.IosLabelPrimary
import com.github.libretube.ui.theme.IosLabelSecondary
import com.github.libretube.ui.theme.IosLabelTertiary
import com.github.libretube.ui.theme.IosMintGreen
import com.github.libretube.ui.theme.IosOledBlack
import com.github.libretube.ui.theme.IosSeparator
import com.github.libretube.ui.theme.IosSurfacePrimary
import com.github.libretube.ui.theme.IosSurfaceSecondary
import com.github.libretube.ui.theme.IosSurfaceTertiary

@Composable
fun HyperTubeScreen(
    uiState: HyperTubeUiState,
    onLinkInputChange: (String) -> Unit,
    onPlayClicked: (String?) -> Unit,
    onSelectVideoId: (String) -> Unit,
    onSelectQuality: (Int) -> Unit,
    onSelectSpeed: (Float) -> Unit,
    onToggleAudioOnly: () -> Unit,
    onSelectTab: (InspectorTab) -> Unit,
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

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(IosOledBlack)
            .drawBehind {
                // Subtle iOS ambient top-left & top-right radial glow
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            IosElectricBlue.copy(alpha = 0.14f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.18f, size.height * 0.08f),
                        radius = size.width * 0.55f
                    )
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            IosIndigoAccent.copy(alpha = 0.11f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.85f, size.height * 0.15f),
                        radius = size.width * 0.5f
                    )
                )
            }
    ) {
        val isTabletOrWide = maxWidth >= 720.dp

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
        } else if (isTabletOrWide) {
            // 16:10 Tablet & Wide Landscape Canonical Split-Stage Layout
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 24.dp, vertical = 14.dp)
            ) {
                IosTopNavigationBar(
                    isWide = true,
                    onOpenAbout = { onToggleAbout(true) }
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(22.dp)
                ) {
                    // Left 62%: Primary Theater Stage + Metadata + Recent Dock
                    Column(
                        modifier = Modifier
                            .weight(0.62f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        MainTheaterStage(
                            uiState = uiState,
                            seekRequestSeconds = seekRequestSeconds,
                            onSeekRequestConsumed = { seekRequestSeconds = null },
                            onSelectQuality = onSelectQuality,
                            onSelectSpeed = onSelectSpeed,
                            onToggleAudioOnly = onToggleAudioOnly,
                            onToggleFullscreen = { isFullscreen = true },
                            onQuickPlayId = onSelectVideoId
                        )

                        if (uiState.currentStream != null) {
                            StreamPrimaryHeaderCard(
                                stream = uiState.currentStream,
                                selectedQuality = uiState.selectedQualityHeight,
                                isAudioOnly = uiState.isAudioOnly,
                                onShareLink = { shareStreamLink(context, uiState.currentStream) }
                            )
                        }

                        if (uiState.recentStreams.isNotEmpty()) {
                            RecentStreamsHorizontalDock(
                                recentStreams = uiState.recentStreams,
                                activeVideoId = uiState.currentStream?.videoId,
                                onSelectStream = onSelectVideoId,
                                onRemoveStream = onRemoveHistoryItem,
                                onClearAll = onClearHistory
                            )
                        }
                    }

                    // Right 38%: iOS Glassmorphic Link Console & Segmented Inspector
                    Column(
                        modifier = Modifier
                            .weight(0.38f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        IosLinkCommandCard(
                            linkInput = uiState.linkInput,
                            isLoading = uiState.isLoading,
                            errorMessage = uiState.errorMessage,
                            onLinkInputChange = onLinkInputChange,
                            onPasteAndPlay = {
                                val clip = readClipboardText(context)
                                if (!clip.isNullOrBlank()) {
                                    onLinkInputChange(clip)
                                    focusManager.clearFocus()
                                    onPlayClicked(clip)
                                }
                            },
                            onPlayClicked = {
                                focusManager.clearFocus()
                                onPlayClicked(null)
                            }
                        )

                        IosSegmentedInspectorPanel(
                            uiState = uiState,
                            onSelectTab = onSelectTab,
                            onSeekToSeconds = { sec -> seekRequestSeconds = sec },
                            onSelectRelated = onSelectVideoId,
                            onSelectRecent = onSelectVideoId,
                            onRemoveRecent = onRemoveHistoryItem,
                            onClearHistory = onClearHistory,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        )
                    }
                }
            }
        } else {
            // Compact Handheld Portrait Layout
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(modifier = Modifier.height(8.dp))
                IosTopNavigationBar(
                    isWide = false,
                    onOpenAbout = { onToggleAbout(true) }
                )

                Spacer(modifier = Modifier.height(12.dp))

                IosLinkCommandCard(
                    linkInput = uiState.linkInput,
                    isLoading = uiState.isLoading,
                    errorMessage = uiState.errorMessage,
                    onLinkInputChange = onLinkInputChange,
                    onPasteAndPlay = {
                        val clip = readClipboardText(context)
                        if (!clip.isNullOrBlank()) {
                            onLinkInputChange(clip)
                            focusManager.clearFocus()
                            onPlayClicked(clip)
                        }
                    },
                    onPlayClicked = {
                        focusManager.clearFocus()
                        onPlayClicked(null)
                    }
                )

                Spacer(modifier = Modifier.height(14.dp))

                MainTheaterStage(
                    uiState = uiState,
                    seekRequestSeconds = seekRequestSeconds,
                    onSeekRequestConsumed = { seekRequestSeconds = null },
                    onSelectQuality = onSelectQuality,
                    onSelectSpeed = onSelectSpeed,
                    onToggleAudioOnly = onToggleAudioOnly,
                    onToggleFullscreen = { isFullscreen = true },
                    onQuickPlayId = onSelectVideoId
                )

                Spacer(modifier = Modifier.height(12.dp))

                IosSegmentedInspectorPanel(
                    uiState = uiState,
                    onSelectTab = onSelectTab,
                    onSeekToSeconds = { sec -> seekRequestSeconds = sec },
                    onSelectRelated = onSelectVideoId,
                    onSelectRecent = onSelectVideoId,
                    onRemoveRecent = onRemoveHistoryItem,
                    onClearHistory = onClearHistory,
                    showHeaderInOverview = true,
                    onShareStream = {
                        uiState.currentStream?.let { shareStreamLink(context, it) }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                )
            }
        }

        if (uiState.showAboutDialog) {
            DeveloperAboutModal(onDismiss = { onToggleAbout(false) })
        }
    }
}

@Composable
private fun IosTopNavigationBar(
    isWide: Boolean,
    onOpenAbout: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(IosElectricBlue, IosIndigoAccent)
                        )
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(13.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = "HyperTube Logo",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }

            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "HyperTube",
                        style = if (isWide) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge,
                        color = IosLabelPrimary
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(IosElectricBlue.copy(alpha = 0.16f))
                            .border(1.dp, IosElectricBlue.copy(alpha = 0.4f), RoundedCornerShape(50))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "PRO STREAM",
                            style = MaterialTheme.typography.labelMedium,
                            color = IosCyanTint
                        )
                    }
                }
                Text(
                    text = "Direct Link Stream Engine • by shumail0ahmed",
                    style = MaterialTheme.typography.bodyMedium,
                    color = IosLabelSecondary
                )
            }
        }

        // Developer Badge / About Pill
        Row(
            modifier = Modifier
                .height(40.dp)
                .clip(RoundedCornerShape(50))
                .background(IosSurfacePrimary.copy(alpha = 0.85f))
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(50))
                .clickable(onClick = onOpenAbout)
                .padding(horizontal = 14.dp)
                .testTag("developer_badge_button"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.AutoAwesome,
                contentDescription = "About Developer",
                tint = IosCyanTint,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "shumail0ahmed",
                style = MaterialTheme.typography.labelLarge,
                color = IosLabelPrimary
            )
        }
    }
}

@Composable
private fun IosLinkCommandCard(
    linkInput: String,
    isLoading: Boolean,
    errorMessage: String?,
    onLinkInputChange: (String) -> Unit,
    onPasteAndPlay: () -> Unit,
    onPlayClicked: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(IosSurfacePrimary.copy(alpha = 0.9f))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(22.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(IosSurfaceSecondary.copy(alpha = 0.9f))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Link,
                contentDescription = "YouTube Link",
                tint = IosCyanTint,
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                if (linkInput.isEmpty()) {
                    Text(
                        text = "Paste YouTube URL, Shorts link, or ID…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = IosLabelTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BasicTextField(
                    value = linkInput,
                    onValueChange = onLinkInputChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = IosLabelPrimary),
                    cursorBrush = SolidColor(IosElectricBlue),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { onPlayClicked() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("youtube_link_input")
                )
            }

            if (linkInput.isNotEmpty()) {
                IconButton(
                    onClick = { onLinkInputChange("") },
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("clear_link_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Clear Link",
                        tint = IosLabelSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Paste & Play Pill Button
            IosSpringButton(
                onClick = onPasteAndPlay,
                backgroundColor = IosSurfaceTertiary,
                modifier = Modifier
                    .weight(0.45f)
                    .height(48.dp)
                    .testTag("paste_clipboard_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.ContentPaste,
                    contentDescription = "Paste from Clipboard",
                    tint = IosCyanTint,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Paste & Play",
                    style = MaterialTheme.typography.labelLarge,
                    color = IosLabelPrimary
                )
            }

            // Primary Stream Button
            IosSpringButton(
                onClick = onPlayClicked,
                backgroundColor = IosElectricBlue,
                modifier = Modifier
                    .weight(0.55f)
                    .height(48.dp)
                    .testTag("play_link_button")
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.2.dp,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Extracting…",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.PlayCircleFilled,
                        contentDescription = "Play Stream",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Stream Now",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White
                    )
                }
            }
        }

        AnimatedVisibility(visible = !errorMessage.isNullOrBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(IosCrimsonPink.copy(alpha = 0.15f))
                    .border(1.dp, IosCrimsonPink.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.ErrorOutline,
                    contentDescription = "Error",
                    tint = IosCrimsonPink,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = errorMessage.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFFFB3C1)
                )
            }
        }
    }
}

@Composable
private fun MainTheaterStage(
    uiState: HyperTubeUiState,
    seekRequestSeconds: Long?,
    onSeekRequestConsumed: () -> Unit,
    onSelectQuality: (Int) -> Unit,
    onSelectSpeed: (Float) -> Unit,
    onToggleAudioOnly: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onQuickPlayId: (String) -> Unit
) {
    AnimatedContent(
        targetState = uiState.currentStream != null,
        transitionSpec = {
            (fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) +
                scaleIn(initialScale = 0.96f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)))
                .togetherWith(
                    fadeOut(spring(stiffness = Spring.StiffnessMediumLow)) +
                        scaleOut(targetScale = 0.96f)
                )
        },
        label = "theater_stage_transition"
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
                startTimestampSeconds = uiState.startTimestampSeconds,
                seekRequestSeconds = seekRequestSeconds,
                onSeekRequestConsumed = onSeekRequestConsumed,
                onSelectQuality = onSelectQuality,
                onSelectSpeed = onSelectSpeed,
                onToggleAudioOnly = onToggleAudioOnly,
                isFullscreen = false,
                onToggleFullscreen = onToggleFullscreen
            )
        } else {
            // Ready-to-Play iOS Theater Standby Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                IosSurfacePrimary,
                                Color(0xFF121420),
                                IosSurfacePrimary
                            )
                        )
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(24.dp))
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                if (uiState.isLoading) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            color = IosElectricBlue,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(40.dp)
                        )
                        Text(
                            text = "Resolving Direct YouTube Stream…",
                            style = MaterialTheme.typography.titleMedium,
                            color = IosLabelPrimary
                        )
                        Text(
                            text = "Bypassing throttling & assembling DASH manifest",
                            style = MaterialTheme.typography.bodyMedium,
                            color = IosLabelSecondary
                        )
                    }
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(IosElectricBlue.copy(alpha = 0.16f))
                                .border(1.dp, IosElectricBlue.copy(alpha = 0.4f), RoundedCornerShape(18.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SmartDisplay,
                                contentDescription = "HyperTube Theater",
                                tint = IosCyanTint,
                                modifier = Modifier.size(30.dp)
                            )
                        }

                        Text(
                            text = "HyperTube Cinema Deck",
                            style = MaterialTheme.typography.titleLarge,
                            color = IosLabelPrimary
                        )
                        Text(
                            text = "Paste any YouTube link above or tap a stream below to start ad-free playback.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = IosLabelSecondary,
                            maxLines = 2
                        )

                        if (uiState.recentStreams.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            val firstRecent = uiState.recentStreams.first()
                            IosSpringButton(
                                onClick = { onQuickPlayId(firstRecent.videoId) },
                                backgroundColor = IosElectricBlue.copy(alpha = 0.25f),
                                modifier = Modifier
                                    .height(38.dp)
                                    .testTag("quick_launch_sample_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                    tint = IosCyanTint,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Play: ${firstRecent.title.take(28)}…",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = IosCyanTint
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
private fun StreamPrimaryHeaderCard(
    stream: HyperStreamData,
    selectedQuality: Int,
    isAudioOnly: Boolean,
    onShareLink: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(IosSurfacePrimary.copy(alpha = 0.88f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(22.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stream.title,
            style = MaterialTheme.typography.titleLarge,
            color = IosLabelPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Channel info
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                AsyncImage(
                    model = stream.uploaderAvatar,
                    contentDescription = stream.uploaderName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(IosSurfaceSecondary)
                )

                Column {
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
                                contentDescription = "Verified Channel",
                                tint = IosCyanTint,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                    if (stream.uploaderSubscribers > 0) {
                        Text(
                            text = "${formatCompactCount(stream.uploaderSubscribers)} subscribers",
                            style = MaterialTheme.typography.bodyMedium,
                            color = IosLabelSecondary
                        )
                    }
                }
            }

            // Stats & Share Pills
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatBadgePill(
                    icon = Icons.Rounded.Visibility,
                    text = formatCompactCount(stream.views)
                )
                if (stream.likes > 0) {
                    StatBadgePill(
                        icon = Icons.Rounded.ThumbUp,
                        text = formatCompactCount(stream.likes)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(IosSurfaceSecondary)
                        .clickable(onClick = onShareLink)
                        .testTag("share_stream_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Share,
                        contentDescription = "Share Link",
                        tint = IosLabelPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun StatBadgePill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String
) {
    Row(
        modifier = Modifier
            .height(34.dp)
            .clip(RoundedCornerShape(50))
            .background(IosSurfaceSecondary)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = IosLabelSecondary,
            modifier = Modifier.size(15.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = IosLabelPrimary
        )
    }
}

@Composable
private fun IosSegmentedInspectorPanel(
    uiState: HyperTubeUiState,
    onSelectTab: (InspectorTab) -> Unit,
    onSeekToSeconds: (Long) -> Unit,
    onSelectRelated: (String) -> Unit,
    onSelectRecent: (String) -> Unit,
    onRemoveRecent: (String) -> Unit,
    onClearHistory: () -> Unit,
    showHeaderInOverview: Boolean = false,
    onShareStream: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(IosSurfacePrimary.copy(alpha = 0.88f))
            .border(1.dp, Color.White.copy(alpha = 0.09f), RoundedCornerShape(22.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // iOS Segmented Control
        IosSegmentedControl(
            tabs = InspectorTab.entries,
            selectedTab = uiState.activeTab,
            onSelectTab = onSelectTab
        )

        // Tab Body
        Box(modifier = Modifier.fillMaxSize()) {
            when (uiState.activeTab) {
                InspectorTab.OVERVIEW -> {
                    val stream = uiState.currentStream
                    if (stream != null) {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(bottom = 16.dp)
                        ) {
                            if (showHeaderInOverview) {
                                item {
                                    StreamPrimaryHeaderCard(
                                        stream = stream,
                                        selectedQuality = uiState.selectedQualityHeight,
                                        isAudioOnly = uiState.isAudioOnly,
                                        onShareLink = onShareStream
                                    )
                                }
                            }

                            item {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(IosElectricBlue.copy(alpha = 0.16f))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "ID: ${stream.videoId}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = IosCyanTint
                                        )
                                    }
                                    if (stream.uploadDateText.isNotBlank()) {
                                        Text(
                                            text = "• ${stream.uploadDateText}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = IosLabelSecondary
                                        )
                                    }
                                }
                            }

                            item {
                                Text(
                                    text = stream.description.ifBlank { "No description provided for this stream." },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = IosLabelSecondary
                                )
                            }
                        }
                    } else {
                        // Show Recent Streams when no active stream is loaded
                        RecentStreamsVerticalList(
                            recentStreams = uiState.recentStreams,
                            onSelectStream = onSelectRecent,
                            onRemoveStream = onRemoveRecent,
                            onClearAll = onClearHistory
                        )
                    }
                }

                InspectorTab.CHAPTERS -> {
                    val chapters = uiState.currentStream?.chapters.orEmpty()
                    if (chapters.isEmpty()) {
                        EmptyInspectorState(
                            title = "No Chapters in Stream",
                            subtitle = "This video doesn't include timestamped chapters."
                        )
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 16.dp)
                        ) {
                            items(chapters) { chapter ->
                                ChapterRowItem(
                                    chapter = chapter,
                                    onClick = { onSeekToSeconds(chapter.startSeconds) }
                                )
                            }
                        }
                    }
                }

                InspectorTab.UP_NEXT -> {
                    val related = uiState.currentStream?.relatedStreams.orEmpty()
                    if (related.isNotEmpty()) {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 16.dp)
                        ) {
                            items(related) { item ->
                                RelatedStreamRowItem(
                                    item = item,
                                    onClick = { onSelectRelated(item.videoId) }
                                )
                            }
                        }
                    } else {
                        RecentStreamsVerticalList(
                            recentStreams = uiState.recentStreams,
                            onSelectStream = onSelectRecent,
                            onRemoveStream = onRemoveRecent,
                            onClearAll = onClearHistory
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IosSegmentedControl(
    tabs: List<InspectorTab>,
    selectedTab: InspectorTab,
    onSelectTab: (InspectorTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(IosSurfaceSecondary)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        tabs.forEach { tab ->
            val selected = tab == selectedTab
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(9.dp))
                    .background(
                        if (selected) IosSurfaceTertiary else Color.Transparent
                    )
                    .clickable { onSelectTab(tab) }
                    .testTag("segmented_tab_${tab.name.lowercase()}"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = tab.label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) IosLabelPrimary else IosLabelSecondary
                )
            }
        }
    }
}

@Composable
private fun ChapterRowItem(
    chapter: ChapterInfo,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(IosSurfaceSecondary.copy(alpha = 0.7f))
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = chapter.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = IosLabelPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(end = 10.dp)
        )

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(IosElectricBlue.copy(alpha = 0.2f))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = formatDuration(chapter.startSeconds),
                style = MaterialTheme.typography.labelMedium,
                color = IosCyanTint
            )
        }
    }
}

@Composable
private fun RelatedStreamRowItem(
    item: RelatedStreamInfo,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(IosSurfaceSecondary.copy(alpha = 0.65f))
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .width(116.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(10.dp))
                .background(IosOledBlack)
        ) {
            AsyncImage(
                model = item.thumbnailUrl,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.78f))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Text(
                    text = formatDuration(item.durationSeconds),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall,
                color = IosLabelPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = item.uploaderName,
                style = MaterialTheme.typography.bodyMedium,
                color = IosLabelSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (item.views > 0) {
                Text(
                    text = "${formatCompactCount(item.views)} views",
                    style = MaterialTheme.typography.labelMedium,
                    color = IosLabelTertiary
                )
            }
        }
    }
}

@Composable
private fun RecentStreamsHorizontalDock(
    recentStreams: List<RecentStreamEntry>,
    activeVideoId: String?,
    onSelectStream: (String) -> Unit,
    onRemoveStream: (String) -> Unit,
    onClearAll: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(IosSurfacePrimary.copy(alpha = 0.85f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(22.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.History,
                    contentDescription = null,
                    tint = IosCyanTint,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Recent Link Queue",
                    style = MaterialTheme.typography.titleSmall,
                    color = IosLabelPrimary
                )
            }

            Text(
                text = "Clear All",
                style = MaterialTheme.typography.labelLarge,
                color = IosElectricBlue,
                modifier = Modifier
                    .clickable(onClick = onClearAll)
                    .padding(4.dp)
            )
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(recentStreams, key = { it.videoId }) { item ->
                val isPlayingNow = item.videoId == activeVideoId
                Column(
                    modifier = Modifier
                        .width(180.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (isPlayingNow) IosElectricBlue.copy(alpha = 0.18f)
                            else IosSurfaceSecondary.copy(alpha = 0.7f)
                        )
                        .border(
                            width = 1.dp,
                            color = if (isPlayingNow) IosElectricBlue else Color.Transparent,
                            shape = RoundedCornerShape(14.dp)
                        )
                        .clickable { onSelectStream(item.videoId) }
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(IosOledBlack)
                    ) {
                        AsyncImage(
                            model = item.thumbnailUrl,
                            contentDescription = item.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(4.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.Black.copy(alpha = 0.78f))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = formatDuration(item.durationSeconds),
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White
                            )
                        }
                    }

                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = IosLabelPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = item.uploaderName,
                        style = MaterialTheme.typography.labelMedium,
                        color = IosLabelSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentStreamsVerticalList(
    recentStreams: List<RecentStreamEntry>,
    onSelectStream: (String) -> Unit,
    onRemoveStream: (String) -> Unit,
    onClearAll: () -> Unit
) {
    if (recentStreams.isEmpty()) {
        EmptyInspectorState(
            title = "No Recent Streams",
            subtitle = "Any YouTube link you paste or share into HyperTube is saved here for instant replay."
        )
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent & Starter Streams",
                    style = MaterialTheme.typography.titleSmall,
                    color = IosLabelSecondary
                )
                Text(
                    text = "Clear",
                    style = MaterialTheme.typography.labelLarge,
                    color = IosElectricBlue,
                    modifier = Modifier
                        .clickable(onClick = onClearAll)
                        .padding(4.dp)
                )
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(recentStreams, key = { it.videoId }) { entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(IosSurfaceSecondary.copy(alpha = 0.7f))
                            .clickable { onSelectStream(entry.videoId) }
                            .padding(8.dp),
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
                                model = entry.thumbnailUrl,
                                contentDescription = entry.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = entry.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = IosLabelPrimary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = entry.uploaderName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = IosLabelSecondary,
                                maxLines = 1
                            )
                        }

                        IconButton(
                            onClick = { onRemoveStream(entry.videoId) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteOutline,
                                contentDescription = "Remove from history",
                                tint = IosLabelTertiary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyInspectorState(
    title: String,
    subtitle: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.Info,
            contentDescription = null,
            tint = IosLabelTertiary,
            modifier = Modifier.size(32.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = IosLabelPrimary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = IosLabelSecondary
        )
    }
}

@Composable
private fun DeveloperAboutModal(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = IosSurfacePrimary,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp)
                .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(26.dp))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(IosElectricBlue, IosIndigoAccent)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Text(
                    text = "HyperTube",
                    style = MaterialTheme.typography.headlineMedium,
                    color = IosLabelPrimary
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(IosMintGreen.copy(alpha = 0.16f))
                        .border(1.dp, IosMintGreen.copy(alpha = 0.4f), RoundedCornerShape(50))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Lead Developer: shumail0ahmed 💀",
                        style = MaterialTheme.typography.labelLarge,
                        color = IosMintGreen
                    )
                }

                Text(
                    text = "Stripped of legacy bloat and Material You — re-engineered with an iOS Dark Glassmorphic interface, spring physics, and adaptive 16:10 tablet split-stage architecture.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = IosLabelSecondary
                )

                HorizontalDivider(color = IosSeparator)

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FeatureBullet("Direct YouTube URL, Shorts & Share-Intent Playback")
                    FeatureBullet("Dynamic MPEG-DASH 1080p/4K & Audio-Only Engine")
                    FeatureBullet("Adaptive 16:10 Tablet Split-View & Handheld Responsive UI")
                }

                Spacer(modifier = Modifier.height(4.dp))

                IosSpringButton(
                    onClick = onDismiss,
                    backgroundColor = IosElectricBlue,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("close_about_dialog_button")
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
}

@Composable
private fun FeatureBullet(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.CheckCircle,
            contentDescription = null,
            tint = IosElectricBlue,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = IosLabelPrimary
        )
    }
}

@Composable
fun IosSpringButton(
    onClick: () -> Unit,
    backgroundColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "ios_btn_scale"
    )

    Row(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(14.dp))
            .background(backgroundColor)
            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        content()
    }
}

private fun readClipboardText(context: Context): String? {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    val item = clipboard?.primaryClip?.getItemAt(0)
    return item?.coerceToText(context)?.toString()?.trim()
}

private fun shareStreamLink(context: Context, stream: HyperStreamData) {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, stream.title)
        putExtra(Intent.EXTRA_TEXT, "https://youtu.be/${stream.videoId}")
    }
    context.startActivity(Intent.createChooser(shareIntent, "Share YouTube Link"))
}
