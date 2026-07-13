package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.focusable
import androidx.compose.foundation.BorderStroke
import com.example.data.Channel
import com.example.viewmodel.IptvViewModel

@Composable
fun ChannelList(
    viewModel: IptvViewModel,
    onSelectChannel: (Channel) -> Unit,
    modifier: Modifier = Modifier
) {
    val channels by viewModel.channels.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val selectedPlaylistId by viewModel.selectedPlaylistId.collectAsState()
    val currentGroup by viewModel.currentGroup.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val currentPrograms by viewModel.currentPrograms.collectAsState()

    // Layout options: "Grid", "Compact", "Text"
    var layoutView by remember { mutableStateOf("Compact") }

    // Deduplicate and extract group lists
    val groups = remember(channels, playlists, selectedPlaylistId) {
        val uniqueGroups = mutableListOf("全部")
        channels.forEach { ch ->
            if (ch.groupTitle.isNotEmpty() && !uniqueGroups.contains(ch.groupTitle)) {
                uniqueGroups.add(ch.groupTitle)
            }
        }
        uniqueGroups
    }

    Column(modifier = modifier.fillMaxSize().padding(12.dp)) {
        // --- Top Bar: View Layout Toggles ---
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            // Layout switcher buttons
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(
                    onClick = { layoutView = "Grid" },
                    modifier = Modifier.testTag("layout_grid_button"),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = if (layoutView == "Grid") MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                    )
                ) {
                    Icon(Icons.Default.GridView, contentDescription = "网格视图")
                }
                IconButton(
                    onClick = { layoutView = "Compact" },
                    modifier = Modifier.testTag("layout_compact_button"),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = if (layoutView == "Compact") MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                    )
                ) {
                    Icon(Icons.Default.List, contentDescription = "紧凑列表")
                }
                IconButton(
                    onClick = { layoutView = "Text" },
                    modifier = Modifier.testTag("layout_text_button"),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = if (layoutView == "Text") MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                    )
                ) {
                    Icon(Icons.Default.Menu, contentDescription = "纯文本")
                }
            }
        }

        // --- Category Filters (Horizontal pills list) ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            groups.forEach { group ->
                val isSelected = currentGroup == group
                FilterChip(
                    selected = isSelected,
                    onClick = { viewModel.selectGroup(group) },
                    label = { Text(group) },
                    modifier = Modifier.testTag("category_pill_$group"),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = Color.White
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // --- Channels Content Rendering ---
        if (channels.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.TvOff, contentDescription = "暂无频道", modifier = Modifier.size(64.dp), tint = Color.Gray)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("未发现符合筛选条件的频道", style = MaterialTheme.typography.bodyLarge, color = Color.Gray)
                }
            }
        } else {
            when (layoutView) {
                "Grid" -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 120.dp),
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(channels) { channel ->
                            GridChannelCard(
                                channel = channel,
                                currentPrograms = currentPrograms,
                                onSelect = { onSelectChannel(channel) },
                                onToggleFav = { viewModel.toggleFavorite(channel) }
                            )
                        }
                    }
                }
                "Compact" -> {
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(channels) { channel ->
                            CompactChannelRow(
                                channel = channel,
                                currentPrograms = currentPrograms,
                                onSelect = { onSelectChannel(channel) },
                                onToggleFav = { viewModel.toggleFavorite(channel) }
                            )
                        }
                    }
                }
                else -> { // "Text"
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(channels) { channel ->
                            TextChannelRow(
                                channel = channel,
                                currentPrograms = currentPrograms,
                                onSelect = { onSelectChannel(channel) },
                                onToggleFav = { viewModel.toggleFavorite(channel) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GridChannelCard(
    channel: Channel,
    currentPrograms: Map<String, com.example.data.EpgProgram>,
    onSelect: () -> Unit,
    onToggleFav: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable { onSelect() }
            .testTag("channel_grid_${channel.id}"),
        border = if (isFocused) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        colors = CardDefaults.cardColors(
            containerColor = if (isFocused) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            // Favorite Star (top-right)
            IconButton(
                onClick = onToggleFav,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(28.dp)
            ) {
                Icon(
                    if (channel.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = "收藏",
                    tint = if (channel.isFavorite) Color(0xFFFFD700) else Color.Gray,
                    modifier = Modifier.size(18.dp)
                )
            }

            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Large generic M3 Icon for design elegance
                Icon(
                    Icons.Default.LiveTv,
                    contentDescription = "CCTV Logo",
                    modifier = Modifier.size(36.dp),
                    tint = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = channel.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer else Color.Unspecified,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                val activeProgram = currentPrograms[channel.tvgId.ifEmpty { "channel_${channel.id}" }]
                Text(
                    text = activeProgram?.title ?: "Ch ${channel.channelNo}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else Color.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun CompactChannelRow(
    channel: Channel,
    currentPrograms: Map<String, com.example.data.EpgProgram>,
    onSelect: () -> Unit,
    onToggleFav: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable { onSelect() }
            .testTag("channel_compact_${channel.id}"),
        border = if (isFocused) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        colors = CardDefaults.cardColors(
            containerColor = if (isFocused) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(
                        if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        MaterialTheme.shapes.small
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = channel.channelNo.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (channel.isMulticast) {
                        Badge(
                            containerColor = Color(0xFF4CAF50),
                            contentColor = Color.White,
                            modifier = Modifier.padding(end = 6.dp)
                        ) {
                            Text("UDP")
                        }
                    }
                    Text(
                        text = channel.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer else Color.Unspecified
                    )
                }
                
                val activeProgram = currentPrograms[channel.tvgId.ifEmpty { "channel_${channel.id}" }]
                if (activeProgram != null) {
                    Text(
                        text = "正在播放: ${activeProgram.title}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f) else MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Text(
                        text = "地址: ${channel.url}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f) else Color.Gray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(onClick = onToggleFav) {
                Icon(
                    if (channel.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = "收藏",
                    tint = if (channel.isFavorite) Color(0xFFFFD700) else if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f) else Color.Gray
                )
            }
        }
    }
}

@Composable
fun TextChannelRow(
    channel: Channel,
    currentPrograms: Map<String, com.example.data.EpgProgram>,
    onSelect: () -> Unit,
    onToggleFav: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable { onSelect() }
            .testTag("channel_text_${channel.id}"),
        border = if (isFocused) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
        colors = CardDefaults.cardColors(
            containerColor = if (isFocused) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Text(
                    text = "${channel.channelNo}.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.width(32.dp)
                )
                
                Column {
                    Text(
                        text = channel.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer else Color.Unspecified,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val activeProgram = currentPrograms[channel.tvgId.ifEmpty { "channel_${channel.id}" }]
                    if (activeProgram != null) {
                        Text(
                            text = activeProgram.title,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else Color.Gray,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            IconButton(onClick = onToggleFav, modifier = Modifier.size(24.dp)) {
                Icon(
                    if (channel.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = "收藏",
                    tint = if (channel.isFavorite) Color(0xFFFFD700) else if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f) else Color.Gray,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
