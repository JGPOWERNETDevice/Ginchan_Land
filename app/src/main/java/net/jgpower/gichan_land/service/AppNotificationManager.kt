package net.jgpower.gichan_land.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import net.jgpower.gichan_land.MainActivity
import net.jgpower.gichan_land.R
import net.jgpower.gichan_land.data.alert.WorkerAlert
import net.jgpower.gichan_land.data.textalert.TextAlert

object AppNotificationManager {

    private const val CHANNEL_ID = "gichan_land_alert_channel"
    private const val CHANNEL_NAME = "기찬랜드 알림"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "위험 알림 및 중앙 관제 알림"
            }

            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    fun showAlertNotification(
        context: Context,
        alert: WorkerAlert
    ) {
        createChannel(context)

        if (!hasNotificationPermission(context)) {
            return
        }

        val pendingIntent = createAlertPendingIntent(
            context = context,
            alert = alert,
            requestCode = alert.alertId.hashCode()
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("위험 알림 발생")
            .setContentText(alert.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(alert.message))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        NotificationManagerCompat.from(context).notify(
            alert.alertId.hashCode(),
            notification
        )
    }

    fun showTextAlertNotification(
        context: Context,
        alert: TextAlert
    ) {
        createChannel(context)

        if (!hasNotificationPermission(context)) {
            return
        }

        val pendingIntent = createMainPendingIntent(
            context = context,
            requestCode = alert.textAlertId.hashCode()
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("중앙 관제 알림")
            .setContentText(alert.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(alert.message))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        NotificationManagerCompat.from(context).notify(
            alert.textAlertId.hashCode(),
            notification
        )
    }

    fun showWalkieIncomingCallNotification(
        context: Context,
        callId: String,
        workerId: String,
        fromWorkerId: String,
        fromName: String?,
        fromAreaGroup: String?
    ) {
        createChannel(context)

        if (!hasNotificationPermission(context)) {
            return
        }

        val title = "무전 연결 요청"
        val name = fromName?.takeIf { it.isNotBlank() } ?: fromWorkerId
        val message = if (!fromAreaGroup.isNullOrBlank()) {
            "$name / $fromAreaGroup"
        } else {
            name
        }

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra("openWalkie", true)
            putExtra("walkieCallId", callId)
            putExtra("walkieFromWorkerId", fromWorkerId)
        }

        val openPendingIntent = PendingIntent.getActivity(
            context,
            ("open-$callId").hashCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val acceptIntent = Intent(context, WalkieIncomingCallActionReceiver::class.java).apply {
            action = WalkieIncomingCallActionReceiver.ACTION_ACCEPT
            putExtra(WalkieIncomingCallActionReceiver.EXTRA_CALL_ID, callId)
            putExtra(WalkieIncomingCallActionReceiver.EXTRA_WORKER_ID, workerId)
        }

        val acceptPendingIntent = PendingIntent.getBroadcast(
            context,
            ("accept-$callId").hashCode(),
            acceptIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val rejectIntent = Intent(context, WalkieIncomingCallActionReceiver::class.java).apply {
            action = WalkieIncomingCallActionReceiver.ACTION_REJECT
            putExtra(WalkieIncomingCallActionReceiver.EXTRA_CALL_ID, callId)
            putExtra(WalkieIncomingCallActionReceiver.EXTRA_WORKER_ID, workerId)
        }

        val rejectPendingIntent = PendingIntent.getBroadcast(
            context,
            ("reject-$callId").hashCode(),
            rejectIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(openPendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(0, "수신", acceptPendingIntent)
            .addAction(0, "거절", rejectPendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(
            callId.hashCode(),
            notification
        )
    }

    fun cancelWalkieIncomingCallNotification(context: Context, callId: String) {
        NotificationManagerCompat.from(context).cancel(callId.hashCode())
    }


    fun showEmergencyBroadcastNotification(
        context: Context,
        broadcastId: String,
        fromWorkerId: String,
        targetType: String?,
        targetAreaGroup: String?
    ) {
        createChannel(context)

        if (!hasNotificationPermission(context)) {
            return
        }

        val title = "긴급 전파 수신"
        val targetText = when (targetType) {
            "GROUP" -> "대상 그룹: ${targetAreaGroup ?: "-"}"
            "ALL" -> "대상: 전체"
            else -> "대상: 개별"
        }
        val message = "중앙관제 긴급 방송을 수신 중입니다. $targetText"

        val openPendingIntent = createMainPendingIntent(
            context = context,
            requestCode = ("emergency-$broadcastId").hashCode()
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(openPendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        NotificationManagerCompat.from(context).notify(
            ("emergency-$broadcastId").hashCode(),
            notification
        )
    }

    fun cancelEmergencyBroadcastNotification(context: Context, broadcastId: String) {
        NotificationManagerCompat.from(context).cancel(("emergency-$broadcastId").hashCode())
    }


    private fun createAlertPendingIntent(
        context: Context,
        alert: WorkerAlert,
        requestCode: Int
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra("alertId", alert.alertId)
            putExtra("workerId", alert.receiverId)
            putExtra("openActionReport", alert.status == "조치 중")
        }

        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createMainPendingIntent(
        context: Context,
        requestCode: Int
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_NEW_TASK
        }

        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
}
