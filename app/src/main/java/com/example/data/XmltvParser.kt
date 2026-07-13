package com.example.data

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

object XmltvParser {
    // Parse formats like "20260712080000 +0000" or "20260712080000"
    private val dateFormat1 = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US)
    private val dateFormat2 = SimpleDateFormat("yyyyMMddHHmmss", Locale.US)

    fun parse(inputStream: InputStream): List<EpgProgram> {
        val programs = mutableListOf<EpgProgram>()
        val parser = Xml.newPullParser()
        try {
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(inputStream, null)
            var eventType = parser.eventType
            var currentProgram: TempProgram? = null

            while (eventType != XmlPullParser.END_DOCUMENT) {
                val name = parser.name
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (name.equals("programme", ignoreCase = true)) {
                            val startAttr = parser.getAttributeValue(null, "start") ?: ""
                            val stopAttr = parser.getAttributeValue(null, "stop") ?: ""
                            val channelAttr = parser.getAttributeValue(null, "channel") ?: ""
                            currentProgram = TempProgram(
                                channel = channelAttr,
                                startStr = startAttr,
                                stopStr = stopAttr
                            )
                        } else if (currentProgram != null) {
                            if (name.equals("title", ignoreCase = true)) {
                                currentProgram.title = parser.nextText().trim()
                            } else if (name.equals("desc", ignoreCase = true)) {
                                currentProgram.description = parser.nextText().trim()
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (name.equals("programme", ignoreCase = true) && currentProgram != null) {
                            val startTime = parseDate(currentProgram.startStr)
                            val endTime = parseDate(currentProgram.stopStr)
                            if (currentProgram.channel.isNotEmpty() && currentProgram.title.isNotEmpty()) {
                                programs.add(
                                    EpgProgram(
                                        tvgId = currentProgram.channel,
                                        title = currentProgram.title,
                                        startTime = startTime,
                                        endTime = endTime,
                                        description = currentProgram.description
                                    )
                                )
                            }
                            currentProgram = null
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
            } catch (e: Exception) {
                // ignore
            }
        }
        return programs
    }

    private fun parseDate(dateStr: String): Long {
        val clean = dateStr.trim()
        if (clean.isEmpty()) return System.currentTimeMillis()
        return try {
            if (clean.contains(" ")) {
                dateFormat1.parse(clean)?.time ?: System.currentTimeMillis()
            } else {
                dateFormat2.parse(clean)?.time ?: System.currentTimeMillis()
            }
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }

    private data class TempProgram(
        val channel: String,
        val startStr: String,
        val stopStr: String,
        var title: String = "",
        var description: String = ""
    )
}
