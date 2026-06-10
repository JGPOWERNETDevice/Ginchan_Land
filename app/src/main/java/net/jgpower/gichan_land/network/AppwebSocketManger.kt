package net.jgpower.gichan_land.network

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.TimeUnit
import net.jgpower.gichan_land.data.alert.AppAlertPopupState
import net.jgpower.gichan_land.data.alert.WorkerAlert
import net.jgpower.gichan_land.data.app.AppVisibilityState
import net.jgpower.gichan_land.data.emergency.EmergencyPresenceState
import net.jgpower.gichan_land.data.textalert.TextAlert
import net.jgpower.gichan_land.data.textalert.TextAlertState
import net.jgpower.gichan_land.service.AppNotificationManager
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject

object AppWebSocketManager {

    private val client = OkHttpClient.Builder()
        .pingInterval(15, TimeUnit.SECONDS)
        .build()

    private val mainHandler = Handler(Looper.getMainLooper())

    private var appContext: Context? = null
    private var webSocket: WebSocket? = null
    private var currentWorkerId: String? = null

    private var shouldReconnect: Boolean = false
    private var isConnecting: Boolean = false

    private var pingRunnable: Runnable? = null

    var onSafetyAlertReceived: ((JSONObject) -> Unit)? = null
    var onTextAlertReceived: ((JSONObject) -> Unit)? = null

    fun connect(workerId: String, context: Context) {
        Log.d("WS_MANAGER", "connect called workerId=$workerId")

        appContext = context.applicationContext
        shouldReconnect = true

        if (
            webSocket != null &&
            currentWorkerId == workerId
        ) {
            Log.d("WS_MANAGER", "already connected. skip")
            startPing(workerId)
            return
        }

        if (isConnecting && currentWorkerId == workerId) {
            Log.d("WS_MANAGER", "already connecting. skip")
            return
        }

        if (currentWorkerId != null && currentWorkerId != workerId) {
            Log.d("WS_MANAGER", "switch worker. close old socket")
            stopPing()
            webSocket?.close(1000, "switch worker")
            webSocket = null
        }

        currentWorkerId = workerId
        isConnecting = true

        val wsUrl = ServerConfig.getWebSocketUrl(context)

        val request = Request.Builder()
            .url(wsUrl)
            .build()

        Log.d("WS_MANAGER", "newWebSocket start url=$wsUrl")

        webSocket = client.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                    Log.d("WS_MANAGER", "onOpen")

                    isConnecting = false
                    this@AppWebSocketManager.webSocket = webSocket

                    val sent = webSocket.send(
                        """{"type":"connect","workerId":"$workerId"}"""
                    )

                    Log.d("WS_MANAGER", "connect message sent=$sent")

                    startPing(workerId)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    Log.d("WS_MANAGER", "onMessage text=$text")

                    try {
                        val json = JSONObject(text)
                        val type = json.optString("type")
                        val data = json.optJSONObject("data") ?: return

                        when (type) {
                            "safety_alert" -> {
                                val safetyAlert = WorkerAlert(
                                    alertId = data.optString("alertId"),
                                    eventId = data.optString("eventId"),
                                    receiverId = data.optString("receiverId"),
                                    receiveType = data.optString("receiveType"),
                                    targetType = data.optString("targetType").ifBlank { null },
                                    message = data.optString("message"),
                                    occurredAt = data.optString("occurredAt"),
                                    status = data.optString("status")
                                )

                                if (safetyAlert.alertId.isNotBlank()) {
                                    AppAlertPopupState.enqueueSafety(safetyAlert)

                                    if (!AppVisibilityState.isForeground.value) {
                                        appContext?.let { context ->
                                            AppNotificationManager.showAlertNotification(
                                                context = context,
                                                alert = safetyAlert
                                            )
                                        }
                                    }
                                }

                                onSafetyAlertReceived?.invoke(data)
                            }

                            "text_alert" -> {
                                val textAlert = TextAlert(
                                    textAlertId = data.optString("textAlertId"),
                                    receiverId = data.optString("receiverId"),
                                    receiveType = data.optString("receiveType"),
                                    message = data.optString("message"),
                                    createdAt = data.optString("createdAt")
                                )

                                if (textAlert.textAlertId.isNotBlank()) {
                                    TextAlertState.addAlert(textAlert)
                                    AppAlertPopupState.enqueueText(textAlert)

                                    if (!AppVisibilityState.isForeground.value) {
                                        appContext?.let { context ->
                                            AppNotificationManager.showTextAlertNotification(
                                                context = context,
                                                alert = textAlert
                                            )
                                        }
                                    }
                                }

                                onTextAlertReceived?.invoke(data)
                            }

                            "presence_status_changed" -> {
                                EmergencyPresenceState.update(
                                    rescueStatus = data.optStringOrNull("rescueStatus"),
                                    rescueUpdatedAt = data.optStringOrNull("rescueUpdatedAt"),
                                    policeStatus = data.optStringOrNull("policeStatus"),
                                    policeUpdatedAt = data.optStringOrNull("policeUpdatedAt")
                                )
                            }

                            else -> {
                                Log.d("WS_MANAGER", "unknown message type=$type")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("WS_MANAGER", "onMessage parse failed", e)
                    }
                }

                override fun onFailure(
                    webSocket: WebSocket,
                    t: Throwable,
                    response: okhttp3.Response?
                ) {
                    Log.e("WS_MANAGER", "onFailure message=${t.message}", t)

                    stopPing()
                    isConnecting = false

                    if (this@AppWebSocketManager.webSocket == webSocket) {
                        this@AppWebSocketManager.webSocket = null
                    }

                    reconnectLater()
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    Log.d("WS_MANAGER", "onClosed code=$code reason=$reason")

                    stopPing()
                    isConnecting = false

                    if (this@AppWebSocketManager.webSocket == webSocket) {
                        this@AppWebSocketManager.webSocket = null
                    }

                    reconnectLater()
                }
            }
        )
    }

    fun reconnectCurrent() {
        val workerId = currentWorkerId
        val context = appContext

        Log.d(
            "WS_MANAGER",
            "reconnectCurrent called workerId=$workerId context=$context"
        )

        if (workerId.isNullOrBlank() || context == null) {
            return
        }

        stopPing()
        isConnecting = false

        val oldSocket = webSocket
        webSocket = null

        oldSocket?.close(1000, "network changed")

        mainHandler.postDelayed(
            {
                if (
                    shouldReconnect &&
                    webSocket == null &&
                    !isConnecting &&
                    currentWorkerId == workerId
                ) {
                    Log.d("WS_MANAGER", "reconnectCurrent execute")
                    connect(workerId, context)
                }
            },
            1000L
        )
    }

    fun disconnect() {
        Log.d("WS_MANAGER", "disconnect called")

        stopPing()

        shouldReconnect = false
        isConnecting = false

        val workerId = currentWorkerId

        if (workerId != null) {
            val sent = webSocket?.send(
                """{"type":"disconnect","workerId":"$workerId"}"""
            )

            Log.d("WS_MANAGER", "disconnect message sent=$sent workerId=$workerId")
        }

        webSocket?.close(1000, "app closed")
        webSocket = null
        currentWorkerId = null
    }

    private fun startPing(workerId: String) {
        stopPing()

        Log.d("WS_MANAGER", "startPing workerId=$workerId")

        pingRunnable = object : Runnable {
            override fun run() {
                val socket = webSocket

                if (
                    shouldReconnect &&
                    socket != null &&
                    currentWorkerId == workerId
                ) {
                    val sent = socket.send(
                        """{"type":"ping","workerId":"$workerId"}"""
                    )

                    Log.d("WS_MANAGER", "ping sent=$sent workerId=$workerId")

                    mainHandler.postDelayed(this, 3000L)
                } else {
                    Log.d(
                        "WS_MANAGER",
                        "ping stopped shouldReconnect=$shouldReconnect socket=$socket currentWorkerId=$currentWorkerId workerId=$workerId"
                    )
                }
            }
        }

        mainHandler.postDelayed(pingRunnable!!, 3000L)
    }

    private fun stopPing() {
        pingRunnable?.let {
            mainHandler.removeCallbacks(it)
        }

        pingRunnable = null
    }

    private fun reconnectLater() {
        val workerId = currentWorkerId

        Log.d(
            "WS_MANAGER",
            "reconnectLater shouldReconnect=$shouldReconnect workerId=$workerId isConnecting=$isConnecting"
        )

        if (!shouldReconnect || workerId.isNullOrBlank()) {
            return
        }

        mainHandler.postDelayed(
            {
                Log.d(
                    "WS_MANAGER",
                    "reconnect check shouldReconnect=$shouldReconnect webSocket=$webSocket isConnecting=$isConnecting currentWorkerId=$currentWorkerId"
                )

                if (
                    shouldReconnect &&
                    webSocket == null &&
                    !isConnecting &&
                    !currentWorkerId.isNullOrBlank()
                ) {
                    val context = appContext

                    if (context != null) {
                        Log.d("WS_MANAGER", "reconnect execute")
                        connect(workerId, context)
                    } else {
                        Log.d("WS_MANAGER", "reconnect skipped. context is null")
                    }
                }
            },
            3000L
        )
    }

    private fun JSONObject.optStringOrNull(name: String): String? {
        val value = optString(name, "")
        return value.takeIf { it.isNotBlank() }
    }
}