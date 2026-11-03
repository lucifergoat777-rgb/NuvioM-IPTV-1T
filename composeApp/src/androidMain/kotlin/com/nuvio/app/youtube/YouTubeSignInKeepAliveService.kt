package com.nuvio.app.youtube

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper

/**
 * Keeps the app running while the YouTube sign-in page is open in the browser tab. Without it Android treats the app
 * as backgrounded and holds back its network access, so the approval isn't noticed until you come back yourself.
 */
class YouTubeSignInKeepAliveService : Service() {
    private val handler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager.getNotificationChannel(CHANNEL) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "YouTube sign-in", NotificationManager.IMPORTANCE_LOW),
            )
        }
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(this, CHANNEL) else Notification.Builder(this)
        val notification = builder
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Waiting for YouTube sign-in")
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        // A sign-in code stops working after a while, so never outlive it.
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ stopSelf() }, MAX_WAIT_MS)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "youtube_sign_in"
        private const val NOTIFICATION_ID = 7421
        private const val MAX_WAIT_MS = 10 * 60 * 1000L

        fun start(context: Context) {
            runCatching { context.startForegroundService(Intent(context, YouTubeSignInKeepAliveService::class.java)) }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, YouTubeSignInKeepAliveService::class.java)) }
        }
    }
}
