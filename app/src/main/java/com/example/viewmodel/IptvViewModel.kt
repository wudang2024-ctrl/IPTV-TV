package com.example.viewmodel

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.NetworkInterface

class IptvViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val dao = db.iptvDao()
    private val repository = IptvRepository(application, dao)

    // --- Core States ---
    val playlists: StateFlow<List<Playlist>> = repository.playlists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedPlaylistId = MutableStateFlow<Int?>(null)
    val selectedPlaylistId: StateFlow<Int?> = _selectedPlaylistId.asStateFlow()

    private val _currentGroup = MutableStateFlow<String>("全部")
    val currentGroup: StateFlow<String> = _currentGroup.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val channels: StateFlow<List<Channel>> = combine(
        repository.allChannels,
        _selectedPlaylistId,
        _currentGroup,
        _searchQuery
    ) { all, playlistId, group, query ->
        var list = all
        if (playlistId != null) {
            list = list.filter { it.playlistId == playlistId }
        }
        if (group != "全部") {
            list = list.filter { it.groupTitle == group }
        }
        if (query.isNotEmpty()) {
            list = list.filter { 
                it.name.contains(query, ignoreCase = true) || 
                it.groupTitle.contains(query, ignoreCase = true) ||
                it.channelNo.toString() == query
            }
        }
        list
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteChannels: StateFlow<List<Channel>> = repository.favoriteChannels
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val history: StateFlow<List<HistoryItem>> = repository.history
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedChannel = MutableStateFlow<Channel?>(null)
    val selectedChannel: StateFlow<Channel?> = _selectedChannel.asStateFlow()

    private val _epgPrograms = MutableStateFlow<List<EpgProgram>>(emptyList())
    val epgPrograms: StateFlow<List<EpgProgram>> = _epgPrograms.asStateFlow()

    private val _currentProgram = MutableStateFlow<EpgProgram?>(null)
    val currentProgram: StateFlow<EpgProgram?> = _currentProgram.asStateFlow()

    // --- Loading & Status ---
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // --- Proxy Server States ---
    private var proxyServer: MulticastProxyServer? = null
    private val _isProxyRunning = MutableStateFlow(false)
    val isProxyRunning: StateFlow<Boolean> = _isProxyRunning.asStateFlow()

    private val _proxyPort = MutableStateFlow(8123)
    val proxyPort: StateFlow<Int> = _proxyPort.asStateFlow()

    // --- Settings & Preferences ---
    private val _themeMode = MutableStateFlow("Dark") // "Dark" or "Light"
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _bufferMs = MutableStateFlow(300) // 100 - 1000
    val bufferMs: StateFlow<Int> = _bufferMs.asStateFlow()

    private val _playbackAspect = MutableStateFlow("Original") // "Original", "Stretch", "16:9", "4:3", "Zoom"
    val playbackAspect: StateFlow<String> = _playbackAspect.asStateFlow()

    // --- Decoder and Audio Settings ---
    private val prefs = application.getSharedPreferences("iptv_settings", android.content.Context.MODE_PRIVATE)

    private val _decoderKernel = MutableStateFlow(prefs.getString("decoder_kernel", "ExoPlayer") ?: "ExoPlayer")
    val decoderKernel: StateFlow<String> = _decoderKernel.asStateFlow()

    private val _preferredAudioTrack = MutableStateFlow(prefs.getString("preferred_audio_track", "默认") ?: "默认")
    val preferredAudioTrack: StateFlow<String> = _preferredAudioTrack.asStateFlow()

    // --- Multicast / Proxy Settings ---
    private val _multicastMode = MutableStateFlow(prefs.getString("multicast_mode", "InternalProxy") ?: "InternalProxy")
    val multicastMode: StateFlow<String> = _multicastMode.asStateFlow()

    private val _externalProxyUrl = MutableStateFlow(prefs.getString("external_proxy_url", "http://192.168.31.1:7088") ?: "http://192.168.31.1:7088")
    val externalProxyUrl: StateFlow<String> = _externalProxyUrl.asStateFlow()

    // --- LAN Remote Push States & Server ---
    val lanPushPort = 19150
    val localIpAddress = MutableStateFlow("127.0.0.1")
    private var lanPushServer: LanPushServer? = null

    private fun getIpAddress(): String {
        try {
            val interfaces = java.util.Collections.list(NetworkInterface.getNetworkInterfaces())
            for (networkInterface in interfaces) {
                val addresses = java.util.Collections.list(networkInterface.inetAddresses)
                for (address in addresses) {
                    if (!address.isLoopbackAddress) {
                        val sAddr = address.hostAddress
                        val isIPv4 = sAddr.indexOf(':') < 0
                        if (isIPv4) {
                            return sAddr
                        }
                    }
                }
            }
        } catch (ex: Exception) {
            Log.e("IptvViewModel", "Error getting IP", ex)
        }
        return "127.0.0.1"
    }

    fun setMulticastMode(mode: String) {
        _multicastMode.value = mode
        prefs.edit().putString("multicast_mode", mode).apply()
    }

    fun setExternalProxyUrl(url: String) {
        _externalProxyUrl.value = url
        prefs.edit().putString("external_proxy_url", url).apply()
    }

    init {
        // Retrieve and update local IP address
        localIpAddress.value = getIpAddress()

        // Start LAN push server to receive streams pushed from phone/PC
        lanPushServer = LanPushServer(lanPushPort) { url, name ->
            handlePushedChannel(url, name)
        }
        lanPushServer?.start()

        // Seed default sample playlist on launch if database is empty
        viewModelScope.launch {
            repository.playlists.first().let { currentList ->
                if (currentList.isEmpty()) {
                    seedDefaultPlaylist()
                } else {
                    _selectedPlaylistId.value = currentList.firstOrNull()?.id
                }
            }
            // Automatically sync/update remote push subscription in background on start
            triggerRemotePushSync()
        }
    }


    fun selectPlaylist(id: Int?) {
        _selectedPlaylistId.value = id
        _currentGroup.value = "全部"
    }

    fun selectGroup(group: String) {
        _currentGroup.value = group
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectChannel(channel: Channel?) {
        _selectedChannel.value = channel
        if (channel != null) {
            // Log history
            viewModelScope.launch {
                repository.addHistoryItem(channel)
                // Fetch EPG
                repository.getProgramsForChannel(channel.tvgId.ifEmpty { "channel_${channel.id}" })
                    .collect { programs ->
                        _epgPrograms.value = programs
                        updateCurrentProgram(programs)
                    }
            }
        } else {
            _epgPrograms.value = emptyList()
            _currentProgram.value = null
        }
    }

    private fun updateCurrentProgram(programs: List<EpgProgram>) {
        val now = System.currentTimeMillis()
        _currentProgram.value = programs.firstOrNull { now in it.startTime..it.endTime }
    }

    fun toggleFavorite(channel: Channel) {
        viewModelScope.launch {
            repository.setFavorite(channel.id, !channel.isFavorite)
            // If the selected channel is the toggled one, refresh it
            if (_selectedChannel.value?.id == channel.id) {
                _selectedChannel.value = _selectedChannel.value?.copy(isFavorite = !channel.isFavorite)
            }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    fun setThemeMode(mode: String) {
        _themeMode.value = mode
    }

    fun setBufferMs(ms: Int) {
        _bufferMs.value = ms
    }

    fun setPlaybackAspect(aspect: String) {
        _playbackAspect.value = aspect
    }

    fun setDecoderKernel(kernel: String) {
        _decoderKernel.value = kernel
        prefs.edit().putString("decoder_kernel", kernel).apply()
    }

    fun setPreferredAudioTrack(track: String) {
        _preferredAudioTrack.value = track
        prefs.edit().putString("preferred_audio_track", track).apply()
    }

    // --- Playlist & EPG management ---
    fun importPlaylistFromUrl(name: String, url: String, userAgent: String = "", cookie: String = "", method: String = "GET", body: String = "") {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val p = Playlist(
                    name = name,
                    url = url,
                    isLocal = false,
                    userAgent = userAgent,
                    cookie = cookie,
                    requestMethod = method,
                    requestBody = body
                )
                val id = repository.insertPlaylist(p).toInt()
                val result = repository.importOrRefreshPlaylist(id)
                if (result.isSuccess) {
                    _selectedPlaylistId.value = id
                } else {
                    _errorMessage.value = result.exceptionOrNull()?.message ?: "导入失败"
                    repository.deletePlaylist(p.copy(id = id)) // delete on failure
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "导入错误"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deletePlaylist(playlist: Playlist) {
        viewModelScope.launch {
            repository.deletePlaylist(playlist)
            if (_selectedPlaylistId.value == playlist.id) {
                _selectedPlaylistId.value = playlists.value.firstOrNull { it.id != playlist.id }?.id
            }
        }
    }

    fun refreshPlaylist(playlistId: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = repository.importOrRefreshPlaylist(playlistId)
            if (!result.isSuccess) {
                _errorMessage.value = result.exceptionOrNull()?.message ?: "刷新失败"
            }
            _isLoading.value = false
        }
    }

    // --- Proxy Controls ---
    fun toggleProxy() {
        val context = getApplication<Application>().applicationContext
        val intent = Intent(context, MulticastProxyService::class.java).apply {
            putExtra("PROXY_PORT", _proxyPort.value)
        }
        if (_isProxyRunning.value) {
            context.stopService(intent)
            _isProxyRunning.value = false
        } else {
            context.startService(intent)
            _isProxyRunning.value = true
        }
    }

    fun updateProxyPort(port: Int) {
        _proxyPort.value = port
        if (_isProxyRunning.value) {
            // restart
            val context = getApplication<Application>().applicationContext
            val stopIntent = Intent(context, MulticastProxyService::class.java)
            context.stopService(stopIntent)
            
            val startIntent = Intent(context, MulticastProxyService::class.java).apply {
                putExtra("PROXY_PORT", port)
            }
            context.startService(startIntent)
        }
    }



    // --- Seeding default data ---
    private suspend fun seedDefaultPlaylist() = withContext(Dispatchers.IO) {
        // Create sample local file
        val filename = "default_sample.m3u"
        val fileContents = """
            #EXTM3U
            #EXTINF:-1 tvg-id="cctv1" tvg-name="CCTV-1 综合" tvg-logo="https://assets.icourse163.org/icourse-portal/school/9b9f9ff5f2cb42478a8eb64821a46979.png" group-title="央视频道", CCTV-1 综合
            http://sample.vodobox.net/skate_phantom_flex_4k/skate_phantom_flex_4k.m3u8
            #EXTINF:-1 tvg-id="cctv5" tvg-name="CCTV-5 体育" tvg-logo="https://assets.icourse163.org/icourse-portal/school/9b9f9ff5f2cb42478a8eb64821a46979.png" group-title="央视频道", CCTV-5 体育
            http://playertest.longtailvideo.com/adaptive/bipbop/bipbop.m3u8
            #EXTINF:-1 tvg-id="hnws" tvg-name="湖南卫视" tvg-logo="https://assets.icourse163.org/icourse-portal/school/9b9f9ff5f2cb42478a8eb64821a46979.png" group-title="卫视频道", 湖南卫视
            https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8
            #EXTINF:-1 tvg-id="zjws" tvg-name="浙江卫视" tvg-logo="https://assets.icourse163.org/icourse-portal/school/9b9f9ff5f2cb42478a8eb64821a46979.png" group-title="卫视频道", 浙江卫视
            https://bitdash-a.akamaihd.net/content/MI201109210084_1/m3u8s/f08e80da-bf1d-4e3d-8899-f0f6155f6efa.m3u8
            #EXTINF:-1 tvg-id="multicast_cctv1" tvg-name="CCTV-1 (局域网组播测试)" group-title="局域网组播", CCTV-1 (组播)
            udp://@239.1.1.1:5000
            #EXTINF:-1 tvg-id="php_source" tvg-name="PHP动态防盗链测试源" group-title="PHP动态源", 动态高清源
            http://your-server.com/live.php?token={timestamp}
        """.trimIndent()

        try {
            val file = File(getApplication<Application>().filesDir, filename)
            FileOutputStream(file).use {
                it.write(fileContents.toByteArray())
            }

            val p = Playlist(
                name = "内置演示播放列表",
                filePath = filename,
                isLocal = true,
                isEnabled = true
            )
            val playlistId = repository.insertPlaylist(p).toInt()
            repository.importOrRefreshPlaylist(playlistId)
            
            _selectedPlaylistId.value = playlistId
        } catch (e: Exception) {
            Log.e("IptvViewModel", "Seeding failed: ${e.message}")
        }
    }

    // --- Remote Push/Pull Subscription Feature ---
    private val _remotePushUrl = MutableStateFlow(prefs.getString("remote_push_url", "https://iptv-org.github.io/iptv/categories/news.m3u") ?: "https://iptv-org.github.io/iptv/categories/news.m3u")
    val remotePushUrl: StateFlow<String> = _remotePushUrl.asStateFlow()

    fun setRemotePushUrl(url: String) {
        _remotePushUrl.value = url
        prefs.edit().putString("remote_push_url", url).apply()
        triggerRemotePushSync()
    }

    fun triggerRemotePushSync() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                // Find or create "远程推送订阅源"
                val playlistsList = repository.playlists.first()
                val existing = playlistsList.firstOrNull { it.name == "远程推送订阅源" }
                val playlistId = if (existing != null) {
                    if (existing.url != _remotePushUrl.value) {
                        repository.updatePlaylist(existing.copy(url = _remotePushUrl.value))
                    }
                    existing.id
                } else {
                    val newPlaylist = Playlist(
                        name = "远程推送订阅源",
                        url = _remotePushUrl.value,
                        isLocal = false,
                        isEnabled = true
                    )
                    repository.insertPlaylist(newPlaylist).toInt()
                }

                val result = repository.importOrRefreshPlaylist(playlistId)
                if (result.isSuccess) {
                    _selectedPlaylistId.value = playlistId
                    Log.d("IptvViewModel", "Remote push subscription synced successfully.")
                } else {
                    _errorMessage.value = "远程同步失败: " + (result.exceptionOrNull()?.message ?: "未知原因")
                }
            } catch (e: Exception) {
                _errorMessage.value = "远程同步发生错误: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun handlePushedChannel(url: String, name: String) {
        viewModelScope.launch {
            if (url.contains(".m3u", ignoreCase = true) || url.contains(".xspf", ignoreCase = true)) {
                _remotePushUrl.value = url
                prefs.edit().putString("remote_push_url", url).apply()
                triggerRemotePushSync()
            } else {
                // Find or create "局域网推送" playlist
                val playlistsList = repository.playlists.first()
                val existingPlaylist = playlistsList.firstOrNull { it.name == "局域网推送" }
                val playlistId = if (existingPlaylist != null) {
                    existingPlaylist.id
                } else {
                    val newPlaylist = Playlist(
                        name = "局域网推送",
                        url = "",
                        isLocal = true,
                        isEnabled = true
                    )
                    repository.insertPlaylist(newPlaylist).toInt()
                }

                // Add channel
                val newChannel = Channel(
                    playlistId = playlistId,
                    name = name,
                    url = url,
                    groupTitle = "局域网推送",
                    isMulticast = url.startsWith("udp://", ignoreCase = true) || url.startsWith("rtp://", ignoreCase = true)
                )
                repository.insertChannels(listOf(newChannel))

                // Query back the newly inserted channel to play
                val channelsList = repository.allChannels.first()
                val savedChannel = channelsList.firstOrNull { it.url == url && it.playlistId == playlistId } ?: newChannel

                // Select and play
                _selectedChannel.value = savedChannel
                _currentGroup.value = "局域网推送"
                _selectedPlaylistId.value = playlistId
                Log.d("IptvViewModel", "LAN Push processed single channel successfully: $name -> $url")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        lanPushServer?.stop()
        lanPushServer = null
    }
}

