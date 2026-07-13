package com.example.data

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream

object XspfParser {
    fun parse(inputStream: InputStream, playlistId: Int): List<Channel> {
        val channels = mutableListOf<Channel>()
        try {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(inputStream, "UTF-8")
            
            var eventType = parser.eventType
            var currentTrack: TrackData? = null
            var text = ""
            
            while (eventType != XmlPullParser.END_DOCUMENT) {
                val tagName = parser.name
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (tagName.equals("track", ignoreCase = true)) {
                            currentTrack = TrackData()
                        }
                    }
                    XmlPullParser.TEXT -> {
                        text = parser.text
                    }
                    XmlPullParser.END_TAG -> {
                        if (currentTrack != null) {
                            when {
                                tagName.equals("location", ignoreCase = true) -> {
                                    currentTrack!!.location = text.trim()
                                }
                                tagName.equals("title", ignoreCase = true) -> {
                                    currentTrack!!.title = text.trim()
                                }
                                tagName.equals("image", ignoreCase = true) -> {
                                    currentTrack!!.image = text.trim()
                                }
                                tagName.equals("annotation", ignoreCase = true) -> {
                                    currentTrack!!.annotation = text.trim()
                                }
                                tagName.equals("vlc:option", ignoreCase = true) || tagName.equals("option", ignoreCase = true) -> {
                                    val trimmedOption = text.trim()
                                    if (trimmedOption.startsWith("group-title=", ignoreCase = true)) {
                                        currentTrack!!.groupTitle = trimmedOption.substring("group-title=".length).trim()
                                    }
                                }
                                tagName.equals("track", ignoreCase = true) -> {
                                    val location = currentTrack!!.location
                                    if (location.isNotEmpty()) {
                                        val isMulticast = location.startsWith("udp://", ignoreCase = true) || 
                                                          location.startsWith("rtp://", ignoreCase = true)
                                        val group = if (currentTrack!!.groupTitle.isNotEmpty()) {
                                            currentTrack!!.groupTitle
                                        } else if (currentTrack!!.annotation.isNotEmpty()) {
                                            currentTrack!!.annotation
                                        } else {
                                            "未分类"
                                        }
                                        
                                        channels.add(
                                            Channel(
                                                playlistId = playlistId,
                                                groupTitle = group,
                                                name = currentTrack!!.title.ifEmpty { "频道 ${channels.size + 1}" },
                                                url = location,
                                                tvgLogo = currentTrack!!.image,
                                                tvgName = currentTrack!!.title,
                                                isMulticast = isMulticast,
                                                channelNo = channels.size + 1
                                            )
                                        )
                                    }
                                    currentTrack = null
                                }
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                inputStream.close()
            } catch (e: Exception) {}
        }
        return channels
    }
    
    private class TrackData {
        var location: String = ""
        var title: String = ""
        var image: String = ""
        var annotation: String = ""
        var groupTitle: String = ""
    }
}
