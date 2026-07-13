package com.example.data

import android.util.Log
import kotlinx.coroutines.*
import java.io.InputStream
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.MulticastSocket
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

class MulticastProxyServer(private val port: Int = 8123) {

    private var serverSocket: ServerSocket? = null
    private var proxyJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val activeStreams = ConcurrentHashMap<String, Job>()

    var isRunning = false
        private set

    fun start() {
        if (isRunning) return
        isRunning = true
        proxyJob = scope.launch {
            try {
                serverSocket = ServerSocket(port)
                Log.d("MulticastProxyServer", "Multicast Proxy Server started on port $port")
                while (isActive) {
                    val clientSocket = serverSocket?.accept() ?: break
                    scope.launch {
                        handleClient(clientSocket)
                    }
                }
            } catch (e: Exception) {
                Log.e("MulticastProxyServer", "Server exception: ${e.message}")
            }
        }
    }

    fun stop() {
        if (!isRunning) return
        isRunning = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {}
        serverSocket = null
        proxyJob?.cancel()
        activeStreams.forEach { (_, job) -> job.cancel() }
        activeStreams.clear()
        Log.d("MulticastProxyServer", "Multicast Proxy Server stopped")
    }

    private suspend fun handleClient(clientSocket: Socket) = withContext(Dispatchers.IO) {
        try {
            clientSocket.tcpNoDelay = true // Disable Nagle's algorithm for low-latency streaming
            val input = clientSocket.getInputStream()
            val output = clientSocket.getOutputStream()
            
            // Read HTTP request header
            val reader = input.bufferedReader()
            val requestLine = reader.readLine() ?: return@withContext
            Log.d("MulticastProxyServer", "Request: $requestLine")

            // Parse request like "GET /stream?addr=239.1.1.1:5000 HTTP/1.1" or "GET /udp/239.1.1.1:5000 HTTP/1.1"
            val parts = requestLine.split(" ")
            if (parts.size < 2 || !parts[0].equals("GET", ignoreCase = true)) {
                sendHttpError(output, 400, "Bad Request")
                return@withContext
            }

            val path = parts[1]
            var addressParam = ""
            if (path.startsWith("/stream?addr=")) {
                addressParam = path.substring("/stream?addr=".length)
            } else if (path.startsWith("/udp/")) {
                addressParam = path.substring("/udp/".length)
            }

            if (addressParam.isEmpty() || !addressParam.contains(":")) {
                sendHttpError(output, 400, "Invalid stream address. Format: addr=ip:port")
                return@withContext
            }

            val addrParts = addressParam.split(":")
            val ip = addrParts[0]
            val streamPort = addrParts[1].toIntOrNull() ?: 5000

            // Respond with HTTP 200 OK Chunked/MPEG-TS headers
            val responseHeaders = """
                HTTP/1.1 200 OK
                Content-Type: video/mp2t
                Connection: keep-alive
                Access-Control-Allow-Origin: *
                Cache-Control: no-cache
                
            """.trimIndent() + "\r\n"

            output.write(responseHeaders.toByteArray())
            output.flush()

            // Stream UDP packets to HTTP output stream
            streamMulticastToHttp(ip, streamPort, output, clientSocket)

        } catch (e: Exception) {
            Log.e("MulticastProxyServer", "Client handler error: ${e.message}")
        } finally {
            try {
                clientSocket.close()
            } catch (e: Exception) {}
        }
    }

    private suspend fun streamMulticastToHttp(
        ip: String,
        port: Int,
        output: OutputStream,
        clientSocket: Socket
    ) = withContext(Dispatchers.IO) {
        var multicastSocket: MulticastSocket? = null
        try {
            val group = InetAddress.getByName(ip)
            multicastSocket = MulticastSocket(port)
            multicastSocket.soTimeout = 4000 // Timeout if no packets
            multicastSocket.joinGroup(group)

            val buffer = ByteArray(65536) // Standard UDP buffer
            val packet = DatagramPacket(buffer, buffer.size)

            Log.d("MulticastProxyServer", "Streaming multicast $ip:$port to client ${clientSocket.inetAddress}")

            while (isActive && !clientSocket.isClosed && multicastSocket.isBound) {
                multicastSocket.receive(packet)
                if (packet.length > 0) {
                    output.write(packet.data, 0, packet.length)
                    output.flush() // Deliver packets immediately to avoid audio/video latency and stalling
                }
            }
        } catch (e: Exception) {
            Log.d("MulticastProxyServer", "Stopped streaming $ip:$port: ${e.message}")
        } finally {
            try {
                multicastSocket?.leaveGroup(InetAddress.getByName(ip))
                multicastSocket?.close()
            } catch (e: Exception) {}
        }
    }

    private fun sendHttpError(output: OutputStream, code: Int, message: String) {
        try {
            val response = """
                HTTP/1.1 $code Error
                Content-Type: text/plain
                Content-Length: ${message.length}
                Connection: close
                
                $message
            """.trimIndent()
            output.write(response.toByteArray())
            output.flush()
        } catch (e: Exception) {}
    }
}
