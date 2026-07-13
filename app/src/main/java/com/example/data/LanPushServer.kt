package com.example.data

import android.util.Log
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder

class LanPushServer(
    private val port: Int = 19150,
    private val onPushReceived: (url: String, name: String) -> Unit
) {
    private var serverSocket: ServerSocket? = null
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    var isRunning = false
        private set

    fun start() {
        if (isRunning) return
        isRunning = true
        job = scope.launch {
            try {
                serverSocket = ServerSocket(port)
                Log.d("LanPushServer", "LAN Push Server started on port $port")
                while (isActive) {
                    val clientSocket = serverSocket?.accept() ?: break
                    scope.launch {
                        handleClient(clientSocket)
                    }
                }
            } catch (e: Exception) {
                Log.e("LanPushServer", "Error in ServerSocket: ${e.message}")
            }
        }
    }

    fun stop() {
        if (!isRunning) return
        isRunning = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            Log.e("LanPushServer", "Error closing ServerSocket: ${e.message}")
        }
        serverSocket = null
        job?.cancel()
        Log.d("LanPushServer", "LAN Push Server stopped")
    }

    private fun handleClient(socket: Socket) {
        var outputStream: OutputStream? = null
        var reader: BufferedReader? = null
        try {
            socket.soTimeout = 5000
            val inputStream = socket.getInputStream()
            outputStream = socket.getOutputStream()
            reader = BufferedReader(InputStreamReader(inputStream, "UTF-8"))

            val requestLine = reader.readLine() ?: return
            Log.d("LanPushServer", "Received request: $requestLine")

            val parts = requestLine.split(" ")
            if (parts.size < 2) {
                sendError(outputStream, 400, "Bad Request")
                return
            }

            val method = parts[0]
            val path = parts[1]

            if (method.equals("GET", ignoreCase = true)) {
                if (path == "/" || path == "/index.html") {
                    sendHtmlResponse(outputStream, getWebHtml())
                } else if (path.startsWith("/push")) {
                    // Extract query parameters
                    val urlIndex = path.indexOf("?")
                    var pushUrl = ""
                    var pushName = "局域网推送"

                    if (urlIndex != -1) {
                        val query = path.substring(urlIndex + 1)
                        val params = parseQueryParams(query)
                        pushUrl = params["url"] ?: ""
                        pushName = params["name"] ?: "局域网推送"
                    }

                    if (pushUrl.isNotEmpty()) {
                        Log.d("LanPushServer", "Push URL received: $pushUrl, Name: $pushName")
                        onPushReceived(pushUrl, pushName)
                        sendJsonResponse(outputStream, "{\"status\":\"success\",\"message\":\"Pushed successfully\"}")
                    } else {
                        sendJsonResponse(outputStream, "{\"status\":\"error\",\"message\":\"Missing url parameter\"}")
                    }
                } else {
                    sendError(outputStream, 404, "Not Found")
                }
            } else {
                sendError(outputStream, 405, "Method Not Allowed")
            }
        } catch (e: Exception) {
            Log.e("LanPushServer", "Error handling client connection: ${e.message}")
        } finally {
            try {
                reader?.close()
                outputStream?.close()
                socket.close()
            } catch (e: Exception) {}
        }
    }

    private fun parseQueryParams(query: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val pairs = query.split("&")
        for (pair in pairs) {
            val idx = pair.indexOf("=")
            if (idx > 0) {
                try {
                    val key = URLDecoder.decode(pair.substring(0, idx), "UTF-8")
                    val value = URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
                    result[key] = value
                } catch (e: Exception) {
                    Log.e("LanPushServer", "Error decoding query parameter", e)
                }
            }
        }
        return result
    }

    private fun sendHtmlResponse(out: OutputStream, html: String) {
        val bytes = html.toByteArray(charset("UTF-8"))
        val response = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: text/html; charset=utf-8\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Connection: close\r\n\r\n"
        out.write(response.toByteArray(charset("UTF-8")))
        out.write(bytes)
        out.flush()
    }

    private fun sendJsonResponse(out: OutputStream, json: String) {
        val bytes = json.toByteArray(charset("UTF-8"))
        val response = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: application/json; charset=utf-8\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Connection: close\r\n\r\n"
        out.write(response.toByteArray(charset("UTF-8")))
        out.write(bytes)
        out.flush()
    }

    private fun sendError(out: OutputStream, code: Int, message: String) {
        val body = "<h1>$code $message</h1>"
        val bytes = body.toByteArray(charset("UTF-8"))
        val response = "HTTP/1.1 $code $message\r\n" +
                "Content-Type: text/html; charset=utf-8\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Connection: close\r\n\r\n"
        out.write(response.toByteArray(charset("UTF-8")))
        out.write(bytes)
        out.flush()
    }

    private fun getWebHtml(): String {
        return """
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>IPTV 局域网远程推送</title>
    <style>
        body {
            background-color: #121212;
            color: #E0E0E0;
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
            margin: 0;
            padding: 20px;
            display: flex;
            justify-content: center;
            align-items: center;
            min-height: 100vh;
            box-sizing: border-box;
        }
        .container {
            background-color: #1E1E1E;
            border-radius: 16px;
            box-shadow: 0 8px 32px rgba(0, 0, 0, 0.5);
            padding: 30px;
            width: 100%;
            max-width: 500px;
            border: 1px solid #2D2D2D;
        }
        h2 {
            text-align: center;
            color: #BB86FC;
            margin-top: 0;
            margin-bottom: 24px;
            font-size: 24px;
        }
        .form-group {
            margin-bottom: 20px;
        }
        label {
            display: block;
            margin-bottom: 8px;
            font-size: 14px;
            color: #A0A0A0;
            font-weight: 500;
        }
        input[type="text"] {
            width: 100%;
            padding: 12px;
            background-color: #2A2A2A;
            border: 1px solid #3D3D3D;
            border-radius: 8px;
            color: #FFFFFF;
            font-size: 14px;
            box-sizing: border-box;
            transition: border-color 0.3s;
        }
        input[type="text"]:focus {
            outline: none;
            border-color: #BB86FC;
        }
        button {
            width: 100%;
            padding: 14px;
            background-color: #BB86FC;
            color: #121212;
            border: none;
            border-radius: 8px;
            font-size: 16px;
            font-weight: bold;
            cursor: pointer;
            transition: background-color 0.3s, transform 0.1s;
        }
        button:hover {
            background-color: #D7AEFB;
        }
        button:active {
            transform: scale(0.98);
        }
        .toast {
            visibility: hidden;
            min-width: 250px;
            background-color: #4CAF50;
            color: #fff;
            text-align: center;
            border-radius: 8px;
            padding: 16px;
            position: fixed;
            z-index: 1;
            left: 50%;
            bottom: 30px;
            transform: translateX(-50%);
            box-shadow: 0 4px 12px rgba(0,0,0,0.3);
        }
        .toast.show {
            visibility: visible;
            animation: fadein 0.5s, fadeout 0.5s 2.5s;
        }
        @keyframes fadein {
            from { bottom: 0; opacity: 0; }
            to { bottom: 30px; opacity: 1; }
        }
        @keyframes fadeout {
            from { bottom: 30px; opacity: 1; }
            to { bottom: 0; opacity: 0; }
        }
        .history {
            margin-top: 30px;
            border-top: 1px solid #2D2D2D;
            padding-top: 20px;
        }
        .history h3 {
            font-size: 16px;
            color: #BB86FC;
            margin-top: 0;
            margin-bottom: 12px;
        }
        .history-list {
            list-style: none;
            padding: 0;
            margin: 0;
            max-height: 180px;
            overflow-y: auto;
        }
        .history-item {
            display: flex;
            justify-content: space-between;
            align-items: center;
            padding: 10px;
            background-color: #252525;
            border-radius: 6px;
            margin-bottom: 8px;
            font-size: 13px;
        }
        .history-info {
            flex-grow: 1;
            margin-right: 10px;
            overflow: hidden;
        }
        .history-name {
            font-weight: bold;
            color: #E0E0E0;
            white-space: nowrap;
            text-overflow: ellipsis;
            overflow: hidden;
        }
        .history-url {
            color: #888;
            font-size: 11px;
            white-space: nowrap;
            text-overflow: ellipsis;
            overflow: hidden;
        }
        .history-play {
            background-color: #03DAC6;
            color: #121212;
            padding: 4px 8px;
            border-radius: 4px;
            cursor: pointer;
            font-size: 11px;
            font-weight: bold;
            border: none;
            width: auto;
        }
        .history-play:hover {
            background-color: #66FFF3;
        }
        .quick-links {
            margin-top: 15px;
            display: flex;
            gap: 8px;
            flex-wrap: wrap;
            margin-bottom: 15px;
        }
        .quick-tag {
            background-color: #2D2D2D;
            padding: 4px 10px;
            border-radius: 12px;
            font-size: 11px;
            cursor: pointer;
            color: #BB86FC;
            border: 1px solid #3D3D3D;
        }
        .quick-tag:hover {
            background-color: #3D3D3D;
        }
    </style>
</head>
<body>
    <div class="container">
        <h2>IPTV 局域网远程推送播放</h2>
        <div class="form-group">
            <label for="url">视频 / IPTV 播放地址 (支持 HTTP/UDP/RTSP/M3U8 等):</label>
            <input type="text" id="url" placeholder="请输入播放链接..." required>
        </div>
        <div class="form-group">
            <label for="name">频道名称 (例如：北京卫视4K):</label>
            <input type="text" id="name" placeholder="局域网推送">
        </div>
        <button onclick="pushUrl()">立即推送播放</button>

        <div class="quick-links">
            <span class="quick-tag" onclick="setQuick('http://192.168.31.1:7088/udp/235.254.197.50:7980', '北京卫视4K')">北京卫视4K</span>
            <span class="quick-tag" onclick="setQuick('http://192.168.31.1:7088/udp/235.254.199.52:7980', 'CCTV-2 (AVS+/DRA)')">CCTV-2</span>
            <span class="quick-tag" onclick="setQuick('https://iptv-org.github.io/iptv/categories/news.m3u', '测试新闻订阅')">测试订阅源</span>
        </div>

        <div class="history">
            <h3>推送历史记录</h3>
            <ul id="historyList" class="history-list"></ul>
        </div>
    </div>

    <div id="toast" class="toast">推送成功！电视已开始播放</div>

    <script>
        function setQuick(url, name) {
            document.getElementById('url').value = url;
            document.getElementById('name').value = name;
        }

        function showToast(text, isError) {
            var toast = document.getElementById("toast");
            toast.innerText = text;
            if (isError) {
                toast.style.backgroundColor = "#F44336";
            } else {
                toast.style.backgroundColor = "#4CAF50";
            }
            toast.className = "toast show";
            setTimeout(function(){ toast.className = toast.className.replace("show", ""); }, 3000);
        }

        function loadHistory() {
            var history = JSON.parse(localStorage.getItem('push_history') || '[]');
            var list = document.getElementById('historyList');
            list.innerHTML = '';
            if (history.length === 0) {
                list.innerHTML = '<li style="color:#666; font-size:12px; text-align:center; padding: 10px;">暂无推送记录</li>';
                return;
            }
            history.forEach(function(item, index) {
                var li = document.createElement('li');
                li.className = 'history-item';
                
                var info = document.createElement('div');
                info.className = 'history-info';
                
                var nameDiv = document.createElement('div');
                nameDiv.className = 'history-name';
                nameDiv.innerText = item.name;
                
                var urlDiv = document.createElement('div');
                urlDiv.className = 'history-url';
                urlDiv.innerText = item.url;
                
                info.appendChild(nameDiv);
                info.appendChild(urlDiv);
                
                var playBtn = document.createElement('button');
                playBtn.className = 'history-play';
                playBtn.innerText = '推送';
                playBtn.onclick = function() {
                    document.getElementById('url').value = item.url;
                    document.getElementById('name').value = item.name;
                    pushUrl();
                };
                
                li.appendChild(info);
                li.appendChild(playBtn);
                list.appendChild(li);
            });
        }

        function saveHistory(url, name) {
            var history = JSON.parse(localStorage.getItem('push_history') || '[]');
            history = history.filter(function(item) {
                return item.url !== url;
            });
            history.unshift({url: url, name: name});
            if (history.length > 10) history.pop();
            localStorage.setItem('push_history', JSON.stringify(history));
            loadHistory();
        }

        function pushUrl() {
            var url = document.getElementById('url').value.trim();
            var name = document.getElementById('name').value.trim() || '局域网推送';
            if (!url) {
                showToast('请输入播放链接！', true);
                return;
            }

            var pushUrl = '/push?url=' + encodeURIComponent(url) + '&name=' + encodeURIComponent(name);
            fetch(pushUrl)
                .then(function(response) {
                    return response.json();
                })
                .then(function(data) {
                    if (data.status === 'success') {
                        showToast('推送成功！电视已开始播放: ' + name, false);
                        saveHistory(url, name);
                    } else {
                        showToast('推送失败: ' + data.message, true);
                    }
                })
                .catch(function(err) {
                    showToast('无法连接到设备，请确保在同一局域网下', true);
                });
        }

        loadHistory();
    </script>
</body>
</html>
        """.trimIndent()
    }
}
