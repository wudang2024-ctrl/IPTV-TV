package com.example.data

import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Binder
import android.os.IBinder
import android.util.Log

class MulticastProxyService : Service() {

    private var proxyServer: MulticastProxyServer? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): MulticastProxyService = this@MulticastProxyService
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("MulticastProxyService", "MulticastProxyService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val port = intent?.getIntExtra("PROXY_PORT", 8123) ?: 8123
        Log.d("MulticastProxyService", "Starting multicast service on port $port")

        // 1. Acquire Multicast Lock to allow UDP packet reception
        try {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            if (wifiManager != null) {
                multicastLock = wifiManager.createMulticastLock("IptvMulticastLock").apply {
                    setReferenceCounted(true)
                    acquire()
                }
                Log.d("MulticastProxyService", "Acquired Multicast Lock successfully")
            }
        } catch (e: Exception) {
            Log.e("MulticastProxyService", "Failed to acquire multicast lock: ${e.message}")
        }

        // 2. Start the local proxy server
        if (proxyServer == null) {
            proxyServer = MulticastProxyServer(port)
            proxyServer?.start()
            Log.d("MulticastProxyService", "MulticastProxyServer started on port $port")
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        Log.d("MulticastProxyService", "Destroying MulticastProxyService")
        
        // Stop Proxy Server
        try {
            proxyServer?.stop()
            proxyServer = null
        } catch (e: Exception) {
            Log.e("MulticastProxyService", "Error stopping proxy server: ${e.message}")
        }

        // Release Multicast Lock
        try {
            if (multicastLock?.isHeld == true) {
                multicastLock?.release()
                Log.d("MulticastProxyService", "Released Multicast Lock")
            }
        } catch (e: Exception) {
            Log.e("MulticastProxyService", "Error releasing multicast lock: ${e.message}")
        }

        super.onDestroy()
    }
}
