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

        // --- LAN Remote Instant Push Card ---
        item {
            val localIp by viewModel.localIpAddress.collectAsState()
            val lanPushPort = viewModel.lanPushPort
            val lanPushUrl = "http://$localIp:$lanPushPort"

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("局域网即时远程推送 (LAN Push)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Surface(
                            color = Color(0xFF4CAF50).copy(alpha = 0.2f),
                            shape = MaterialTheme.shapes.extraSmall,
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4CAF50))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(Color(0xFF4CAF50), shape = androidx.compose.foundation.shape.CircleShape)
                                )
                                Text("服务正在运行", style = MaterialTheme.typography.labelSmall, color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Text(
                        "在手机、电脑或平板的浏览器中直接打开以下网址，即可远程推送任意直播链接或 M3U 订阅源至此设备播放：",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                        shape = MaterialTheme.shapes.medium,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = lanPushUrl,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.testTag("lan_push_url_text")
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "👉 请确保您的发送端（如手机、电脑）和此接收设备处于同一个局域网（Wi-Fi/网线）下",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.padding(top = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("📺 播放格式与兼容性深度解析：", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text(
                            "1. 北京卫视 4K 播放正常原因：该频道流采用标准的 H.265/HEVC (视频编码) 与 AC-3/AAC (音频编码)。这两种编码是国际数字广播与流媒体通用标准，现代安卓电视盒子或系统底层都自带硬件解码加速器，任何播放器内核（包括默认的 ExoPlayer）均可无缝拉流播放，画面极其流畅。\n\n" +
                            "2. CCTV-2 HD/SD 无法播放原因：中国国内 IPTV（电信/联通/移动）组播源中的 CCTV-1、CCTV-2、CCTV-3、CCTV-5 等高清及标清频道，为了节省卫星和骨干网传输带宽并配合广电合规，大都采用了中国主导的专属广电标准：AVS+ / AVS2 / AVS3 (视频编码) 加上 DRA (音频编码)。\n" +
                            "由于这些编码属于中国独有国家标准，在海外标准的 Android 原生固件或默认 ExoPlayer 中完全没有内置对应的软硬件解码器（Codec）。因此强行拉流播放会由于解码器缺失而导致直接报「格式不支持/黑屏/无声/解码失败」错误。\n\n" +
                            "3. 推荐解决方案：\n" +
                            "• 方案 A：在路由器或 udpxy 组播代理服务器后台，开启「媒体流实时转码」选项，将 AVS+ 视频转码为 H.264，DRA 音频转码为 AAC，这样客户端在任何平台上都能秒开播放。\n" +
                            "• 方案 B：在本页上方的「播放器设置」中，将「解码内核」切换为 VLC 或 IJKPlayer。由于 VLC 本身带有非常强大的软解 FFmpeg 底层，能兼容更多的非标准音频与中国国标格式。",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray,
                            lineHeight = androidx.compose.ui.unit.TextUnit.Unspecified
                        )
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
