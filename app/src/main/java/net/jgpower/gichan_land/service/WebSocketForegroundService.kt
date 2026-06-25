package net.jgpower.gichan_land.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import net.jgpower.gichan_land.MainActivity
import net.jgpower.gichan_land.R
import net.jgpower.gichan_land.data.app.AppVisibilityState
import net.jgpower.gichan_land.data.walkie.WalkieGlobalState
import net.jgpower.gichan_land.data.walkie.WalkieMissedCallState
import net.jgpower.gichan_land.data.walkie.WalkieTarget
import net.jgpower.gichan_land.data.walkie.WalkieTargetType
import net.jgpower.gichan_land.network.ApiServiceManager
import net.jgpower.gichan_land.network.AppWebSocketManager
import net.jgpower.gichan_land.network.ServerConfig
import net.jgpower.gichan_land.network.WalkieSignalingClient
import net.jgpower.gichan_land.network.WalkieTalkieManager
import net.jgpower.gichan_land.watch.TWatchBleNotifier
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking

class WebSocketForegroundService : Service() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var walkieWorkerId: String? = null

    private val walkiePingRunnable = object : Runnable {
        override fun run() {
            WalkieSignalingClient.ping()
            mainHandler.postDelayed(this, 5000L)
        }
    }

    private val walkieReconnectRunnable = object : Runnable {
        override fun run() {
            val workerId = walkieWorkerId
            if (!workerId.isNullOrBlank()) {
                try {
                    AppWebSocketManager.connect(
                        workerId = workerId,
                        context = applicationContext
                    )
                } catch (e: Exception) {
                    Log.e("WS_SERVICE", "app websocket health reconnect failed", e)
                }

                if (!WalkieSignalingClient.isConnected() && !WalkieSignalingClient.isConnecting()) {
                    Log.d("WS_SERVICE", "walkie websocket reconnect by health check")
                    connectWalkieSignalingIfInternalNetwork(workerId)
                } else {
                    WalkieSignalingClient.ping()
                }

                ensureWalkieReceiverReady(workerId)
            }

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

        TWatchBleNotifier.start(applicationContext)

        try {
            startForegroundWithMicType(createForegroundNotification())

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

    private fun terminateLocalWalkieStateOnSignalLost(message: String = "무전기 연결이 끊어졌습니다. 재연결 중...") {
        val pendingCallIds = WalkieGlobalState.pendingIncomingCalls.map { it.callId }

        pendingCallIds.forEach { callId ->
            AppNotificationManager.cancelWalkieIncomingCallNotification(applicationContext, callId)
        }

        try {
            WalkieTalkieManager.stopTransmit()
            WalkieTalkieManager.stop()
        } catch (e: Exception) {
            Log.e("WS_SERVICE", "walkie local cleanup failed", e)
        }

        WalkieGlobalState.isMicOn.value = false

        if (WalkieGlobalState.isEmergencyBroadcastActive.value) {
            WalkieGlobalState.endEmergencyBroadcast("무전기 연결 끊김")
        }

        if (WalkieGlobalState.activeCallId.value != null || WalkieGlobalState.pendingIncomingCalls.isNotEmpty()) {
            WalkieGlobalState.clearCall(message)
        } else {
            WalkieGlobalState.lastStatusText.value = message
        }

        updateForegroundNotification()
    }

    override fun onDestroy() {
        Log.d("WS_SERVICE", "onDestroy")
        mainHandler.removeCallbacks(walkiePingRunnable)
        mainHandler.removeCallbacks(walkieReconnectRunnable)
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



    private fun connectWalkieSignalingIfInternalNetwork(workerId: String): Boolean {
        val walkieBaseUrl = ServerConfig.getWalkieBaseHttpUrl(applicationContext)
        if (walkieBaseUrl.isNullOrBlank()) {
            Log.d("WS_SERVICE", "walkie network unavailable. block signaling connect")
            terminateLocalWalkieStateOnSignalLost(ServerConfig.walkieNetworkErrorMessage())
            WalkieSignalingClient.disconnect(sendDisconnect = false)
            mainHandler.removeCallbacks(walkiePingRunnable)
            return false
        }

        WalkieSignalingClient.connect(
            serverBaseUrl = walkieBaseUrl,
            workerId = workerId
        )
        return true
    }

    private fun startWalkieSignaling(workerId: String) {
        walkieWorkerId = workerId

        WalkieSignalingClient.setBackgroundListener(
            object : WalkieSignalingClient.Listener {
                override fun onConnected() {
                    WalkieSignalingClient.missedCallsGet(workerId)
                }

                override fun onDisconnected() {
                    terminateLocalWalkieStateOnSignalLost()
                    scheduleWalkieReconnect(workerId)
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
                    TWatchBleNotifier.notifyWalkieCall(
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
                    setWalkieTargetForPeer(peerWorkerId)
                    ensureWalkieReceiverReady(workerId)
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
                    TWatchBleNotifier.notifyEmergencyBroadcast(
                        broadcastId = broadcastId,
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
                    terminateLocalWalkieStateOnSignalLost("무전기 연결 오류. 재연결 중...")
                    scheduleWalkieReconnect(workerId)
                }
            }
        )

        connectWalkieSignalingIfInternalNetwork(workerId)

        ensureWalkieReceiverReady(workerId)

        mainHandler.removeCallbacks(walkiePingRunnable)
        mainHandler.removeCallbacks(walkieReconnectRunnable)
        mainHandler.postDelayed(walkieReconnectRunnable, 5000L)
    }


    private fun scheduleWalkieReconnect(workerId: String) {
        if (workerId.isBlank()) return
        mainHandler.postDelayed({
            if (!WalkieSignalingClient.isConnected() && !WalkieSignalingClient.isConnecting()) {
                Log.d("WS_SERVICE", "walkie websocket reconnect scheduled")
                connectWalkieSignalingIfInternalNetwork(workerId)
            }
        }, 1500L)
    }

    private fun ensureWalkieReceiverReady(workerId: String, afterReady: (() -> Unit)? = null) {
        if (workerId.isBlank()) return

        if (!ServerConfig.isWalkieNetworkAvailable(applicationContext)) {
            terminateLocalWalkieStateOnSignalLost(ServerConfig.walkieNetworkErrorMessage())
            afterReady?.let { callback -> mainHandler.post { callback.invoke() } }
            return
        }

        if (WalkieTalkieManager.isStarted()) {
            afterReady?.invoke()
            return
        }

        thread(name = "walkie-audio-init") {
            var started = false
            try {
                ApiServiceManager.init(applicationContext)
                val workers = runBlocking { ApiServiceManager.apiService.getOnlineWorkers() }
                val areaGroup = workers.firstOrNull { it.workerId?.trim() == workerId }
                    ?.primaryGroupName()
                    ?.trim()

                if (!areaGroup.isNullOrBlank()) {
                    WalkieTalkieManager.start(
                        context = applicationContext,
                        workerId = workerId,
                        areaGroup = areaGroup
                    )
                    started = true
                    Log.d("WS_SERVICE", "walkie receiver ready workerId=$workerId areaGroup=$areaGroup")
                } else {
                    Log.d("WS_SERVICE", "walkie receiver ready failed. areaGroup not found workerId=$workerId")
                }
            } catch (e: Exception) {
                Log.e("WS_SERVICE", "ensureWalkieReceiverReady failed", e)
            }

            if (started && afterReady != null) {
                mainHandler.post { afterReady.invoke() }
            } else if (!started && afterReady != null) {
                mainHandler.post {
                    WalkieGlobalState.lastStatusText.value = "MIC 준비 실패"
                    updateForegroundNotification()
                }
            }
        }
    }

    private fun setWalkieTargetForPeer(peerWorkerId: String) {
        if (peerWorkerId.isBlank()) return

        WalkieTalkieManager.setTarget(
            WalkieTarget(
                targetType = WalkieTargetType.USER,
                targetWorkerId = peerWorkerId,
                targetWorkerName = peerWorkerId,
                targetAreaGroup = null
            )
        )
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
        if (!ServerConfig.isWalkieNetworkAvailable(applicationContext)) {
            terminateLocalWalkieStateOnSignalLost(ServerConfig.walkieNetworkErrorMessage())
            return
        }

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
            updateForegroundNotification()
            return
        }

        startMicFromNotificationAfterReady()
    }

    private fun startMicFromNotificationAfterReady() {
        val workerId = walkieWorkerId
        val peerWorkerId = WalkieGlobalState.activePeerWorkerId.value

        if (workerId.isNullOrBlank() || peerWorkerId.isNullOrBlank()) {
            WalkieGlobalState.lastStatusText.value = "송신 준비 실패"
            updateForegroundNotification()
            return
        }

        setWalkieTargetForPeer(peerWorkerId)

        if (!WalkieTalkieManager.isStarted()) {
            WalkieGlobalState.lastStatusText.value = "MIC 준비 중"
            updateForegroundNotification()
            ensureWalkieReceiverReady(workerId) {
                tryStartMicFromNotification()
            }
            return
        }

        tryStartMicFromNotification()
    }

    private fun tryStartMicFromNotification() {
        val peerWorkerId = WalkieGlobalState.activePeerWorkerId.value
        if (!peerWorkerId.isNullOrBlank()) {
            setWalkieTargetForPeer(peerWorkerId)
        }

        ensureForegroundForMic()
        val started = WalkieTalkieManager.startTransmit(applicationContext)
        WalkieGlobalState.isMicOn.value = started
        WalkieGlobalState.lastStatusText.value = if (started) "내 MIC ON" else "송신 시작 실패"
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

    private fun startForegroundWithMicType(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun ensureForegroundForMic() {
        TWatchBleNotifier.start(applicationContext)

        try {
            startForegroundWithMicType(createForegroundNotification())
        } catch (e: Exception) {
            Log.e("WS_SERVICE", "ensureForegroundForMic failed", e)
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
            val contentText = "상대: $peer / $micText"
            val compactView = createWalkieCallCompactNotificationView(peer, micText)

            builder
                .setContentTitle("기찬랜드 통화중")
                .setContentText(contentText)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setCustomContentView(compactView)
                .setStyle(NotificationCompat.DecoratedCustomViewStyle())
        } else {
            builder
                .setContentTitle("기찬랜드 알림 연결 중")
                .setContentText("백그라운드에서 알림을 수신하고 있습니다.")
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
        }

        return builder.build()
    }

    private fun createWalkieCallCompactNotificationView(
        peer: String,
        micText: String
    ): RemoteViews {
        return RemoteViews(packageName, R.layout.notification_walkie_call_compact).apply {
            setTextViewText(R.id.walkie_notification_title, "기찬랜드 통화중")
            setTextViewText(R.id.walkie_notification_body, "상대: $peer / $micText")
            setImageViewResource(R.id.walkie_notification_mic_button, R.drawable.ic_walkie_mic_24)
            setImageViewResource(R.id.walkie_notification_end_button, R.drawable.ic_walkie_call_end_24)
            setOnClickPendingIntent(
                R.id.walkie_notification_mic_button,
                createServicePendingIntent(ACTION_TOGGLE_MIC, 3002)
            )
            setOnClickPendingIntent(
                R.id.walkie_notification_end_button,
                createServicePendingIntent(ACTION_END_CALL, 3003)
            )
        }
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