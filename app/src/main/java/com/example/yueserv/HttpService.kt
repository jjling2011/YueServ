package com.example.yueserv

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log

private const val TAG = "HttpService"

class HttpService : Service() {

    private var server: AndroidHttpServer? = null
    private var port: Int = 3000


    override fun onCreate() {
        super.onCreate()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "http_server_channel"
            val channel = NotificationChannel(
                channelId, getString(R.string.http_server), NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
            val notification = Notification.Builder(this, channelId)
                .setContentTitle(getString(R.string.http_server_running))
                .setContentText("${getString(R.string.serving_content_on_port)}$port")
                .setSmallIcon(android.R.drawable.ic_dialog_info).build()
            startForeground(1, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            port = intent?.getIntExtra("PORT", 3000) ?: 3000
            server = AndroidHttpServer(
                port,
                applicationContext,
            )
            server?.start()
        } catch (e: Exception) {
            Log.e(TAG, "onStartCommand: ${e.message}")
        }
        return START_STICKY
    }


    override fun onDestroy() {
        server?.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

}