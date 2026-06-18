package net.jgpower.gichan_land.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import net.jgpower.gichan_land.MainActivity
import net.jgpower.gichan_land.R
import net.jgpower.gichan_land.data.app.AppVisibilityState
import net.jgpower.gichan_land.data.walkie.WalkieGlobalState
import net.jgpower.gichan_land.data.walkie.WalkieMissedCallState
import net.jgpower.gichan_land.network.AppWebSocketManager
import net.jgpower.gichan_land.network.ServerConfig
import net.jgpower.gichan_land.network.WalkieSignalingClient
import net.jgpower.gichan_land.network.WalkieTalkieManager

class WebSocketForegroundService : Service() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var walkieWorkerId: String? = null

    private val walkiePingRunnable = object : Runnable {
        override fun run() {
            WalkieSignalingClient.ping()
            mainHandler.postDelayed(this, 5000L)
        }
    }

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
        val action = intent?.action
        val workerId = intent?.getStringExtra(EXTRA_WORKER_ID) ?: walkieWorkerId

        Log.d("WS_SERVICE", "onStartCommand action=$action workerId=$workerId")

        if (action == ACTION_TOGGLE_MIC ||
            action == ACTION_END_CALL ||
            action == ACTION_OPEN_WALKIE ||
            action == ACTION_REFRESH_NOTIFICATION
        ) {
            handleWalkieNotificationAction(action)
            return START_STICKY
        }

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

        startWalkieSignaling(workerId)

        return START_STICKY
    }

    private fun callFailedReasonText(reason: String?): String {
        return when (reason) {
            "ring_timeout" -> "상대방이 응답하지 않았습니다."
            "caller_cancelled" -> "연결 요청이 취소되었습니다."
            "callee_selected_other" -> "상대방이 다른 요청을 수락했습니다."
            "callee_busy" -> "상대방이 통화 중입니다."
            else -> "연결 실패"
        }
    }

    private fun callEndedStatusText(reason: String?): String {
        return when (reason) {
            "peer_ended" -> "상대방이 통화를 종료했습니다."
            "peer_disconnected" -> "상대방 연결이 끊어졌습니다."
            "self_ended" -> "통화 종료됨"
            else -> "통화 종료됨"
        }
    }

    override fun onDestroy() {
        Log.d("WS_SERVICE", "onDestroy")
        mainHandler.removeCallbacks(walkiePingRunnable)
        WalkieSignalingClient.setBackgroundListener(null)
        WalkieSignalingClient.disconnect()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.d("WS_SERVICE", "onTaskRemoved - keep foreground service running")
        super.onTaskRemoved(rootIntent)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }


    private fun startWalkieSignaling(workerId: String) {
        walkieWorkerId = workerId

        WalkieSignalingClient.setBackgroundListener(
            object : WalkieSignalingClient.Listener {
                override fun onConnected() {
                    WalkieSignalingClient.missedCallsGet(workerId)
                }

                override fun onDisconnected() {
                    // foreground service가 살아있는 동안 OkHttp/서버 연결이 끊기면
                    // 다음 서비스 재시작 또는 사용자의 화면 진입 시 다시 연결됩니다.
                }

                override fun onCallRinging(callId: String, toWorkerId: String) {
                    WalkieGlobalState.setRinging(callId, toWorkerId)
                    updateForegroundNotification()
                }

                override fun onIncomingCall(
                    callId: String,
                    fromWorkerId: String,
                    fromName: String?,
                    fromAreaGroup: String?
                ) {
                    WalkieGlobalState.upsertIncomingCall(
                        callId = callId,
                        fromWorkerId = fromWorkerId,
                        fromName = fromName,
                        fromAreaGroup = fromAreaGroup
                    )
                    updateForegroundNotification()

                    if (!AppVisibilityState.isForeground.value) {
                        AppNotificationManager.showWalkieIncomingCallNotification(
                            context = applicationContext,
                            callId = callId,
                            workerId = workerId,
                            fromWorkerId = fromWorkerId,
                            fromName = fromName,
                            fromAreaGroup = fromAreaGroup
                        )
                    }
                }

                override fun onCallActive(callId: String, peerWorkerId: String, talkerId: String?) {
                    WalkieGlobalState.setActive(callId, peerWorkerId)
                    updateForegroundNotification()
                }

                override fun onCallRejected(callId: String, byWorkerId: String?) {
                    val wasIncoming = WalkieGlobalState.pendingIncomingCalls.any { it.callId == callId }
                    val wasActiveOrOutgoing = WalkieGlobalState.activeCallId.value == callId

                    AppNotificationManager.cancelWalkieIncomingCallNotification(applicationContext, callId)

                    if (wasIncoming) {
                        WalkieGlobalState.removeIncomingCall(callId)
                    }

                    if (wasActiveOrOutgoing) {
                        WalkieTalkieManager.stopTransmit()
                        WalkieGlobalState.isMicOn.value = false
                        WalkieGlobalState.clearCall("통화 거절됨")
                    }
                    updateForegroundNotification()
                }

                override fun onCallFailed(callId: String, reason: String?, peerWorkerId: String?) {
                    val wasIncoming = WalkieGlobalState.pendingIncomingCalls.any { it.callId == callId }
                    val wasActiveOrOutgoing = WalkieGlobalState.activeCallId.value == callId

                    AppNotificationManager.cancelWalkieIncomingCallNotification(applicationContext, callId)

                    if (wasIncoming) {
                        WalkieGlobalState.removeIncomingCall(callId)
                    }

                    if (wasActiveOrOutgoing) {
                        WalkieTalkieManager.stopTransmit()
                        WalkieGlobalState.isMicOn.value = false
                        WalkieGlobalState.clearCall(callFailedReasonText(reason))
                    }
                    updateForegroundNotification()
                }

                override fun onCallEnded(
                    callId: String,
                    reason: String?,
                    byWorkerId: String?,
                    peerWorkerId: String?
                ) {
                    val wasIncoming = WalkieGlobalState.pendingIncomingCalls.any { it.callId == callId }
                    val wasActiveOrOutgoing = WalkieGlobalState.activeCallId.value == callId

                    AppNotificationManager.cancelWalkieIncomingCallNotification(applicationContext, callId)

                    if (wasIncoming) {
                        WalkieGlobalState.removeIncomingCall(callId)
                    }

                    if (wasActiveOrOutgoing) {
                        WalkieTalkieManager.stopTransmit()
                        WalkieGlobalState.isMicOn.value = false
                        if (reason == "peer_ended") {
                            WalkieGlobalState.showPeerEndedPopup.value = true
                        }
                        WalkieGlobalState.clearCall(callEndedStatusText(reason))
                    }
                    updateForegroundNotification()
                }

                override fun onEmergencyBroadcastStarted(
                    broadcastId: String,
                    fromWorkerId: String,
                    targetType: String?,
                    targetAreaGroup: String?
                ) {
                    WalkieTalkieManager.stopTransmit()
                    WalkieGlobalState.isMicOn.value = false
                    WalkieGlobalState.startEmergencyBroadcast(
                        broadcastId = broadcastId,
                        fromWorkerId = fromWorkerId,
                        targetType = targetType,
                        targetAreaGroup = targetAreaGroup
                    )
                    updateForegroundNotification()

                    if (!AppVisibilityState.isForeground.value) {
                        AppNotificationManager.showEmergencyBroadcastNotification(
                            context = applicationContext,
                            broadcastId = broadcastId,
                            fromWorkerId = fromWorkerId,
                            targetType = targetType,
                            targetAreaGroup = targetAreaGroup
                        )
                    }
                }

                override fun onEmergencyBroadcastEnded(broadcastId: String, reason: String?) {
                    WalkieGlobalState.endEmergencyBroadcast("긴급 전파 종료")
                    AppNotificationManager.cancelEmergencyBroadcastNotification(applicationContext, broadcastId)
                    updateForegroundNotification()
                }

                override fun onMicState(callId: String, workerId: String, micOn: Boolean) = Unit

                override fun onMissedCallsList(items: List<WalkieSignalingClient.MissedCallDto>) {
                    WalkieMissedCallState.replaceAll(items)
                }

                override fun onMissedCallAdded(item: WalkieSignalingClient.MissedCallDto) {
                    WalkieMissedCallState.upsert(item)
                }

                override fun onError(message: String) {
                    Log.d("WS_SERVICE", "walkie signaling error=$message")
                }
            }
        )

        WalkieSignalingClient.connect(
            serverBaseUrl = ServerConfig.getBaseHttpUrl(applicationContext),
            workerId = workerId
        )

        mainHandler.removeCallbacks(walkiePingRunnable)
        mainHandler.postDelayed(walkiePingRunnable, 5000L)
    }

    private fun handleWalkieNotificationAction(action: String?) {
        when (action) {
            ACTION_TOGGLE_MIC -> toggleMicFromNotification()
            ACTION_END_CALL -> endCallFromNotification()
            ACTION_OPEN_WALKIE -> openWalkieScreen()
            ACTION_REFRESH_NOTIFICATION -> updateForegroundNotification()
        }
    }

    private fun toggleMicFromNotification() {
        if (!WalkieGlobalState.isCallActive.value ||
            WalkieGlobalState.activeCallId.value.isNullOrBlank() ||
            WalkieGlobalState.isEmergencyBroadcastActive.value
        ) {
            updateForegroundNotification()
            return
        }

        if (WalkieGlobalState.isMicOn.value) {
            WalkieTalkieManager.stopTransmit()
            WalkieGlobalState.isMicOn.value = false
            WalkieGlobalState.lastStatusText.value = "통화 연결됨"
        } else {
            val started = WalkieTalkieManager.startTransmit(applicationContext)
            WalkieGlobalState.isMicOn.value = started
            WalkieGlobalState.lastStatusText.value = if (started) "내 MIC ON" else "송신 시작 실패"
        }

        updateForegroundNotification()
    }

    private fun endCallFromNotification() {
        val callId = WalkieGlobalState.activeCallId.value
        val workerId = walkieWorkerId

        if (!callId.isNullOrBlank() && !workerId.isNullOrBlank()) {
            WalkieSignalingClient.endCall(callId = callId, workerId = workerId)
        }

        WalkieTalkieManager.stopTransmit()
        WalkieGlobalState.isMicOn.value = false
        WalkieGlobalState.clearCall("통화 종료됨")
        updateForegroundNotification()
    }

    private fun openWalkieScreen() {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra("openWalkie", true)
        }
        startActivity(openIntent)
        updateForegroundNotification()
    }

    private fun updateForegroundNotification() {
        try {
            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(NOTIFICATION_ID, createForegroundNotification())
        } catch (e: Exception) {
            Log.e("WS_SERVICE", "updateForegroundNotification failed", e)
        }
    }

    private fun createForegroundNotification(): Notification {
        val isCallActive = WalkieGlobalState.isCallActive.value
        val callId = WalkieGlobalState.activeCallId.value
        val isEmergency = WalkieGlobalState.isEmergencyBroadcastActive.value

        val openPendingIntent = createServicePendingIntent(
            action = ACTION_OPEN_WALKIE,
            requestCode = 3001
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setContentIntent(openPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (isCallActive && !callId.isNullOrBlank() && !isEmergency) {
            val peer = WalkieGlobalState.activePeerWorkerId.value ?: "상대"
            val micText = if (WalkieGlobalState.isMicOn.value) "MIC ON" else "MIC OFF"

            builder
                .setContentTitle("기찬랜드 통화중")
                .setContentText("상대: $peer / $micText")
                .setStyle(NotificationCompat.BigTextStyle().bigText("상대: $peer / $micText"))
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .addAction(
                    0,
                    if (WalkieGlobalState.isMicOn.value) "MIC OFF" else "MIC ON",
                    createServicePendingIntent(ACTION_TOGGLE_MIC, 3002)
                )
                .addAction(
                    0,
                    "통화 종료",
                    createServicePendingIntent(ACTION_END_CALL, 3003)
                )
        } else {
            builder
                .setContentTitle("기찬랜드 알림 연결 중")
                .setContentText("백그라운드에서 알림을 수신하고 있습니다.")
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
        }

        return builder.build()
    }

    private fun createServicePendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, WebSocketForegroundService::class.java).apply {
            this.action = action
            walkieWorkerId?.let { putExtra(EXTRA_WORKER_ID, it) }
        }

        return PendingIntent.getService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
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

        private const val ACTION_TOGGLE_MIC = "net.jgpower.gichan_land.action.WALKIE_NOTIFICATION_TOGGLE_MIC"
        private const val ACTION_END_CALL = "net.jgpower.gichan_land.action.WALKIE_NOTIFICATION_END_CALL"
        private const val ACTION_OPEN_WALKIE = "net.jgpower.gichan_land.action.WALKIE_NOTIFICATION_OPEN"
        private const val ACTION_REFRESH_NOTIFICATION = "net.jgpower.gichan_land.action.WALKIE_NOTIFICATION_REFRESH"

        fun refreshWalkieNotification(context: android.content.Context) {
            val intent = Intent(context, WebSocketForegroundService::class.java).apply {
                action = ACTION_REFRESH_NOTIFICATION
            }
            try {
                context.startService(intent)
            } catch (_: Exception) {
                try {
                    androidx.core.content.ContextCompat.startForegroundService(context, intent)
                } catch (_: Exception) {
                    // 서비스가 실행 중이 아니면 무시합니다. 다음 시작 시 알림이 갱신됩니다.
                }
            }
        }

        private const val CHANNEL_ID = "gichan_land_ws_service_channel"
        private const val CHANNEL_NAME = "기찬랜드 연결 상태"
        private const val NOTIFICATION_ID = 2001
    }
}