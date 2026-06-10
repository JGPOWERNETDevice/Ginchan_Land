package net.jgpower.gichan_land.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import net.jgpower.gichan_land.R
import net.jgpower.gichan_land.network.AppWebSocketManager

class WebSocketForegroundService : Service() {

    override fun onCreate() {
        super.onCreate()
        Log.d("WS_SERVICE", "onCreate")
        createChannel()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        val workerId = intent?.getStringExtra(EXTRA_WORKER_ID)

        Log.d("WS_SERVICE", "onStartCommand workerId=$workerId")

        if (workerId.isNullOrBlank()) {
            Log.d("WS_SERVICE", "workerId is blank. stopSelf")
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            startForeground(
                NOTIFICATION_ID,
                createForegroundNotification()
            )

            Log.d("WS_SERVICE", "startForeground success")
        } catch (e: Exception) {
            Log.e("WS_SERVICE", "startForeground failed", e)
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            AppWebSocketManager.connect(
                workerId = workerId,
                context = applicationContext
            )

            Log.d("WS_SERVICE", "AppWebSocketManager.connect called")
        } catch (e: Exception) {
            Log.e("WS_SERVICE", "connect failed", e)
        }

        return START_STICKY
    }

    override fun onDestroy() {
        Log.d("WS_SERVICE", "onDestroy - do not disconnect here")
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.d("WS_SERVICE", "onTaskRemoved - keep foreground service running")
        super.onTaskRemoved(rootIntent)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createForegroundNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("기찬랜드 알림 연결 중")
            .setContentText("백그라운드에서 알림을 수신하고 있습니다.")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "백그라운드 알림 수신 연결 상태"
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)

            Log.d("WS_SERVICE", "notification channel created")
        }
    }

    companion object {
        const val EXTRA_WORKER_ID = "workerId"

        private const val CHANNEL_ID = "gichan_land_ws_service_channel"
        private const val CHANNEL_NAME = "기찬랜드 연결 상태"
        private const val NOTIFICATION_ID = 2001
    }
}