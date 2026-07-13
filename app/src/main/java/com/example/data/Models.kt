package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playlists")
data class Playlist(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val url: String = "",
    val filePath: String = "",
    val isLocal: Boolean = false,
    val userAgent: String = "",
    val requestMethod: String = "GET", // "GET" or "POST"
    val requestBody: String = "",
    val cookie: String = "",
    val isEnabled: Boolean = true,
    val lastUpdated: Long = 0L
)

@Entity(tableName = "channels")
data class Channel(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val playlistId: Int,
    val tvgId: String = "",
    val tvgName: String = "",
    val tvgLogo: String = "",
    val groupTitle: String = "未分类",
    val name: String,
    val url: String,
    val channelNo: Int = 0,
    val isFavorite: Boolean = false,
    val isMulticast: Boolean = false,
    val isLocked: Boolean = false
)

@Entity(tableName = "epg_programs")
data class EpgProgram(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val tvgId: String,
    val title: String,
    val startTime: Long, // milliseconds
    val endTime: Long, // milliseconds
    val description: String = "",
    val catchupUrl: String = ""
)

@Entity(tableName = "history_items")
data class HistoryItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val channelId: Int,
    val channelName: String,
    val channelUrl: String,
    val channelLogo: String = "",
    val watchedAt: Long = System.currentTimeMillis()
)
