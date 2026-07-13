package com.example.ui.components

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.viewmodel.IptvViewModel

@Composable
fun SettingsPanel(
    viewModel: IptvViewModel,
    modifier: Modifier = Modifier
) {
    val themeMode by viewModel.themeMode.collectAsState()
    val bufferMs by viewModel.bufferMs.collectAsState()
    val history by viewModel.history.collectAsState()
    val remotePushUrl by viewModel.remotePushUrl.collectAsState()
    val preferredAudioTrack by viewModel.preferredAudioTrack.collectAsState()
    val multicastMode by viewModel.multicastMode.collectAsState()
    val externalProxyUrl by viewModel.externalProxyUrl.collectAsState()

    var remotePushUrlInput by remember(remotePushUrl) { mutableStateOf(remotePushUrl) }

    var sleepTimerText by remember { mutableStateOf("未开启") }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- Title Section ---
        item {
            Text(
                text = "设置与个性化",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "配置播放器以及后台远程直接订阅推送源。",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        }

        // --- General Settings ---
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("常规偏好设置", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                    // Theme selector
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("系统主题颜色模式: ")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = themeMode == "Dark",
                                onClick = { viewModel.setThemeMode("Dark") },
                                label = { Text("深色模式") },
                                modifier = Modifier.testTag("theme_dark_chip")
                            )
                            FilterChip(
                                selected = themeMode == "Light",
                                onClick = { viewModel.setThemeMode("Light") },
                                label = { Text("浅色模式") },
                                modifier = Modifier.testTag("theme_light_chip")
                            )
                        }
                    }

                    HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.2f))

                    // Buffer configurations
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("组播与网络缓冲池大小")
                            Text("${bufferMs}毫秒", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = bufferMs.toFloat(),
                            onValueChange = { viewModel.setBufferMs(it.toInt()) },
                            valueRange = 100f..1000f,
                            steps = 9,
                            modifier = Modifier.testTag("buffer_slider")
                        )
                        Text("更高的缓冲区将降低播放卡顿率，但切台时间会略微增加。", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    }

                    HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.2f))

                    // Sleep Timer
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("定时停止播放 (睡眠倒计时)")
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("关", "30分", "60分", "120分").forEach { text ->
                                FilterChip(
                                    selected = sleepTimerText == text,
                                    onClick = { sleepTimerText = text },
                                    label = { Text(text) },
                                    modifier = Modifier.testTag("sleep_timer_$text")
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- Multicast / Proxy Settings Card ---
        item {
            var externalProxyInput by remember(externalProxyUrl) { mutableStateOf(externalProxyUrl) }

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("UDP组播与代理播放设置", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "配置如何处理和转换局域网 UDP 组播/RTP 直播源。外置代理(如路由器 udpxy)通常提供最稳定的播放效果。",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("组播处理模式", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = multicastMode == "InternalProxy",
                                    onClick = { viewModel.setMulticastMode("InternalProxy") },
                                    modifier = Modifier.testTag("radio_internal_proxy")
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Column {
                                    Text("本地内置代理转换 (推荐无路由代理时使用)", style = MaterialTheme.typography.bodyMedium)
                                    Text("在应用后台自动启动 UDP 收流服务，并将其代理为低延迟 HTTP 播放流。", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                }
                            }
                            
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = multicastMode == "ExternalProxy",
                                    onClick = { viewModel.setMulticastMode("ExternalProxy") },
                                    modifier = Modifier.testTag("radio_external_proxy")
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Column {
                                    Text("路由器外置代理 (推荐，极致稳定)", style = MaterialTheme.typography.bodyMedium)
                                    Text("将组播地址重定向到路由器 udpxy 服务（如：http://192.168.31.1:7088/udp/...）。", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = multicastMode == "DirectUDP",
                                    onClick = { viewModel.setMulticastMode("DirectUDP") },
                                    modifier = Modifier.testTag("radio_direct_udp")
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Column {
                                    Text("原生直接组播接收 (ExoPlayer 直接拉流)", style = MaterialTheme.typography.bodyMedium)
                                    Text("直接由播放器内核通过 udp:// 协议连接（部分安卓盒子网卡不支持或需特殊配置）。", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                }
                            }
                        }
                    }

                    if (multicastMode == "ExternalProxy") {
                        HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.2f))
                        
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("外置代理服务器前缀 (udpxy URL)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            OutlinedTextField(
                                value = externalProxyInput,
                                onValueChange = { externalProxyInput = it },
                                label = { Text("外置代理地址 (如 http://192.168.31.1:7088)") },
                                placeholder = { Text("http://192.168.31.1:7088") },
                                modifier = Modifier.fillMaxWidth().testTag("external_proxy_input"),
                                singleLine = true
                            )
                            
                            Button(
                                onClick = {
                                    var cleaned = externalProxyInput.trim()
                                    if (cleaned.endsWith("/")) {
                                        cleaned = cleaned.dropLast(1)
                                    }
                                    viewModel.setExternalProxyUrl(cleaned)
                                },
                                modifier = Modifier.align(Alignment.End).testTag("save_external_proxy_button")
                            ) {
                                Icon(Icons.Default.Save, contentDescription = "保存外置代理")
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("保存并应用代理")
                            }
                        }
                    }
                }
            }
        }

        // --- Player Settings ---
        item {
            val decoderKernel by viewModel.decoderKernel.collectAsState()
            val preferredAudioTrack by viewModel.preferredAudioTrack.collectAsState()

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("播放器设置", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                    // 1. Decoder selection
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("解码内核 (Decoder Kernel)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            listOf("ExoPlayer", "VLC", "IJKPlayer").forEach { kernel ->
                                FilterChip(
                                    selected = decoderKernel == kernel,
                                    onClick = { viewModel.setDecoderKernel(kernel) },
                                    label = { Text(kernel) },
                                    modifier = Modifier.testTag("decoder_kernel_$kernel")
                                )
                            }
                        }

                        // Display detailed specifications for the selected kernel
                        val (advantages, compatibility, scenarios) = when (decoderKernel) {
                            "ExoPlayer" -> Triple(
                                "Google 官方推荐，HLS/DASH 支持出色，占用资源低",
                                "默认支持 AAC、MP3、Vorbis；AC3/EAC3 需设备硬件支持或额外扩展，部分盒子可能无声",
                                "单播 HLS、HTTP-FLV 等标准流"
                            )
                            "VLC" -> Triple(
                                "软解库极强，几乎支持所有音频编码（AC3/EAC3/DTS/AAC-LATM 等）",
                                "自带完整解码库，不依赖硬件，最不容易出现无声问题",
                                "组播流（UDP/RTP）、老旧 RTSP、PHP 源"
                            )
                            else -> Triple(
                                "轻量、可定制，基于 FFmpeg",
                                "通过 FFmpeg 编译选项决定，打包时需显式开启 AC3/DTS，否则无声",
                                "需高度定制的场景"
                            )
                        }

                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.Top) {
                                    Text("🎯 核心优势: ", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    Text(advantages, style = MaterialTheme.typography.labelMedium)
                                }
                                Row(verticalAlignment = Alignment.Top) {
                                    Text("🔊 音频兼容: ", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    Text(compatibility, style = MaterialTheme.typography.labelMedium)
                                }
                                Row(verticalAlignment = Alignment.Top) {
                                    Text("💡 推荐场景: ", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    Text(scenarios, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.2f))

                    // 2. Audio track / Audio stream preferences
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("首选音轨与音频流 (Audio Track / Stream)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        
                        val tracks = listOf("默认", "主音轨 (第一音轨)", "副音轨 (第二音轨)", "立体声", "左声道", "右声道")
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            // Row 1
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                tracks.take(3).forEach { track ->
                                    FilterChip(
                                        selected = preferredAudioTrack == track,
                                        onClick = { viewModel.setPreferredAudioTrack(track) },
                                        label = { Text(track, style = MaterialTheme.typography.labelMedium) },
                                        modifier = Modifier.testTag("audio_track_$track")
                                    )
                                }
                            }
                            // Row 2
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                tracks.drop(3).forEach { track ->
                                    FilterChip(
                                        selected = preferredAudioTrack == track,
                                        onClick = { viewModel.setPreferredAudioTrack(track) },
                                        label = { Text(track, style = MaterialTheme.typography.labelMedium) },
                                        modifier = Modifier.testTag("audio_track_$track")
                                    )
                                }
                            }
                        }
                        Text("播放具有多语言/多音轨的节目源或进行声道切换时，系统将自动尝试优先匹配选中的音轨与通道模式。", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    }
                }
            }
        }

        // --- Remote Push / Subscription Settings ---
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("后台远程推送/直接源订阅", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "在此配置您的远程直接推送源或 M3U 订阅源 URL。应用启动时或配置保存后将自动于后台静默拉取并刷新该源。",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )

                    OutlinedTextField(
                        value = remotePushUrlInput,
                        onValueChange = { remotePushUrlInput = it },
                        label = { Text("后台远程订阅 URL") },
                        modifier = Modifier.fillMaxWidth().testTag("remote_push_url_input"),
                        singleLine = true
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                viewModel.setRemotePushUrl(remotePushUrlInput)
                            },
                            modifier = Modifier.testTag("save_remote_push_url_button")
                        ) {
                            Icon(Icons.Default.Save, contentDescription = "保存")
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("保存并静默同步")
                        }
                    }
                }
            }
        }

        // --- Playback History ---
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("最近播放历史", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        if (history.isNotEmpty()) {
                            TextButton(onClick = { viewModel.clearHistory() }) {
                                Text("清空历史", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }

                    if (history.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(80.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("暂无观看记录", color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
                            history.forEach { item ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(item.channelName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                        Text(
                                            text = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.CHINA).format(item.watchedAt),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.Gray
                                        )
                                    }
                                    Icon(Icons.Default.History, contentDescription = "历史", tint = Color.Gray, modifier = Modifier.size(16.dp))
                                }
                                HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.1f))
                            }
                        }
                    }
                }
            }
        }
    }
}
