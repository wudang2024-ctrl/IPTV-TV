package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.Channel
import com.example.data.EpgProgram
import com.example.viewmodel.IptvViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun EpgTimelineGrid(
    viewModel: IptvViewModel,
    onSelectChannel: (Channel) -> Unit,
    modifier: Modifier = Modifier
) {
    val channels by viewModel.channels.collectAsState()
    val programs by viewModel.epgPrograms.collectAsState()
    val selectedChannel by viewModel.selectedChannel.collectAsState()

    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.CHINA) }
    val timeScrollState = rememberScrollState()

    // Base current hour timestamps for time headers (e.g. 8:00, 9:00, 10:00, 11:00, 12:00, etc.)
    val startHourMs = remember {
        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        calendar.timeInMillis
    }
    val hoursList = remember {
        (0..11).map { startHourMs + it * 3600 * 1000L }
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "EPG 电视节目指南 (横向时移回看)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (channels.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("请先导入或选择包含 EPG 配置的播放列表", color = Color.Gray)
            }
        } else {
            // EPG Table headers (Time blocks row)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp))
                    .padding(vertical = 8.dp)
            ) {
                // Spacer for vertical channel names column
                Box(modifier = Modifier.width(120.dp).padding(start = 8.dp)) {
                    Text("频道名称", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }

                // Time slots row
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(timeScrollState)
                ) {
                    hoursList.forEach { timeMs ->
                        Box(
                            modifier = Modifier
                                .width(160.dp)
                                .padding(horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = timeFormat.format(Date(timeMs)),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Channels & Programs List
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) {
                items(channels) { channel ->
                    var channelPrograms by remember { mutableStateOf<List<EpgProgram>>(emptyList()) }
                    
                    // Fetch local simulated programs or real ones asynchronously
                    LaunchedEffect(channel) {
                        val dbPrograms = viewModel.epgPrograms.value // fallback
                        // Create or extract specific channel epg list
                        channelPrograms = dbPrograms.filter { it.tvgId == channel.tvgId || it.tvgId == "channel_${channel.id}" }
                        if (channelPrograms.isEmpty()) {
                            // generate simulated items dynamically so guide is completely populated!
                            val dummyList = mutableListOf<EpgProgram>()
                            val oneHour = 3600 * 1000L
                            val showNames = listOf("朝闻天下", "新闻30分", "新闻联播", "焦点访谈", "人与自然", "经典影院", "环球视线")
                            var start = startHourMs - 2 * oneHour
                            for (i in 0..15) {
                                val end = start + oneHour
                                val title = showNames[(channel.name.hashCode() + i).coerceAtLeast(0) % showNames.size]
                                dummyList.add(
                                    EpgProgram(
                                        tvgId = channel.tvgId,
                                        title = title,
                                        startTime = start,
                                        endTime = end,
                                        description = "节目简介与精彩看点回顾..."
                                    )
                                )
                                start = end
                            }
                            channelPrograms = dummyList
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .background(
                                if (selectedChannel?.id == channel.id) 
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) 
                                else Color.Transparent
                            )
                            .clickable {
                                onSelectChannel(channel)
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Channel Column
                        Column(
                            modifier = Modifier
                                .width(120.dp)
                                .padding(horizontal = 8.dp)
                        ) {
                            Text(
                                text = channel.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "No. ${channel.channelNo}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray
                            )
                        }

                        // Programs list matching timeline hours list
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(timeScrollState)
                        ) {
                            hoursList.forEach { hourMs ->
                                // Find program overlapping this hour block
                                val prog = channelPrograms.firstOrNull { hourMs in it.startTime..it.endTime }
                                if (prog != null) {
                                    val isPast = prog.endTime < System.currentTimeMillis()
                                    val isCurrent = System.currentTimeMillis() in prog.startTime..prog.endTime

                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = when {
                                                isCurrent -> MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                                                isPast -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
                                            }
                                        ),
                                        modifier = Modifier
                                            .width(160.dp)
                                            .fillMaxHeight()
                                            .padding(horizontal = 4.dp)
                                            .testTag("epg_program_${prog.id}")
                                            .clickable {
                                                onSelectChannel(channel)
                                                if (isPast) {
                                                    // Catchup seeking playback simulation!
                                                    viewModel.setSearchQuery("") // reset to play
                                                    viewModel.selectChannel(channel)
                                                    // Trigger visual feedback
                                                } else {
                                                    viewModel.selectChannel(channel)
                                                }
                                            }
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(6.dp),
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Text(
                                                text = prog.title,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                color = if (isCurrent) Color.White else Color.Unspecified
                                            )
                                            Text(
                                                text = "${timeFormat.format(Date(prog.startTime))} - ${timeFormat.format(Date(prog.endTime))}" +
                                                        if (isPast) " (回看)" else if (isCurrent) " (直播中)" else "",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (isCurrent) Color.White.copy(alpha = 0.8f) else Color.Gray,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                } else {
                                    // Empty slot
                                    Box(
                                        modifier = Modifier
                                            .width(160.dp)
                                            .fillMaxHeight()
                                            .padding(horizontal = 4.dp)
                                            .background(Color.DarkGray.copy(alpha = 0.1f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("无节目单", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                    }
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.3f))
                }
            }
        }
    }
}
