package net.jgpower.gichan_land.network

import android.os.Handler
import android.os.Looper
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object WalkieSignalingClient {

    private const val TAG = "WALKIE_SIGNAL"

    private val mainHandler = Handler(Looper.getMainLooper())

    private val client =
        OkHttpClient.Builder()
            .pingInterval(15, TimeUnit.SECONDS)
            .build()

    private var webSocket: WebSocket? = null
    private var currentWorkerId: String? = null

    var listener: Listener? = null

    interface Listener {
        fun onConnected()
        fun onDisconnected()

        fun onCallRinging(callId: String, toWorkerId: String)

        fun onIncomingCall(
            callId: String,
            fromWorkerId: String,
            fromName: String?,
            fromAreaGroup: String?
        )

        fun onCallActive(
            callId: String,
            peerWorkerId: String,
            talkerId: String?
        )

        fun onCallRejected(callId: String, byWorkerId: String?)
        fun onCallEnded(callId: String, reason: String?)
        fun onMicState(callId: String, workerId: String, micOn: Boolean)
        fun onError(message: String)
    }

    fun connect(
        serverBaseUrl: String,
        workerId: String
    ) {
        currentWorkerId = workerId

        val wsUrl =
            serverBaseUrl
                .replace("https://", "wss://")
                .replace("http://", "ws://")
                .trimEnd('/') + "/ws/walkie-app"

        disconnect(sendDisconnect = false)

        val request = Request.Builder()
            .url(wsUrl)
            .build()

        webSocket = client.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    Log.d(TAG, "connected $wsUrl")

                    this@WalkieSignalingClient.webSocket = webSocket

                    val json = JSONObject()
                        .put("type", "connect")
                        .put("workerId", workerId)
                        .toString()

                    Log.d(TAG, "send $json")
                    webSocket.send(json)

                    post {
                        listener?.onConnected()
                    }
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    handleMessage(text)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    Log.d(TAG, "closed code=$code reason=$reason")
                    post { listener?.onDisconnected() }
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    Log.d(TAG, "closing code=$code reason=$reason")
                    post { listener?.onDisconnected() }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.e(TAG, "failed", t)
                    post {
                        listener?.onError(t.message ?: "WebSocket error")
                        listener?.onDisconnected()
                    }
                }
            }
        )
    }

    fun disconnect(sendDisconnect: Boolean = true) {
        val workerId = currentWorkerId

        if (sendDisconnect && !workerId.isNullOrBlank()) {
            sendJson(
                mapOf(
                    "type" to "disconnect",
                    "workerId" to workerId
                )
            )
        }

        try {
            webSocket?.close(1000, "bye")
        } catch (_: Exception) {
        }

        webSocket = null
    }

    fun ping() {
        val workerId = currentWorkerId ?: return
        val socket = webSocket ?: return

        val json = JSONObject()
            .put("type", "ping")
            .put("workerId", workerId)
            .toString()

        Log.d(TAG, "send $json")
        socket.send(json)
    }

    fun requestCall(
        fromWorkerId: String,
        fromName: String?,
        fromAreaGroup: String?,
        toWorkerId: String
    ) {
        sendJson(
            mapOf(
                "type" to "call_request",
                "fromWorkerId" to fromWorkerId,
                "fromName" to (fromName ?: fromWorkerId),
                "fromAreaGroup" to (fromAreaGroup ?: ""),
                "toWorkerId" to toWorkerId
            )
        )
    }

    fun acceptCall(callId: String, workerId: String) {
        sendJson(
            mapOf(
                "type" to "call_accept",
                "callId" to callId,
                "workerId" to workerId
            )
        )
    }

    fun rejectCall(callId: String, workerId: String) {
        sendJson(
            mapOf(
                "type" to "call_reject",
                "callId" to callId,
                "workerId" to workerId
            )
        )
    }

    fun endCall(callId: String, workerId: String) {
        sendJson(
            mapOf(
                "type" to "call_end",
                "callId" to callId,
                "workerId" to workerId
            )
        )
    }

    // 동시 송신 허용 구조에서는 송신권 요청이 아니라 MIC ON 상태 알림입니다.
    fun micOn(callId: String, workerId: String) {
        sendJson(
            mapOf(
                "type" to "talk_start",
                "callId" to callId,
                "workerId" to workerId
            )
        )
    }

    // 동시 송신 허용 구조에서는 송신권 반납이 아니라 MIC OFF 상태 알림입니다.
    fun micOff(callId: String, workerId: String) {
        sendJson(
            mapOf(
                "type" to "talk_stop",
                "callId" to callId,
                "workerId" to workerId
            )
        )
    }

    private fun sendJson(map: Map<String, Any>) {
        val json = JSONObject()

        map.forEach { (key, value) ->
            json.put(key, value)
        }

        val text = json.toString()
        Log.d(TAG, "send $text")

        val sent = webSocket?.send(text) ?: false
        if (!sent) {
            Log.d(TAG, "send failed. socket not connected")
        }
    }

    private fun handleMessage(text: String) {
        try {
            Log.d(TAG, "recv $text")

            val json = JSONObject(text)
            val type = json.optString("type")

            post {
                when (type) {
                    "connect_ok" -> listener?.onConnected()
                    "pong" -> Unit

                    "call_ringing" -> {
                        listener?.onCallRinging(
                            callId = json.optString("callId"),
                            toWorkerId = json.optString("toWorkerId")
                        )
                    }

                    "incoming_call" -> {
                        listener?.onIncomingCall(
                            callId = json.optString("callId"),
                            fromWorkerId = json.optString("fromWorkerId"),
                            fromName = json.optString("fromName").takeIf { it.isNotBlank() },
                            fromAreaGroup = json.optString("fromAreaGroup").takeIf { it.isNotBlank() }
                        )
                    }

                    "call_active" -> {
                        listener?.onCallActive(
                            callId = json.optString("callId"),
                            peerWorkerId = json.optString("peerWorkerId"),
                            talkerId = json.optString("talkerId")
                                .takeIf { it.isNotBlank() && it != "null" }
                        )
                    }

                    "call_rejected" -> {
                        listener?.onCallRejected(
                            callId = json.optString("callId"),
                            byWorkerId = json.optString("byWorkerId")
                                .takeIf { it.isNotBlank() && it != "null" }
                        )
                    }

                    "call_ended" -> {
                        listener?.onCallEnded(
                            callId = json.optString("callId"),
                            reason = json.optString("reason").takeIf { it.isNotBlank() }
                        )
                    }

                    "mic_state" -> {
                        listener?.onMicState(
                            callId = json.optString("callId"),
                            workerId = json.optString("workerId"),
                            micOn = json.optBoolean("micOn", false)
                        )
                    }

                    "talk_granted" -> {
                        // 이전 클라이언트 호환용 응답입니다. 현재 앱은 이 응답을 기다리지 않습니다.
                    }

                    "talk_denied" -> {
                        listener?.onError(
                            json.optString("reason").ifBlank { "talk_denied" }
                        )
                    }

                    "call_request_failed" -> {
                        listener?.onError(
                            json.optString("reason").ifBlank { "call_request_failed" }
                        )
                    }

                    else -> Log.d(TAG, "unknown type=$type")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "handle message failed", e)
            post { listener?.onError(e.message ?: "message parse error") }
        }
    }

    private fun post(block: () -> Unit) {
        mainHandler.post(block)
    }
}
