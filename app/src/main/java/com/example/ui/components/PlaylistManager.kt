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
import com.example.data.Playlist
import com.example.viewmodel.IptvViewModel

@Composable
fun PlaylistManager(
    viewModel: IptvViewModel,
    modifier: Modifier = Modifier
) {
    val playlists by viewModel.playlists.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }

    // Form states
    var listName by remember { mutableStateOf("") }
    var listUrl by remember { mutableStateOf("") }
    var userAgent by remember { mutableStateOf("") }
    var cookieStr by remember { mutableStateOf("") }
    var requestMethod by remember { mutableStateOf("GET") }
    var requestBody by remember { mutableStateOf("") }

    Box(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "订阅源管理",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                Button(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.testTag("add_playlist_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "添加订阅")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("添加订阅")
                }
            }

            if (isLoading) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (playlists.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.CloudOff, contentDescription = "暂无源", modifier = Modifier.size(64.dp), tint = Color.Gray)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("暂无播放订阅，点击右上角按钮添加", style = MaterialTheme.typography.bodyLarge, color = Color.Gray)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(playlists) { playlist ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("playlist_card_${playlist.id}"),
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
                                    Column {
                                        Text(
                                            text = playlist.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = if (playlist.isLocal) "本地导入: ${playlist.filePath}" else playlist.url,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.Gray,
                                            maxLines = 1
                                        )
                                    }

                                    Row {
                                        IconButton(
                                            onClick = { viewModel.refreshPlaylist(playlist.id) },
                                            modifier = Modifier.testTag("refresh_playlist_${playlist.id}")
                                        ) {
                                            Icon(Icons.Default.Refresh, contentDescription = "刷新")
                                        }
                                        IconButton(
                                            onClick = { viewModel.deletePlaylist(playlist) },
                                            modifier = Modifier.testTag("delete_playlist_${playlist.id}")
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (playlist.lastUpdated > 0) "更新于: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.CHINA).format(playlist.lastUpdated)}" else "尚未更新",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.LightGray
                                    )
                                    
                                    if (playlist.requestMethod == "POST") {
                                        Badge(containerColor = MaterialTheme.colorScheme.secondary) {
                                            Text("POST")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            errorMessage?.let { err ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Text(text = err, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(12.dp))
                }
            }
        }

        // --- Add Subscription Dialog ---
        if (showAddDialog) {
            AlertDialog(
                onDismissRequest = { showAddDialog = false },
                title = { Text("添加 IPTV 订阅源") },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = listName,
                            onValueChange = { listName = it },
                            label = { Text("源名称 (例如: 国内高清流)") },
                            modifier = Modifier.fillMaxWidth().testTag("playlist_name_input")
                        )

                        OutlinedTextField(
                            value = listUrl,
                            onValueChange = { listUrl = it },
                            label = { Text("订阅 URL (M3U)") },
                            modifier = Modifier.fillMaxWidth().testTag("playlist_url_input")
                        )

                        Text("高级设置", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))

                        OutlinedTextField(
                            value = userAgent,
                            onValueChange = { userAgent = it },
                            label = { Text("自定义 User-Agent (可选)") },
                            placeholder = { Text("IPTVPlayer/1.0") },
                            modifier = Modifier.fillMaxWidth().testTag("playlist_ua_input")
                        )

                        OutlinedTextField(
                            value = cookieStr,
                            onValueChange = { cookieStr = it },
                            label = { Text("自定义 Cookie (可选)") },
                            modifier = Modifier.fillMaxWidth().testTag("playlist_cookie_input")
                        )

                        // Request Method Segmented Control
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("请求方式: ", style = MaterialTheme.typography.bodyMedium)
                            Button(
                                onClick = { requestMethod = "GET" },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (requestMethod == "GET") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (requestMethod == "GET") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            ) {
                                Text("GET")
                            }
                            Button(
                                onClick = { requestMethod = "POST" },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (requestMethod == "POST") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (requestMethod == "POST") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            ) {
                                Text("POST")
                            }
                        }

                        if (requestMethod == "POST") {
                            OutlinedTextField(
                                value = requestBody,
                                onValueChange = { requestBody = it },
                                label = { Text("POST 请求体 (表单 key=val&...)") },
                                modifier = Modifier.fillMaxWidth().testTag("playlist_body_input")
                            )
                        }

                        Text(
                            text = "注: 订阅 URL/请求体中支持占位符 {timestamp} 和 {random}，系统将在更新时自动进行替换。",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.LightGray
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (listName.isNotEmpty() && listUrl.isNotEmpty()) {
                                viewModel.importPlaylistFromUrl(
                                    name = listName,
                                    url = listUrl,
                                    userAgent = userAgent,
                                    cookie = cookieStr,
                                    method = requestMethod,
                                    body = requestBody
                                )
                                showAddDialog = false
                                // Clear fields
                                listName = ""
                                listUrl = ""
                                userAgent = ""
                                cookieStr = ""
                                requestMethod = "GET"
                                requestBody = ""
                            }
                        },
                        modifier = Modifier.testTag("submit_playlist_button")
                    ) {
                        Text("导入并加载")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddDialog = false }) {
                        Text("取消")
                    }
                }
            )
        }
    }
}
