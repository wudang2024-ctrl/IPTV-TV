package com.example

import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.foundation.focusable
import androidx.compose.ui.text.style.TextOverflow
import android.view.KeyEvent
import kotlinx.coroutines.delay
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.Channel
import com.example.ui.components.*
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.IptvViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: IptvViewModel = viewModel()
            val themeMode by viewModel.themeMode.collectAsState()

            MyApplicationTheme(darkTheme = themeMode == "Dark") {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    IptvMainScreen(viewModel = viewModel, onEnterPiP = { triggerPictureInPicture() })
                }
            }
        }
    }

    private fun triggerPictureInPicture() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            enterPictureInPictureMode(params)
        }
    }
}

@Composable
fun IptvMainScreen(
    viewModel: IptvViewModel,
    onEnterPiP: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val isTabletOrTv = configuration.screenWidthDp >= 720 || configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val channels by viewModel.channels.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val selectedChannel by viewModel.selectedChannel.collectAsState()
    val currentProgram by viewModel.currentProgram.collectAsState()
    val isProxyRunning by viewModel.isProxyRunning.collectAsState()
    val proxyPort by viewModel.proxyPort.collectAsState()
    val bufferMs by viewModel.bufferMs.collectAsState()
    val playbackAspect by viewModel.playbackAspect.collectAsState()

    var activeTab by remember { mutableStateOf("播放") }

    var isFullscreen by remember { mutableStateOf(false) }
    var showChannelOverlay by remember { mutableStateOf(false) }
    var showBriefChannelBanner by remember { mutableStateOf(false) }

    val playerFocusRequester = remember { FocusRequester() }
    val overlayFocusRequester = remember { FocusRequester() }

    // Auto-hide the transient channel banner
    LaunchedEffect(selectedChannel) {
        if (selectedChannel != null) {
            showBriefChannelBanner = true
            delay(3000)
            showBriefChannelBanner = false
        }
    }

    // Manage focus transfer when fullscreen is entered or overlay is toggled
    LaunchedEffect(isFullscreen, showChannelOverlay) {
        if (isFullscreen) {
            if (showChannelOverlay) {
                overlayFocusRequester.requestFocus()
            } else {
                playerFocusRequester.requestFocus()
            }
        }
    }

    val handleChannelSelect = { channel: Channel ->
        viewModel.selectChannel(channel)
        if (isTabletOrTv) {
            isFullscreen = true
        }
    }

    // Next/Prev channel actions
    val handleNextChannel = {
        val idx = channels.indexOfFirst { it.id == selectedChannel?.id }
        if (idx != -1 && idx < channels.size - 1) {
            handleChannelSelect(channels[idx + 1])
        } else if (channels.isNotEmpty()) {
            handleChannelSelect(channels.first())
        }
    }

    val handlePrevChannel = {
        val idx = channels.indexOfFirst { it.id == selectedChannel?.id }
        if (idx > 0) {
            handleChannelSelect(channels[idx - 1])
        } else if (channels.isNotEmpty()) {
            handleChannelSelect(channels.last())
        }
    }

    if (isFullscreen && selectedChannel != null) {
        // --- Fullscreen Immersive TV/Mobile Mode ---
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .focusRequester(playerFocusRequester)
                .focusable()
                .onKeyEvent { keyEvent ->
                    if (keyEvent.type == KeyEventType.KeyDown) {
                        when (keyEvent.nativeKeyEvent.keyCode) {
                            KeyEvent.KEYCODE_DPAD_UP -> {
                                handlePrevChannel()
                                true
                            }
                            KeyEvent.KEYCODE_DPAD_DOWN -> {
                                handleNextChannel()
                                true
                            }
                            KeyEvent.KEYCODE_DPAD_LEFT -> {
                                showChannelOverlay = !showChannelOverlay
                                true
                            }
                            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                                showChannelOverlay = !showChannelOverlay
                                true
                            }
                            KeyEvent.KEYCODE_BACK -> {
                                if (showChannelOverlay) {
                                    showChannelOverlay = false
                                    true
                                } else {
                                    isFullscreen = false
                                    true
                                }
                            }
                            else -> false
                        }
                    } else false
                }
        ) {
            VideoPlayer(
                channel = selectedChannel!!,
                currentProgram = currentProgram,
                aspectRatio = playbackAspect,
                bufferMs = bufferMs,
                isProxyRunning = isProxyRunning,
                proxyPort = proxyPort,
                onNextChannel = handleNextChannel,
                onPrevChannel = handlePrevChannel,
                onToggleFullscreen = { isFullscreen = false },
                modifier = Modifier.fillMaxSize(),
                onPlaybackAspectChange = { viewModel.setPlaybackAspect(it) },
                isFullscreen = true
            )

            // Transparent overlay for touch screens
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable {
                        showChannelOverlay = !showChannelOverlay
                    }
            )

            // Exit Fullscreen Floating Button
            IconButton(
                onClick = { isFullscreen = false },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .background(Color.Black.copy(alpha = 0.65f), shape = androidx.compose.foundation.shape.CircleShape)
                    .size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.FullscreenExit,
                    contentDescription = "退出全屏",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            // PiP Floating Button
            IconButton(
                onClick = onEnterPiP,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .background(Color.Black.copy(alpha = 0.65f), shape = androidx.compose.foundation.shape.CircleShape)
                    .size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PictureInPicture,
                    contentDescription = "画中画",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }

            // --- Floating Overlay Channel List ---
            AnimatedVisibility(
                visible = showChannelOverlay,
                enter = slideInHorizontally(initialOffsetX = { -it }) + fadeIn(),
                exit = slideOutHorizontally(targetOffsetX = { -it }) + fadeOut(),
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(300.dp)
                        .background(Color.Black.copy(alpha = 0.9f))
                        .padding(16.dp)
                        .clickable(enabled = false) {}
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Text(
                            "频道列表",
                            color = Color.White,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .focusRequester(overlayFocusRequester)
                                .focusable(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(channels) { ch ->
                                val isCurrent = ch.id == selectedChannel?.id
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else Color.White.copy(alpha = 0.08f)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            handleChannelSelect(ch)
                                            showChannelOverlay = false
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .background(
                                                    if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.12f),
                                                    MaterialTheme.shapes.small
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                ch.channelNo.toString(),
                                                color = if (isCurrent) MaterialTheme.colorScheme.primary else Color.White,
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            ch.name,
                                            color = if (isCurrent) MaterialTheme.colorScheme.primary else Color.White,
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // --- Brief Channel Info Banner ---
            AnimatedVisibility(
                visible = showBriefChannelBanner,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 48.dp)
            ) {
                selectedChannel?.let { ch ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.82f)),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    ch.channelNo.toString(),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    ch.name,
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                if (currentProgram != null) {
                                    Text(
                                        "正在播放: ${currentProgram!!.title}",
                                        color = Color.LightGray,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    } else {
        Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (!isTabletOrTv) {
                NavigationBar(
                    modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                ) {
                    val tabs = listOf(
                        "播放" to Icons.Default.PlayArrow,
                        "节目单" to Icons.Default.DateRange,
                        "订阅源" to Icons.Default.Cloud,
                        "设置" to Icons.Default.Settings
                    )
                    tabs.forEach { (tab, icon) ->
                        NavigationBarItem(
                            selected = activeTab == tab,
                            onClick = { activeTab = tab },
                            icon = { Icon(icon, contentDescription = tab) },
                            label = { Text(tab, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.testTag("nav_tab_$tab")
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        if (isTabletOrTv) {
            // --- Landscape / Tablet / TV canonical side-by-side split layout ---
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
            ) {
                // TV navigation column
                NavigationRail(
                    header = {
                        Icon(
                            Icons.Default.LiveTv,
                            contentDescription = "Logo",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp).padding(bottom = 16.dp)
                        )
                    }
                ) {
                    val tabs = listOf(
                        "播放" to Icons.Default.PlayArrow,
                        "节目单" to Icons.Default.DateRange,
                        "订阅源" to Icons.Default.Cloud,
                        "设置" to Icons.Default.Settings
                    )
                    tabs.forEach { (tab, icon) ->
                        NavigationRailItem(
                            selected = activeTab == tab,
                            onClick = { activeTab = tab },
                            icon = { Icon(icon, contentDescription = tab) },
                            label = { Text(tab) },
                            modifier = Modifier.testTag("tv_nav_tab_$tab")
                        )
                    }
                }

                // Split screen sections
                Row(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Left panel: selectors / channels based on tab
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                    ) {
                        when (activeTab) {
                            "播放" -> ChannelList(viewModel = viewModel, onSelectChannel = handleChannelSelect)
                            "节目单" -> EpgTimelineGrid(viewModel = viewModel, onSelectChannel = handleChannelSelect)
                            "订阅源" -> PlaylistManager(viewModel = viewModel)
                            else -> SettingsPanel(viewModel = viewModel)
                        }
                    }

                    // Right panel: Video Player + EPG synopsis or info block
                    Column(
                        modifier = Modifier
                            .weight(1.3f)
                            .fillMaxHeight()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            if (selectedChannel != null) {
                                VideoPlayer(
                                    channel = selectedChannel!!,
                                    currentProgram = currentProgram,
                                    aspectRatio = playbackAspect,
                                    bufferMs = bufferMs,
                                    isProxyRunning = isProxyRunning,
                                    proxyPort = proxyPort,
                                    onNextChannel = handleNextChannel,
                                    onPrevChannel = handlePrevChannel,
                                    onToggleFullscreen = { isFullscreen = true },
                                    modifier = Modifier.fillMaxSize(),
                                    onPlaybackAspectChange = { viewModel.setPlaybackAspect(it) },
                                    isFullscreen = false
                                )

                                // Enter PiP Button
                                IconButton(
                                    onClick = onEnterPiP,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(8.dp)
                                        .background(Color.Black.copy(alpha = 0.5f), MaterialTheme.shapes.small)
                                ) {
                                    Icon(Icons.Default.PictureInPicture, contentDescription = "画中画", tint = Color.White)
                                }
                            } else {
                                // Splash display
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(Icons.Default.LiveTv, contentDescription = "选择", modifier = Modifier.size(64.dp), tint = Color.DarkGray)
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text("请选择左侧列表中的电视频道进行播放", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                                    }
                                }
                            }
                        }

                        // Right-bottom: EPG Synopsis card
                        if (selectedChannel != null) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                                    .padding(8.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(selectedChannel!!.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    if (currentProgram != null) {
                                        Text("正在播送: ${currentProgram!!.title}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(currentProgram!!.description.ifEmpty { "暂无节目简介。" }, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                    } else {
                                        Text("无即时节目单。点击左侧‘节目单’可以进行横向时间回看。", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // --- Mobile Portrait Screen Layout ---
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .windowInsetsPadding(WindowInsets.statusBars)
            ) {
                // Top portion: Video Player
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                ) {
                    if (selectedChannel != null) {
                        VideoPlayer(
                            channel = selectedChannel!!,
                            currentProgram = currentProgram,
                            aspectRatio = playbackAspect,
                            bufferMs = bufferMs,
                            isProxyRunning = isProxyRunning,
                            proxyPort = proxyPort,
                            onNextChannel = handleNextChannel,
                            onPrevChannel = handlePrevChannel,
                            onToggleFullscreen = { isFullscreen = true },
                            modifier = Modifier.fillMaxSize(),
                            onPlaybackAspectChange = { viewModel.setPlaybackAspect(it) },
                            isFullscreen = false
                        )

                        // Enter PiP Button
                        IconButton(
                            onClick = onEnterPiP,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .background(Color.Black.copy(alpha = 0.5f), MaterialTheme.shapes.small)
                        ) {
                            Icon(Icons.Default.PictureInPicture, contentDescription = "画中画", tint = Color.White)
                        }
                    } else {
                        // Splash display
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.LiveTv, contentDescription = "播放厅", modifier = Modifier.size(48.dp), tint = Color.DarkGray)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("星辰IPTV - 暂无播放流", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                            }
                        }
                    }
                }

                // Bottom portion: Tab screens
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    when (activeTab) {
                        "播放" -> ChannelList(viewModel = viewModel, onSelectChannel = handleChannelSelect)
                        "节目单" -> EpgTimelineGrid(viewModel = viewModel, onSelectChannel = handleChannelSelect)
                        "订阅源" -> PlaylistManager(viewModel = viewModel)
                        else -> SettingsPanel(viewModel = viewModel)
                    }
                }
            }
        }
    }

    }
}
