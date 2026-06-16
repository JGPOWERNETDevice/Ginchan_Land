package net.jgpower.gichan_land.network

import android.os.Handler
import android.os.Looper
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
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

    data class MissedCallDto(
        val missedId: String,
        val callId: String,
        val fromWorkerId: String,
        val fromName: String?,
        val fromAreaGroup: String?,
        val toWorkerId: String,
        val reason: String,
        val createdAt: Long,
        val endedAt: Long,
        val read: Boolean
    )

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
        fun onMissedCallsList(items: List<MissedCallDto>) {}
        fun onMissedCallAdded(item: MissedCallDto) {}
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

                    sendJson(
                        mapOf(
                            "type" to "connect",
                            "workerId" to workerId
                        )
                    )

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

    fun missedCallsGet(workerId: String) {
        sendJson(
            mapOf(
                "type" to "missed_calls_get",
                "workerId" to workerId
            )
        )
    }

    fun missedCallsMarkRead(workerId: String, missedId: String? = null, callId: String? = null) {
        val json = JSONObject()
            .put("type", "missed_calls_mark_read")
            .put("workerId", workerId)

        if (!missedId.isNullOrBlank()) json.put("missedId", missedId)
        if (!callId.isNullOrBlank()) json.put("callId", callId)

        sendText(json.toString())
    }

    fun missedCallsRemove(workerId: String, missedId: String? = null, callId: String? = null) {
        val json = JSONObject()
            .put("type", "missed_calls_remove")
            .put("workerId", workerId)

        if (!missedId.isNullOrBlank()) json.put("missedId", missedId)
        if (!callId.isNullOrBlank()) json.put("callId", callId)

        sendText(json.toString())
    }

    fun missedCallsClear(workerId: String) {
        sendJson(
            mapOf(
                "type" to "missed_calls_clear",
                "workerId" to workerId
            )
        )
    }

    // 구버전 호환용. 현재 Node-RED는 talk_start/talk_stop을 사용하지 않습니다.
    fun micOn(callId: String, workerId: String) {
        sendJson(
            mapOf(
                "type" to "talk_start",
                "callId" to callId,
                "workerId" to workerId
            )
        )
    }

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

        sendText(json.toString())
    }

    private fun sendText(text: String) {
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

                    "missed_calls_list" -> {
                        listener?.onMissedCallsList(parseMissedCallArray(json.optJSONArray("items")))
                    }

                    "missed_call_added" -> {
                        json.optJSONObject("item")?.let { item ->
                            listener?.onMissedCallAdded(parseMissedCall(item))
                        }
                    }

                    "talk_granted" -> Unit

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

    private fun parseMissedCallArray(array: JSONArray?): List<MissedCallDto> {
        if (array == null) return emptyList()

        val result = mutableListOf<MissedCallDto>()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            result.add(parseMissedCall(item))
        }
        return result
    }

    private fun parseMissedCall(json: JSONObject): MissedCallDto {
        return MissedCallDto(
            missedId = json.optString("missedId"),
            callId = json.optString("callId"),
            fromWorkerId = json.optString("fromWorkerId"),
            fromName = json.optString("fromName").takeIf { it.isNotBlank() && it != "null" },
            fromAreaGroup = json.optString("fromAreaGroup").takeIf { it.isNotBlank() && it != "null" },
            toWorkerId = json.optString("toWorkerId"),
            reason = json.optString("reason").ifBlank { "missed" },
            createdAt = json.optLong("createdAt", 0L),
            endedAt = json.optLong("endedAt", 0L),
            read = json.optBoolean("read", false)
        )
    }

    private fun post(block: () -> Unit) {
        mainHandler.post(block)
    }
}
