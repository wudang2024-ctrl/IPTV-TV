package com.example.data

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

object M3uParser {
    fun parse(inputStream: InputStream, playlistId: Int): List<Channel> {
        val channels = mutableListOf<Channel>()
        val reader = BufferedReader(InputStreamReader(inputStream))
        var line: String?
        var currentTvgId = ""
        var currentTvgName = ""
        var currentTvgLogo = ""
        var currentGroupTitle = "未分类"
        var currentChannelNo = 0

        try {
            while (reader.readLine().also { line = it } != null) {
                val trimmed = line!!.trim()
                if (trimmed.isEmpty()) continue

                if (trimmed.startsWith("#EXTINF:")) {
                    // Extract info
                    currentTvgId = extractAttribute(trimmed, "tvg-id")
                    currentTvgName = extractAttribute(trimmed, "tvg-name")
                    currentTvgLogo = extractAttribute(trimmed, "tvg-logo")
                    currentGroupTitle = extractAttribute(trimmed, "group-title").ifEmpty { "未分类" }
                    
                    val channelNoStr = extractAttribute(trimmed, "tvg-chno")
                    currentChannelNo = channelNoStr.toIntOrNull() ?: 0

                    if (currentTvgName.isEmpty()) {
                        // fallback tvg-name from after comma
                        val commaIdx = trimmed.lastIndexOf(',')
                        if (commaIdx != -1 && commaIdx < trimmed.length - 1) {
                            currentTvgName = trimmed.substring(commaIdx + 1).trim()
                        }
                    }
                } else if (!trimmed.startsWith("#")) {
                    // This line is a URL
                    val name = if (currentTvgName.isNotEmpty()) currentTvgName else "频道 ${channels.size + 1}"
                    val isMulticast = trimmed.startsWith("udp://", ignoreCase = true) || trimmed.startsWith("rtp://", ignoreCase = true)
                    
                    channels.add(
                        Channel(
                            playlistId = playlistId,
                            tvgId = currentTvgId,
                            tvgName = currentTvgName,
                            tvgLogo = currentTvgLogo,
                            groupTitle = currentGroupTitle,
                            name = name,
                            url = trimmed,
                            channelNo = if (currentChannelNo > 0) currentChannelNo else channels.size + 1,
                            isMulticast = isMulticast
                        )
                    )
                    // Reset
                    currentTvgId = ""
                    currentTvgName = ""
                    currentTvgLogo = ""
                    currentGroupTitle = "未分类"
                    currentChannelNo = 0
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                reader.close()
            } catch (e: Exception) {
                // ignore
            }
        }
        return channels
    }

    private fun extractAttribute(extinfLine: String, attributeName: String): String {
        val key = "$attributeName=\""
        val startIdx = extinfLine.indexOf(key)
        if (startIdx == -1) return ""
        val valStart = startIdx + key.length
        val endIdx = extinfLine.indexOf('"', valStart)
        if (endIdx == -1) return ""
        return extinfLine.substring(valStart, endIdx)
    }
}
