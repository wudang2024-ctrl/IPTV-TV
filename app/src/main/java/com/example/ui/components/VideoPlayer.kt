@file:OptIn(androidx.media3.common.util.UnstableApi::class)
package com.example.ui.components

import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.util.Log
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.rtmp.RtmpDataSource
import androidx.media3.datasource.UdpDataSource
import androidx.media3.ui.PlayerView
import com.example.data.Channel
import com.example.data.EpgProgram
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class AudioTrackInfo(
    val groupIndex: Int,
    val trackIndex: Int,
    val language: String?,
    val label: String?,
    val isSelected: Boolean,
    val trackGroup: androidx.media3.common.TrackGroup? = null,
    val mimeType: String? = null,
    val sampleRate: Int = -1,
    val channelCount: Int = -1
)

@Composable
fun VideoPlayer(
    channel: Channel,
    currentProgram: EpgProgram?,
    aspectRatio: String, // "Original", "Stretch", "16:9", "4:3", "Zoom"
    bufferMs: Int,
    isProxyRunning: Boolean,
    proxyPort: Int,
    onNextChannel: () -> Unit,
    onPrevChannel: () -> Unit,
    viewModel: com.example.viewmodel.IptvViewModel,
    modifier: Modifier = Modifier,
    onToggleFullscreen: (() -> Unit)? = null,
    multicastMode: String = "InternalProxy",
    externalProxyUrl: String = "http://192.168.31.1:7088",
    onPlaybackAspectChange: ((String) -> Unit)? = null,
    isFullscreen: Boolean = false,
    decoderKernel: String = "VLC"
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // --- Controller HUD Visibility & Playback States ---
    var showControllers by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(true) }
    var playerError by remember { mutableStateOf<String?>(null) }
    var isBuffering by remember { mutableStateOf(false) }
    var forceHlsForCurrentUrl by remember { mutableStateOf(false) }

    // --- LibVLC Instantiation ---
    val libVLC = remember {
        val args = ArrayList<String>().apply {
            add("--rtsp-tcp")
            add("--audio-time-stretch")
            add("--no-drop-late-frames")
            add("--no-skip-frames")
            add("-vvv")
        }
        LibVLC(context, args)
    }

    val vlcPlayer = remember(libVLC) {
        MediaPlayer(libVLC)
    }

    // --- AudioManager & Brightness Controls ---
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = viewModel.maxVolume
    val currentVolume by viewModel.currentVolume.collectAsState()
    val showVolumeOverlay by viewModel.showVolumeOverlay.collectAsState()

    val activity = context as? Activity
    var currentBrightness by remember {
        mutableStateOf(activity?.window?.attributes?.screenBrightness ?: 0.5f)
    }

    // Gesture indicator values
    var showBrightnessOverlay by remember { mutableStateOf(false) }

    // --- Persisted Player Settings (Decoder & Audio Delay) ---
    val sharedPrefs = remember { context.getSharedPreferences("iptv_player_settings", Context.MODE_PRIVATE) }
    var useHardwareDecoder by remember {
        mutableStateOf(sharedPrefs.getBoolean("use_hardware_decoder", true))
    }
    var audioDelayMs by remember {
        mutableStateOf(sharedPrefs.getInt("audio_delay_ms", 0))
    }
    var playerEngine by remember {
        mutableStateOf(sharedPrefs.getString("player_engine", "exoplayer") ?: "exoplayer")
    }

    // --- Audio Processor for delay/offset adjustment ---
    val delayAudioProcessor = remember { DelayAudioProcessor() }

    LaunchedEffect(audioDelayMs) {
        delayAudioProcessor.setDelayMs(audioDelayMs)
        try {
            vlcPlayer.setAudioDelay(audioDelayMs * 1000L)
        } catch (e: Exception) {
            Log.e("VideoPlayer", "Failed to set VLC audio delay", e)
        }
    }

    // --- Dynamic Track State ---
    var audioTracks by remember { mutableStateOf<List<AudioTrackInfo>>(emptyList()) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showScreenSettingsPanel by remember { mutableStateOf(false) }

    // --- Additional Player Settings & Quick Controls State ---
    var reloadTrigger by remember { mutableStateOf(0) }
    var overlayMessage by remember { mutableStateOf<String?>(null) }
    var currentAspectRatio by remember(aspectRatio) { mutableStateOf(aspectRatio) }

    LaunchedEffect(isFullscreen) {
        if (isFullscreen) {
            currentAspectRatio = "Stretch"
            onPlaybackAspectChange?.invoke("Stretch")
        } else {
            currentAspectRatio = "Original"
            onPlaybackAspectChange?.invoke("Original")
        }
    }

    LaunchedEffect(overlayMessage) {
        if (overlayMessage != null) {
            kotlinx.coroutines.delay(2000)
            overlayMessage = null
        }
    }

    // --- ExoPlayer Instantiation ---
    val exoPlayer = remember(useHardwareDecoder, bufferMs) {
        val mediaCodecSelector = if (useHardwareDecoder) {
            androidx.media3.exoplayer.mediacodec.MediaCodecSelector.DEFAULT
        } else {
            androidx.media3.exoplayer.mediacodec.MediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
                val decoders = androidx.media3.exoplayer.mediacodec.MediaCodecUtil.getDecoderInfos(
                    mimeType,
                    requiresSecureDecoder,
                    requiresTunnelingDecoder
                )
                decoders.sortedWith { d1, d2 ->
                    val isSw1 = d1.name.lowercase().contains("google") || d1.name.lowercase().contains("sw")
                    val isSw2 = d2.name.lowercase().contains("google") || d2.name.lowercase().contains("sw")
                    when {
                        isSw1 && !isSw2 -> -1
                        !isSw1 && isSw2 -> 1
                        else -> 0
                    }
                }
            }
        }

        val renderersFactory = object : androidx.media3.exoplayer.DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): androidx.media3.exoplayer.audio.AudioSink? {
                // Configure high-performance audio sink with explicit support for:
                // - MPEG Audio Layer 1/2/3 (mp3)
                // - AAC (LC)
                // - AC3 (A/52 / Dolby Digital)
                // - EAC3 (Dolby Digital Plus)
                // - DTS
                // - PCM
                return androidx.media3.exoplayer.audio.DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(true) // Force 32-bit float output for high-res lossless PCM / Dolby
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .setAudioProcessors(arrayOf(delayAudioProcessor))
                    .build()
            }
        }.apply {
            // Enable software fallback extension decoders (FFmpeg, FLAC, Opus, etc.)
            setExtensionRendererMode(androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
        }
        renderersFactory.setMediaCodecSelector(mediaCodecSelector)

        val minBuffer = maxOf(bufferMs, 2000)
        val maxBuffer = maxOf(bufferMs * 3, 8000)
        val playbackStart = maxOf(bufferMs / 2, 800)
        val playbackRebuffer = maxOf(bufferMs, 1200)

        val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                minBuffer,
                maxBuffer,
                playbackStart,
                playbackRebuffer
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        ExoPlayer.Builder(context, renderersFactory)
            .setLoadControl(loadControl)
            .build().apply {
                repeatMode = Player.REPEAT_MODE_OFF
                playWhenReady = true
            }
    }

    DisposableEffect(libVLC) {
        onDispose {
            libVLC.release()
        }
    }

    DisposableEffect(vlcPlayer) {
        val listener = MediaPlayer.EventListener { event ->
            when (event.type) {
                MediaPlayer.Event.Buffering -> {
                    isBuffering = event.buffering < 100f
                }
                MediaPlayer.Event.Playing -> {
                    isPlaying = true
                    isBuffering = false
                    playerError = null
                    
                    // Retrieve VLC audio tracks
                    val list = mutableListOf<AudioTrackInfo>()
                    val currentTrackId = vlcPlayer.audioTrack
                    vlcPlayer.audioTracks?.forEach { track ->
                        list.add(
                            AudioTrackInfo(
                                groupIndex = track.id,
                                trackIndex = 0,
                                language = "",
                                label = track.name ?: "Track ${track.id}",
                                isSelected = track.id == currentTrackId,
                                trackGroup = null,
                                mimeType = "VLC",
                                sampleRate = 0,
                                channelCount = 0
                            )
                        )
                    }
                    audioTracks = list
                }
                MediaPlayer.Event.Paused -> {
                    isPlaying = false
                }
                MediaPlayer.Event.Stopped -> {
                    isPlaying = false
                }
                MediaPlayer.Event.EncounteredError -> {
                    playerError = "播放错误 (VLC)"
                    isBuffering = false
                }
            }
        }
        vlcPlayer.setEventListener(listener)
        onDispose {
            vlcPlayer.setEventListener(null)
            vlcPlayer.stop()
            vlcPlayer.release()
        }
    }



    // Reset fallback on channel change
    LaunchedEffect(channel) {
        forceHlsForCurrentUrl = false
    }

    // Auto-hide controller HUD
    LaunchedEffect(showControllers) {
        if (showControllers) {
            delay(5000)
            showControllers = false
        } else {
            showScreenSettingsPanel = false
        }
    }

    // Track stream error, buffer state and audio tracks
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = state == Player.STATE_BUFFERING
                isPlaying = state == Player.STATE_READY && exoPlayer.playWhenReady
                if (state == Player.STATE_READY) {
                    playerError = null
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e("VideoPlayer", "Playback error: ${error.message}", error)
                val errorMessage = error.localizedMessage ?: "连接超时"
                val url = channel.url
                val isDynamicOrPhp = url.contains(".php", ignoreCase = true) || url.contains("?")
                
                if (errorMessage.contains("audio/mpeg-L2", ignoreCase = true) || 
                    errorMessage.contains("mpeg-L2", ignoreCase = true) || 
                    errorMessage.contains("audio/mpeg", ignoreCase = true) ||
                    error.message?.contains("audio/mpeg-L2", ignoreCase = true) == true ||
                    error.message?.contains("mpeg-L2", ignoreCase = true) == true
                ) {
                    playerError = "播放失败：该频道音频采用国标组播/广播常用编码（MPEG-L2 / MP2）。\n" +
                                 "由于安卓原生内核（ExoPlayer）不支持此格式硬解，请在「设置」中将「解码内核」切换为 VLC 或 IJKPlayer，或在代理服务（如 udpxy）中将音频转码为 AAC。"
                } else if (isDynamicOrPhp && !forceHlsForCurrentUrl) {
                    Log.d("VideoPlayer", "Dynamic/PHP stream failed. Retrying with forced HLS decoding...")
                    playerError = "尝试切换至 HLS 协议解码中..."
                    forceHlsForCurrentUrl = true
                } else {
                    playerError = "播放失败: $errorMessage"
                }
            }

            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                val list = mutableListOf<AudioTrackInfo>()
                val groups = tracks.groups
                var hasUnsupportedSelectedAudio = false
                var fallbackTrack: AudioTrackInfo? = null

                for (groupIndex in groups.indices) {
                    val group = groups[groupIndex]
                    if (group.type == androidx.media3.common.C.TRACK_TYPE_AUDIO) {
                        for (trackIndex in 0 until group.length) {
                            val format = group.getTrackFormat(trackIndex)
                            val isSupported = group.isTrackSupported(trackIndex)
                            val isSelected = group.isTrackSelected(trackIndex)
                            val mime = format.sampleMimeType ?: ""
                            
                            val trackInfo = AudioTrackInfo(
                                groupIndex = groupIndex,
                                trackIndex = trackIndex,
                                language = format.language,
                                label = format.label ?: format.id,
                                isSelected = isSelected,
                                trackGroup = group.mediaTrackGroup,
                                mimeType = mime,
                                sampleRate = format.sampleRate,
                                channelCount = format.channelCount
                            )
                            list.add(trackInfo)
                            
                            // Check if this is the currently selected audio track and whether it is problematic
                            if (isSelected) {
                                val isMimeProblematic = mime.contains("dra", ignoreCase = true) || 
                                                        mime.contains("ac3", ignoreCase = true) || 
                                                        mime.contains("eac3", ignoreCase = true) || 
                                                        mime.contains("dts", ignoreCase = true) ||
                                                        format.label?.contains("dra", ignoreCase = true) == true
                                if (!isSupported || isMimeProblematic) {
                                    hasUnsupportedSelectedAudio = true
                                    Log.d("VideoPlayer", "Selected track is unsupported or problematic: $mime (supported: $isSupported)")
                                }
                            }
                            
                            // Identify a highly-compatible track as a potential fallback (must be supported)
                            if (isSupported && (
                                mime.contains("mp4a", ignoreCase = true) || 
                                mime.contains("aac", ignoreCase = true) || 
                                mime.contains("mpeg", ignoreCase = true) || 
                                mime.contains("x-mpeg", ignoreCase = true) || 
                                mime.contains("raw", ignoreCase = true)
                            )) {
                                if (fallbackTrack == null) {
                                    fallbackTrack = trackInfo
                                }
                            }
                        }
                    }
                }
                audioTracks = list

                // Automatically switch to compatible fallback audio if current is unsupported/problematic
                if (hasUnsupportedSelectedAudio && fallbackTrack != null) {
                    Log.d("VideoPlayer", "Detected DRA or unsupported E-AC-3/AC-3/DTS track default on device. Automatically switching to compatible audio track: ${fallbackTrack.mimeType}")
                    try {
                        val parameters = exoPlayer.trackSelectionParameters.buildUpon()
                            .setOverrideForType(
                                androidx.media3.common.TrackSelectionOverride(
                                    fallbackTrack.trackGroup!!,
                                    listOf(fallbackTrack.trackIndex)
                                )
                            )
                            .build()
                        exoPlayer.trackSelectionParameters = parameters
                    } catch (e: Exception) {
                        Log.e("VideoPlayer", "Auto track selection fallback failed", e)
                    }
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // --- Prepare Playback Stream ---
    LaunchedEffect(channel, isProxyRunning, proxyPort, forceHlsForCurrentUrl, playerEngine, multicastMode, externalProxyUrl, reloadTrigger, decoderKernel) {
        playerError = null
        if (playerEngine == "html5_webview") {
            try {
                exoPlayer.stop()
                exoPlayer.clearMediaItems()
                vlcPlayer.stop()
            } catch (e: Exception) {
                Log.e("VideoPlayer", "Error stopping players for WebView", e)
            }
            return@LaunchedEffect
        }
        val isRawMulticast = channel.url.startsWith("udp://", ignoreCase = true) || channel.url.startsWith("rtp://", ignoreCase = true)
        val playUrl = if (isRawMulticast) {
            when (multicastMode) {
                "ExternalProxy" -> {
                    val cleanAddr = channel.url.replace("udp://@", "").replace("udp://", "").replace("rtp://@", "").replace("rtp://", "")
                    val base = externalProxyUrl.removeSuffix("/")
                    "$base/udp/$cleanAddr"
                }
                "InternalProxy" -> {
                    if (isProxyRunning) {
                        val cleanAddr = channel.url.replace("udp://@", "").replace("udp://", "").replace("rtp://@", "").replace("rtp://", "")
                        "http://localhost:$proxyPort/stream?addr=$cleanAddr"
                    } else {
                        channel.url
                    }
                }
                else -> {
                    channel.url
                }
            }
        } else {
            channel.url
        }

        try {
            if (decoderKernel == "VLC") {
                exoPlayer.stop()
                exoPlayer.clearMediaItems()
                
                vlcPlayer.stop()
                
                val resolvedStream = if (!channel.isMulticast && (playUrl.contains(".php", ignoreCase = true) || playUrl.contains("?"))) {
                    playerError = "解析动态播放地址中..."
                    resolveStreamUrl(playUrl)
                } else {
                    ResolvedStream(playUrl, null)
                }
                
                playerError = null
                val media = Media(libVLC, android.net.Uri.parse(resolvedStream.url)).apply {
                    addOption(":network-caching=$bufferMs")
                    addOption(":clock-jitter=0")
                    addOption(":clock-synchro=0")
                }
                vlcPlayer.media = media
                media.release()
                vlcPlayer.play()
            } else {
                vlcPlayer.stop()
                
                exoPlayer.stop()
                exoPlayer.clearMediaItems()
                
                val resolvedStream = if (!channel.isMulticast && (playUrl.contains(".php", ignoreCase = true) || playUrl.contains("?"))) {
                    playerError = "解析动态播放地址中..."
                    resolveStreamUrl(playUrl)
                } else {
                    ResolvedStream(playUrl, null)
                }
                
                playerError = null
                val mediaSource = createMediaSource(context, resolvedStream, forceHls = forceHlsForCurrentUrl)
                exoPlayer.setMediaSource(mediaSource)
                exoPlayer.prepare()
                exoPlayer.play()
            }
        } catch (e: Exception) {
            playerError = "无法加载流: ${e.message}"
        }
    }

    // Aspect ratio mappings
    val resizeMode = when (currentAspectRatio) {
        "Stretch" -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
        "16:9" -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
        "4:3" -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
        "Zoom" -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        else -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
    }

    LaunchedEffect(currentAspectRatio, decoderKernel) {
        if (decoderKernel == "VLC") {
            try {
                when (currentAspectRatio) {
                    "Original" -> {
                        vlcPlayer.aspectRatio = null
                        vlcPlayer.scale = 0f
                    }
                    "Stretch" -> {
                        vlcPlayer.aspectRatio = "0:0"
                        vlcPlayer.scale = 0f
                    }
                    "16:9" -> {
                        vlcPlayer.aspectRatio = "16:9"
                        vlcPlayer.scale = 0f
                    }
                    "4:3" -> {
                        vlcPlayer.aspectRatio = "4:3"
                        vlcPlayer.scale = 0f
                    }
                    "Zoom" -> {
                        vlcPlayer.aspectRatio = null
                        vlcPlayer.scale = 1.3f
                    }
                    else -> {
                        vlcPlayer.aspectRatio = null
                        vlcPlayer.scale = 0f
                    }
                }
            } catch (e: Exception) {
                Log.e("VideoPlayer", "Error applying VLC aspect ratio", e)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    Box(
        modifier = modifier
            .background(Color.Black)
            .testTag("video_player_container")
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        showControllers = true
                    },
                    onDragEnd = {
                        showBrightnessOverlay = false
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val screenWidth = size.width
                        val isRightSide = change.position.x > (screenWidth / 2)

                        if (isRightSide) {
                            // Volume control
                            val volumeDelta = -(dragAmount.y / 25f).toInt()
                            if (volumeDelta != 0) {
                                viewModel.adjustVolume(volumeDelta)
                            }
                        } else {
                            // Brightness control
                            val brightnessDelta = -dragAmount.y / 800f
                            val targetBrightness = (currentBrightness + brightnessDelta).coerceIn(0.01f, 1.0f)
                            activity?.let {
                                val lp = it.window.attributes
                                lp.screenBrightness = targetBrightness
                                it.window.attributes = lp
                            }
                            currentBrightness = targetBrightness
                            showBrightnessOverlay = true
                        }
                    }
                )
            }
            .clickable {
                showControllers = !showControllers
            }
    ) {
        // --- Playback View Wrapper (ExoPlayer vs HTML5 WebView) ---
        if (playerEngine == "html5_webview") {
            val webViewUrl = remember(channel, isProxyRunning, proxyPort, multicastMode, externalProxyUrl) {
                val isRawMulticast = channel.url.startsWith("udp://", ignoreCase = true) || channel.url.startsWith("rtp://", ignoreCase = true)
                if (isRawMulticast) {
                    when (multicastMode) {
                        "ExternalProxy" -> {
                            val cleanAddr = channel.url.replace("udp://@", "").replace("udp://", "").replace("rtp://@", "").replace("rtp://", "")
                            val base = externalProxyUrl.removeSuffix("/")
                            "$base/udp/$cleanAddr"
                        }
                        "InternalProxy" -> {
                            if (isProxyRunning) {
                                val cleanAddr = channel.url.replace("udp://@", "").replace("udp://", "").replace("rtp://@", "").replace("rtp://", "")
                                "http://localhost:$proxyPort/stream?addr=$cleanAddr"
                            } else {
                                channel.url
                            }
                        }
                        else -> {
                            channel.url
                        }
                    }
                } else {
                    channel.url
                }
            }

            val htmlContent = remember(webViewUrl) {
                """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                    <style>
                        body, html { margin: 0; padding: 0; width: 100%; height: 100%; background-color: black; overflow: hidden; display: flex; align-items: center; justify-content: center; }
                        video { width: 100%; height: 100%; object-fit: contain; }
                    </style>
                    <script src="https://cdn.jsdelivr.net/npm/hls.js@latest"></script>
                </head>
                <body>
                    <video id="video" autoplay controls playsinline webkit-playsinline></video>
                    <script>
                        const video = document.getElementById('video');
                        const videoSrc = "$webViewUrl";
                        
                        if (Hls.isSupported() && (videoSrc.indexOf('.m3u8') !== -1 || videoSrc.indexOf('hls') !== -1)) {
                            const hls = new Hls({
                                maxMaxBufferLength: 10,
                                enableWorker: true,
                                lowLatencyMode: true
                            });
                            hls.loadSource(videoSrc);
                            hls.attachMedia(video);
                            hls.on(Hls.Events.MANIFEST_PARSED, function() {
                                video.play();
                            });
                            hls.on(Hls.Events.ERROR, function(event, data) {
                                console.error("HLS.js error:", data);
                                if (data.fatal) {
                                    switch(data.type) {
                                        case Hls.ErrorTypes.NETWORK_ERROR:
                                            hls.startLoad();
                                            break;
                                        case Hls.ErrorTypes.MEDIA_ERROR:
                                            hls.recoverMediaError();
                                            break;
                                        default:
                                            break;
                                    }
                                }
                            });
                        } else {
                            video.src = videoSrc;
                            video.addEventListener('loadedmetadata', function() {
                                video.play();
                            });
                        }
                    </script>
                </body>
                </html>
                """.trimIndent()
            }

            AndroidView(
                factory = { ctx ->
                    android.webkit.WebView(ctx).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            mediaPlaybackRequiresUserGesture = false
                            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            userAgentString = "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0.0.0 Mobile Safari/537.36"
                        }
                        webViewClient = object : android.webkit.WebViewClient() {
                            override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                Log.d("VideoPlayer", "WebView page loaded: $url")
                            }
                        }
                        webChromeClient = android.webkit.WebChromeClient()
                        setBackgroundColor(android.graphics.Color.BLACK)
                    }
                },
                update = { webView ->
                    val isDirect = webViewUrl.contains(".m3u8") || 
                                   webViewUrl.contains(".ts") || 
                                   webViewUrl.contains(".mp4") || 
                                   webViewUrl.contains(".mkv") || 
                                   webViewUrl.contains("stream") ||
                                   webViewUrl.startsWith("udp://") || 
                                   webViewUrl.startsWith("rtp://")
                    if (isDirect) {
                        webView.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
                    } else {
                        webView.loadUrl(webViewUrl)
                    }
                },
                onRelease = { webView ->
                    try {
                        webView.stopLoading()
                        webView.loadUrl("about:blank")
                        webView.destroy()
                    } catch (e: Exception) {
                        Log.e("VideoPlayer", "Error releasing WebView", e)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else if (decoderKernel == "VLC") {
            AndroidView(
                factory = { context ->
                    VLCVideoLayout(context).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        try {
                            vlcPlayer.attachViews(this, null, false, false)
                        } catch (e: Exception) {
                            Log.e("VideoPlayer", "Error attaching VLC views", e)
                        }
                    }
                },
                update = { view ->
                    // Aspect ratio is dynamically managed via LaunchedEffect
                },
                onRelease = { view ->
                    try {
                        vlcPlayer.detachViews()
                    } catch (e: Exception) {
                        Log.e("VideoPlayer", "Error detaching VLC views on release", e)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            AndroidView(
                factory = { context ->
                    PlayerView(context).apply {
                        useController = false
                        this.resizeMode = resizeMode
                        player = exoPlayer
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                update = { view ->
                    view.resizeMode = resizeMode
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // --- Custom Touch Swipe / Channel Gestures Box ---
        // Slide left or right triggers channel shifts
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { onPrevChannel() },
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.4f), MaterialTheme.shapes.small)
                    .testTag("prev_channel_button")
            ) {
                Icon(Icons.Default.ChevronLeft, contentDescription = "上一个频道", tint = Color.White)
            }
            IconButton(
                onClick = { onNextChannel() },
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.4f), MaterialTheme.shapes.small)
                    .testTag("next_channel_button")
            ) {
                Icon(Icons.Default.ChevronRight, contentDescription = "下一个频道", tint = Color.White)
            }
        }

        // --- Gestures Overlays ---
        AnimatedVisibility(
            visible = showVolumeOverlay,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.75f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (currentVolume == 0) Icons.Default.VolumeMute else Icons.Default.VolumeUp,
                        contentDescription = "音量",
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "音量: ${(currentVolume * 100 / maxVolume)}%",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = showBrightnessOverlay,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.75f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.BrightnessMedium, contentDescription = "亮度", tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "亮度: ${(currentBrightness * 100).toInt()}%",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // --- Buffering Indicator ---
        if (isBuffering) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(48.dp)
            )
        }

        // --- Error state ---
        playerError?.let { err ->
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.85f), shape = MaterialTheme.shapes.medium)
                    .padding(24.dp)
                    .widthIn(max = 520.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.Error, 
                    contentDescription = "播放错误", 
                    tint = MaterialTheme.colorScheme.error, 
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = err, 
                    color = Color.White, 
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    lineHeight = androidx.compose.ui.unit.TextUnit.Unspecified
                )
            }
        }

        // --- Controller Overlay HUD ---
        AnimatedVisibility(
            visible = showControllers,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
            ) {
                // Top header: channel logo, channel name, category
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (channel.isMulticast) {
                                Badge(
                                    containerColor = Color(0xFF4CAF50),
                                    contentColor = Color.White,
                                    modifier = Modifier.padding(end = 8.dp)
                                ) {
                                    Text("组播")
                                }
                            }
                            Text(
                                text = channel.name,
                                color = Color.White,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "分类: ${channel.groupTitle}",
                            color = Color.LightGray,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    // Top control buttons (PiP, Lock, Settings, etc.)
                    Row {
                        if (onToggleFullscreen != null) {
                            IconButton(onClick = {
                                showScreenSettingsPanel = !showScreenSettingsPanel
                            }) {
                                Icon(
                                    imageVector = Icons.Default.AspectRatio,
                                    contentDescription = "屏幕比例与全屏",
                                    tint = if (showScreenSettingsPanel) MaterialTheme.colorScheme.primary else Color.White
                                )
                            }
                        }
                        IconButton(onClick = {
                            showSettingsDialog = true
                        }) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = "播放高级设置",
                                tint = Color.White
                            )
                        }
                        IconButton(onClick = {
                            if (decoderKernel == "VLC") {
                                if (isPlaying) {
                                    vlcPlayer.pause()
                                } else {
                                    vlcPlayer.play()
                                }
                            } else {
                                exoPlayer.playWhenReady = !isPlaying
                            }
                            isPlaying = !isPlaying
                        }) {
                            Icon(
                                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "播放暂停",
                                tint = Color.White
                            )
                        }
                    }
                }

                // Bottom HUD: EPG info bar & status indicator
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(Color.Black.copy(alpha = 0.75f))
                        .padding(16.dp)
                ) {
                    if (currentProgram != null) {
                        Text(
                            text = "正在播放: ${currentProgram.title}",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (currentProgram.description.isNotEmpty()) {
                            Text(
                                text = currentProgram.description,
                                color = Color.LightGray,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    } else {
                        Text(
                            text = "暂无节目单信息",
                            color = Color.Gray,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    // Progress slider or static track line
                    LinearProgressIndicator(
                        progress = { 0.35f }, // Mock progress line for live
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.Gray
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "时区: GMT+8 | 缓冲区: ${bufferMs}ms",
                            color = Color.Gray,
                            style = MaterialTheme.typography.labelSmall
                        )
                        Text(
                            text = if (isProxyRunning) "本地组播代理已开启 (Port: $proxyPort)" else "直接组播接收",
                            color = Color.LightGray,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                // Quick Action Floating Bar
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 120.dp) // Lifted up to sit gracefully above the bottom HUD bar!
                        .background(Color.Black.copy(alpha = 0.75f), shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 1. Refresh Stream
                    IconButton(
                        onClick = {
                            reloadTrigger++
                            overlayMessage = "正在重新连接直播源..."
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "重新加载",
                            tint = Color.White
                        )
                    }
                    
                    // 2. Unified Screen & Scale Panel Trigger
                    IconButton(
                        onClick = {
                            showScreenSettingsPanel = !showScreenSettingsPanel
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.AspectRatio,
                            contentDescription = "屏幕比例与全屏",
                            tint = if (showScreenSettingsPanel) MaterialTheme.colorScheme.primary else Color.White
                        )
                    }
                    
                    // 3. Audio Tracks Quick Dialog Trigger
                    IconButton(
                        onClick = {
                            showSettingsDialog = true
                            overlayMessage = "已打开播放高级设置"
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Audiotrack,
                            contentDescription = "音频轨道",
                            tint = Color.White
                        )
                    }
                    
                    // 4. Decoder Quick Toggle (HW / SW)
                    IconButton(
                        onClick = {
                            useHardwareDecoder = !useHardwareDecoder
                            sharedPrefs.edit().putBoolean("use_hardware_decoder", useHardwareDecoder).apply()
                            overlayMessage = if (useHardwareDecoder) "已切换为「硬解」视频硬件加速" else "已切换为「软解」兼容视频解码"
                        }
                    ) {
                        Icon(
                            imageVector = if (useHardwareDecoder) Icons.Default.DeveloperMode else Icons.Default.ToggleOff,
                            contentDescription = "解码方式",
                            tint = if (useHardwareDecoder) MaterialTheme.colorScheme.primary else Color.LightGray
                        )
                    }
                }

                // Consolidated Screen and Zoom Settings Panel
                if (showScreenSettingsPanel) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 180.dp) // Sits perfectly above the Quick Action Bar (which is padding bottom 120.dp)
                            .widthIn(max = 420.dp)
                            .background(Color.Black.copy(alpha = 0.85f), shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                            .clickable(enabled = false) {} // Avoid click-throughs to player background controls
                            .padding(16.dp)
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Header
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(
                                        imageVector = Icons.Default.AspectRatio,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = "屏幕与缩放设置",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                                IconButton(
                                    onClick = { showScreenSettingsPanel = false },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "关闭",
                                        tint = Color.Gray,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            // Fullscreen Control Row
                            if (onToggleFullscreen != null) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onToggleFullscreen() }
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "全屏播放模式",
                                            color = Color.White,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            text = if (isFullscreen) "当前处于全屏模式" else "当前处于窗口模式",
                                            color = Color.Gray,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                    Switch(
                                        checked = isFullscreen,
                                        onCheckedChange = { onToggleFullscreen() },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                                            checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                        )
                                    )
                                }
                                HorizontalDivider(color = Color.White.copy(alpha = 0.15f), thickness = 0.5.dp)
                            }

                            // Aspect Ratio Options
                            Text(
                                text = "画面比例与缩放",
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val aspects = listOf(
                                    "Original" to "原始比例",
                                    "Stretch" to "拉伸全屏",
                                    "16:9" to "16:9",
                                    "4:3" to "4:3",
                                    "Zoom" to "裁剪放大"
                                )
                                aspects.forEach { (aspect, label) ->
                                    val isSelected = currentAspectRatio == aspect
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .background(
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.1f),
                                                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                                            )
                                            .clickable {
                                                currentAspectRatio = aspect
                                                onPlaybackAspectChange?.invoke(aspect)
                                                overlayMessage = "画面比例: $label"
                                            }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            color = if (isSelected) Color.White else Color.LightGray,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- Custom Toast / Banner Notifications ---
        AnimatedVisibility(
            visible = overlayMessage != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
            modifier = Modifier
                .align(Alignment.Center)
                .padding(bottom = 64.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.85f)),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = overlayMessage ?: "",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }

    // --- Advanced Playback Settings Dialog ---
    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("高级播放设置", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 1. Audio latency/delay offset
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("音频偏移调整 (Audio Delay)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("${audioDelayMs} 毫秒 (ms)", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            TextButton(
                                onClick = {
                                    audioDelayMs = (audioDelayMs - 100).coerceAtLeast(0)
                                    sharedPrefs.edit().putInt("audio_delay_ms", audioDelayMs).apply()
                                }
                            ) {
                                Text("-100ms", fontWeight = FontWeight.Bold)
                            }

                            Slider(
                                value = audioDelayMs.toFloat(),
                                onValueChange = {
                                    audioDelayMs = it.toInt()
                                    sharedPrefs.edit().putInt("audio_delay_ms", audioDelayMs).apply()
                                },
                                valueRange = 0f..2000f,
                                steps = 19,
                                modifier = Modifier.weight(1f)
                            )

                            TextButton(
                                onClick = {
                                    audioDelayMs = (audioDelayMs + 100).coerceAtMost(2000)
                                    sharedPrefs.edit().putInt("audio_delay_ms", audioDelayMs).apply()
                                }
                            ) {
                                Text("+100ms", fontWeight = FontWeight.Bold)
                            }
                        }
                        Text("如果出现口型音轨不同步、组播源延迟等，可通过调大音频偏移使音频延迟播放，以此对齐画面。", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))

                    // 2. Playback Engine selection
                    Column {
                        Text("播放内核引擎 (Decoder Engine)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val engines = listOf(
                                "exoplayer" to "原生 ExoPlayer",
                                "html5_webview" to "HTML5 网页播放器"
                            )
                            engines.forEach { (key, label) ->
                                val isSelected = playerEngine == key
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        playerEngine = key
                                        sharedPrefs.edit().putString("player_engine", key).apply()
                                    },
                                    label = { Text(label, fontWeight = FontWeight.Bold) }
                                )
                            }
                        }
                        Text("原生 ExoPlayer 适合绝大多数标准组播/单播流；HTML5 网页播放器适合播放嵌入式网页、动态流或在硬解兼容性不佳时作为备选。", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))

                    // 3. Hardware acceleration toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("H.264 / H.265 硬件加速解码", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("启用设备内置的 GPU 硬件视频解码器（如 AVC、HEVC）。若部分 4K/HEVC 源出现黑屏或绿屏，可尝试关闭此选项以使用软件兼容解码。", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Switch(
                            checked = useHardwareDecoder,
                            onCheckedChange = {
                                useHardwareDecoder = it
                                sharedPrefs.edit().putBoolean("use_hardware_decoder", it).apply()
                            }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))

                    // 4. Multi Audio Track selection (Track 1, Track 2 etc.)
                    Column {
                        Text("多音轨/伴音选择 (Track 1 / Track 2)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("支持多声道伴音、国配/原音、主/副音轨等。选择音轨 1 或 音轨 2 切换当前播放流的声道音轨。", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        Spacer(modifier = Modifier.height(8.dp))
                        if (audioTracks.isEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = true,
                                    onClick = { },
                                    label = { Text("音轨 1 (默认主音轨)", fontWeight = FontWeight.Bold) }
                                )
                                FilterChip(
                                    selected = false,
                                    onClick = { },
                                    label = { Text("音轨 2 (伴音音轨)", fontWeight = FontWeight.Bold) }
                                )
                            }
                        } else {
                            // Lay out chips in a scrollable Row to handle any number of tracks beautifully without clipping
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                audioTracks.forEachIndexed { idx, track ->
                                    val codecLabel = when {
                                        track.mimeType?.contains("dra", ignoreCase = true) == true -> "DRA"
                                        track.mimeType == "audio/ac3" -> "AC3"
                                        track.mimeType == "audio/eac3" -> "EAC3"
                                        track.mimeType == "audio/mpeg" || track.mimeType == "audio/x-mpeg" -> "MP2/MP3"
                                        track.mimeType == "audio/mp4a-latm" || track.mimeType == "audio/aac" -> "AAC"
                                        track.mimeType == "audio/raw" -> "PCM"
                                        track.mimeType == "audio/vnd.dts" || track.mimeType == "audio/vnd.dts.hd" -> "DTS"
                                        else -> track.mimeType?.substringAfter("audio/")?.uppercase() ?: "未知"
                                    }
                                    val channelsLabel = when (track.channelCount) {
                                        1 -> "单声道"
                                        2 -> "双声道"
                                        6 -> "5.1环绕声"
                                        else -> if (track.channelCount > 0) "${track.channelCount}声道" else ""
                                    }
                                    val baseLabel = track.label ?: track.language ?: "音轨 ${idx + 1}"
                                    val trackText = "$baseLabel ($codecLabel $channelsLabel)"

                                    FilterChip(
                                        selected = track.isSelected,
                                        onClick = {
                                            try {
                                                if (decoderKernel == "VLC") {
                                                    vlcPlayer.setAudioTrack(track.groupIndex)
                                                    val list = mutableListOf<AudioTrackInfo>()
                                                    val currentTrackId = vlcPlayer.audioTrack
                                                    vlcPlayer.audioTracks?.forEach { t ->
                                                        list.add(
                                                            AudioTrackInfo(
                                                                groupIndex = t.id,
                                                                trackIndex = 0,
                                                                language = "",
                                                                label = t.name ?: "Track ${t.id}",
                                                                isSelected = t.id == currentTrackId,
                                                                trackGroup = null,
                                                                mimeType = "VLC",
                                                                sampleRate = 0,
                                                                channelCount = 0
                                                            )
                                                        )
                                                    }
                                                    audioTracks = list
                                                } else {
                                                    val parameters = exoPlayer.trackSelectionParameters.buildUpon()
                                                        .setOverrideForType(
                                                            androidx.media3.common.TrackSelectionOverride(
                                                                track.trackGroup!!,
                                                                listOf(track.trackIndex)
                                                            )
                                                        )
                                                        .build()
                                                    exoPlayer.trackSelectionParameters = parameters
                                                }
                                             } catch (e: Exception) {
                                                 Log.e("VideoPlayer", "Failed to select audio track", e)
                                             }
                                        },
                                        label = { Text(trackText, fontWeight = FontWeight.Bold) }
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "💡 兼容性贴士: 中国部分直播源采用 DRA (多声道数字音频) 或特定规格的 AAC-LATM / E-AC-3。由于部分电视/电视盒/手机缺少硬件级解码授权，原生 ExoPlayer 可能会遇到无声现象。\n" +
                                   "系统已内置‘智能无声检测自动降级’，如有多个音轨将自动切换至可解压的有伴音。若该频道依然完全无声，建议在此弹窗上方将‘播放内核引擎’切换至‘HTML5 网页播放器’作为备用解码方式。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showSettingsDialog = false }) {
                    Text("确定")
                }
            }
        )
    }
}

@OptIn(UnstableApi::class)
private data class ResolvedStream(val url: String, val contentType: String?)

@OptIn(UnstableApi::class)
private suspend fun resolveStreamUrl(url: String): ResolvedStream {
    return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()
            
            val request = okhttp3.Request.Builder()
                .url(url)
                .header("User-Agent", "IPTVPlayer/1.0")
                .get()
                .build()
            
            client.newCall(request).execute().use { response ->
                val finalUrl = response.request.url.toString()
                val contentType = response.header("Content-Type")?.lowercase()
                Log.d("VideoPlayer", "Resolved dynamic stream. Final URL: $finalUrl, Content-Type: $contentType")
                ResolvedStream(finalUrl, contentType)
            }
        } catch (e: Exception) {
            Log.e("VideoPlayer", "Failed to resolve dynamic stream: $url", e)
            ResolvedStream(url, null)
        }
    }
}

@OptIn(UnstableApi::class)
private fun createDataSourceFactory(context: Context): androidx.media3.datasource.DataSource.Factory {
    val userAgent = "VLC/3.0.16 LibVLC/3.0.16"
    val httpDataSourceFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
        .setUserAgent(userAgent)
        .setConnectTimeoutMs(15000)
        .setReadTimeoutMs(20000)
        .setAllowCrossProtocolRedirects(true)
        
    return androidx.media3.datasource.DefaultDataSource.Factory(context, httpDataSourceFactory)
}

@OptIn(UnstableApi::class)
private fun createMediaSource(
    context: Context,
    resolved: ResolvedStream,
    forceHls: Boolean = false
): MediaSource {
    val url = resolved.url
    val contentType = resolved.contentType ?: ""
    val uri = android.net.Uri.parse(url)
    
    val mediaItemBuilder = MediaItem.Builder().setUri(uri)
    
    val isHlsType = forceHls || 
                    url.contains(".m3u8", ignoreCase = true) || 
                    url.contains("hls", ignoreCase = true) ||
                    contentType.contains("mpegurl") || 
                    contentType.contains("application/vnd.apple.mpegurl")
                    
    if (isHlsType) {
        mediaItemBuilder.setMimeType(androidx.media3.common.MimeTypes.APPLICATION_M3U8)
    } else if (
        url.contains(".ts", ignoreCase = true) || 
        url.contains("/udp/", ignoreCase = true) || 
        url.contains("/rtp/", ignoreCase = true) || 
        url.contains("udpxy", ignoreCase = true) || 
        url.contains(":7088", ignoreCase = true) ||
        contentType.contains("mp2t") || 
        contentType.contains("mpeg") || 
        contentType.contains("octet-stream")
    ) {
        mediaItemBuilder.setMimeType(androidx.media3.common.MimeTypes.VIDEO_MP2T)
    }
    
    val mediaItem = mediaItemBuilder.build()
    
    val extractorsFactory = androidx.media3.extractor.DefaultExtractorsFactory().apply {
        setTsExtractorFlags(
            androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS or
            androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS or
            androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_IGNORE_SPLICE_INFO_STREAM
        )
    }

    return when {
        url.startsWith("rtsp://", ignoreCase = true) -> {
            RtspMediaSource.Factory()
                .createMediaSource(mediaItem)
        }
        url.startsWith("rtmp://", ignoreCase = true) -> {
            val rtmpDataSourceFactory = RtmpDataSource.Factory()
            ProgressiveMediaSource.Factory(rtmpDataSourceFactory, extractorsFactory)
                .createMediaSource(mediaItem)
        }
        url.startsWith("udp://", ignoreCase = true) || url.startsWith("rtp://", ignoreCase = true) -> {
            val udpDataSourceFactory = androidx.media3.datasource.DataSource.Factory {
                UdpDataSource()
            }
            ProgressiveMediaSource.Factory(udpDataSourceFactory, extractorsFactory)
                .createMediaSource(MediaItem.fromUri(url.replace("udp://@", "udp://").replace("rtp://@", "udp://")))
        }
        isHlsType -> {
            val dataSourceFactory = createDataSourceFactory(context)
            val hlsExtractorFactory = androidx.media3.exoplayer.hls.DefaultHlsExtractorFactory(
                androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS or
                androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS or
                androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_IGNORE_SPLICE_INFO_STREAM,
                true
            )
            HlsMediaSource.Factory(dataSourceFactory)
                .setExtractorFactory(hlsExtractorFactory)
                .createMediaSource(mediaItem)
        }
        else -> {
            val dataSourceFactory = createDataSourceFactory(context)
            ProgressiveMediaSource.Factory(dataSourceFactory, extractorsFactory)
                .createMediaSource(mediaItem)
        }
    }
}
