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
    private var currentAreaGroup: String? = null

    @Volatile
    private var isConnectedFlag: Boolean = false

    @Volatile
    private var isConnectingFlag: Boolean = false

    var listener: Listener? = null
    private var backgroundListener: Listener? = null

    fun setBackgroundListener(listener: Listener?) {
        backgroundListener = listener
    }

    private fun notifyListeners(block: (Listener) -> Unit) {
        listener?.let(block)
        val bg = backgroundListener
        if (bg != null && bg !== listener) {
            block(bg)
        }
    }

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
        fun onCallFailed(callId: String, reason: String?, peerWorkerId: String?) {}
        fun onCallEnded(callId: String, reason: String?, byWorkerId: String?, peerWorkerId: String?)
        fun onMicState(callId: String, workerId: String, micOn: Boolean)
        fun onMissedCallsList(items: List<MissedCallDto>) {}
        fun onMissedCallAdded(item: MissedCallDto) {}
        fun onEmergencyBroadcastStarted(
            broadcastId: String,
            fromWorkerId: String,
            targetType: String?,
            targetAreaGroup: String?
        ) {}
        fun onEmergencyBroadcastEnded(broadcastId: String, reason: String?) {}
        fun onError(message: String)
    }

    fun isConnected(): Boolean {
        return isConnectedFlag && webSocket != null
    }

    fun isConnecting(): Boolean {
        return isConnectingFlag
    }

    fun connect(
        serverBaseUrl: String,
        workerId: String,
        areaGroup: String? = null
    ) {
        currentWorkerId = workerId
        if (!areaGroup.isNullOrBlank()) {
            currentAreaGroup = areaGroup
        }

        if (isConnectedFlag && webSocket != null && currentWorkerId == workerId) {
            Log.d(TAG, "connect skipped. already connected workerId=$workerId")
            return
        }

        if (isConnectingFlag && currentWorkerId == workerId) {
            Log.d(TAG, "connect skipped. already connecting workerId=$workerId")
            return
        }

        val wsUrl =
            serverBaseUrl
                .replace("https://", "wss://")
                .replace("http://", "ws://")
                .trimEnd('/') + "/ws/walkie-app"

        disconnect(sendDisconnect = false)
        isConnectingFlag = true

        val request = Request.Builder()
            .url(wsUrl)
            .build()

        webSocket = client.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    Log.d(TAG, "connected $wsUrl")
                    this@WalkieSignalingClient.webSocket = webSocket
                    isConnectedFlag = true
                    isConnectingFlag = false

                    val connectJson = JSONObject()
                        .put("type", "connect")
                        .put("workerId", workerId)

                    currentAreaGroup?.takeIf { it.isNotBlank() }?.let { group ->
                        connectJson.put("areaGroup", group)
                    }

                    sendText(connectJson.toString())

                    post {
                        notifyListeners { it.onConnected() }
                    }
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    handleMessage(text)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    Log.d(TAG, "closed code=$code reason=$reason")
                    if (this@WalkieSignalingClient.webSocket == webSocket) {
                        this@WalkieSignalingClient.webSocket = null
                    }
                    isConnectedFlag = false
                    isConnectingFlag = false
                    post { notifyListeners { it.onDisconnected() } }
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    Log.d(TAG, "closing code=$code reason=$reason")
                    isConnectedFlag = false
                    post { notifyListeners { it.onDisconnected() } }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.e(TAG, "failed", t)
                    if (this@WalkieSignalingClient.webSocket == webSocket) {
                        this@WalkieSignalingClient.webSocket = null
                    }
                    isConnectedFlag = false
                    isConnectingFlag = false
                    post {
                        notifyListeners { it.onError(t.message ?: "WebSocket error") }
                        notifyListeners { it.onDisconnected() }
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
        isConnectedFlag = false
        isConnectingFlag = false
    }

    fun updateWorkerInfo(areaGroup: String?) {
        if (!areaGroup.isNullOrBlank()) {
            currentAreaGroup = areaGroup
        }

        val workerId = currentWorkerId ?: return
        val json = JSONObject()
            .put("type", "worker_info_update")
            .put("workerId", workerId)

        currentAreaGroup?.takeIf { it.isNotBlank() }?.let { group ->
            json.put("areaGroup", group)
        }

        sendText(json.toString())
    }

    fun ping() {
        val workerId = currentWorkerId ?: return
        val socket = webSocket ?: return

        val jsonObject = JSONObject()
            .put("type", "ping")
            .put("workerId", workerId)

        currentAreaGroup?.takeIf { it.isNotBlank() }?.let { group ->
            jsonObject.put("areaGroup", group)
        }

        val json = jsonObject.toString()

        Log.d(TAG, "send $json")
        val sent = socket.send(json)
        if (!sent) {
            Log.d(TAG, "ping send failed")
            if (webSocket === socket) {
                webSocket = null
            }
            isConnectedFlag = false
            isConnectingFlag = false
            post { notifyListeners { it.onDisconnected() } }
        }
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

    fun emergencyBroadcastStart(
        fromWorkerId: String,
        targetType: String,
        targetWorkerIds: List<String>,
        targetAreaGroup: String? = null
    ) {
        val json = JSONObject()
            .put("type", "emergency_broadcast_start")
            .put("fromWorkerId", fromWorkerId)
            .put("targetType", targetType)
            .put("targetWorkerIds", JSONArray(targetWorkerIds))

        if (!targetAreaGroup.isNullOrBlank()) {
            json.put("targetAreaGroup", targetAreaGroup)
        }

        sendText(json.toString())
    }

    fun emergencyBroadcastEnd(broadcastId: String, fromWorkerId: String) {
        sendJson(
            mapOf(
                "type" to "emergency_broadcast_end",
                "broadcastId" to broadcastId,
                "fromWorkerId" to fromWorkerId
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
            isConnectedFlag = false
            isConnectingFlag = false
            webSocket = null
        }
    }

    private fun handleMessage(text: String) {
        try {
            Log.d(TAG, "recv $text")

            val json = JSONObject(text)
            val type = json.optString("type")

            post {
                when (type) {
                    "connect_ok" -> notifyListeners { it.onConnected() }
                    "pong" -> Unit

                    "call_ringing" -> {
                        notifyListeners {
                            it.onCallRinging(
                                callId = json.optString("callId"),
                                toWorkerId = json.optString("toWorkerId")
                            )
                        }
                    }

                    "incoming_call" -> {
                        notifyListeners {
                            it.onIncomingCall(
                                callId = json.optString("callId"),
                                fromWorkerId = json.optString("fromWorkerId"),
                                fromName = json.optString("fromName").takeIf { value -> value.isNotBlank() },
                                fromAreaGroup = json.optString("fromAreaGroup").takeIf { value -> value.isNotBlank() }
                            )
                        }
                    }

                    "call_active" -> {
                        notifyListeners {
                            it.onCallActive(
                                callId = json.optString("callId"),
                                peerWorkerId = json.optString("peerWorkerId"),
                                talkerId = json.optString("talkerId")
                                    .takeIf { value -> value.isNotBlank() && value != "null" }
                            )
                        }
                    }

                    "call_rejected" -> {
                        notifyListeners {
                            it.onCallRejected(
                                callId = json.optString("callId"),
                                byWorkerId = json.optString("byWorkerId")
                                    .takeIf { value -> value.isNotBlank() && value != "null" }
                            )
                        }
                    }

                    "call_failed" -> {
                        notifyListeners {
                            it.onCallFailed(
                                callId = json.optString("callId"),
                                reason = json.optString("reason").takeIf { value -> value.isNotBlank() },
                                peerWorkerId = json.optString("peerWorkerId")
                                    .takeIf { value -> value.isNotBlank() && value != "null" }
                            )
                        }
                    }

                    "call_ended" -> {
                        notifyListeners {
                            it.onCallEnded(
                                callId = json.optString("callId"),
                                reason = json.optString("reason").takeIf { value -> value.isNotBlank() },
                                byWorkerId = json.optString("byWorkerId")
                                    .takeIf { value -> value.isNotBlank() && value != "null" },
                                peerWorkerId = json.optString("peerWorkerId")
                                    .takeIf { value -> value.isNotBlank() && value != "null" }
                            )
                        }
                    }

                    "mic_state" -> {
                        notifyListeners {
                            it.onMicState(
                                callId = json.optString("callId"),
                                workerId = json.optString("workerId"),
                                micOn = json.optBoolean("micOn", false)
                            )
                        }
                    }

                    "emergency_broadcast_started" -> {
                        notifyListeners {
                            it.onEmergencyBroadcastStarted(
                                broadcastId = json.optString("broadcastId"),
                                fromWorkerId = json.optString("fromWorkerId").ifBlank { "monitor" },
                                targetType = json.optString("targetType").takeIf { value -> value.isNotBlank() },
                                targetAreaGroup = json.optString("targetAreaGroup")
                                    .takeIf { value -> value.isNotBlank() && value != "null" }
                            )
                        }
                    }

                    "emergency_broadcast_ended" -> {
                        notifyListeners {
                            it.onEmergencyBroadcastEnded(
                                broadcastId = json.optString("broadcastId"),
                                reason = json.optString("reason").takeIf { value -> value.isNotBlank() }
                            )
                        }
                    }

                    "missed_calls_list" -> {
                        notifyListeners {
                            it.onMissedCallsList(parseMissedCallArray(json.optJSONArray("items")))
                        }
                    }

                    "missed_call_added" -> {
                        json.optJSONObject("item")?.let { item ->
                            notifyListeners {
                                it.onMissedCallAdded(parseMissedCall(item))
                            }
                        }
                    }

                    "talk_granted" -> Unit

                    "talk_denied" -> {
                        notifyListeners {
                            it.onError(json.optString("reason").ifBlank { "talk_denied" })
                        }
                    }

                    "call_request_failed" -> {
                        notifyListeners {
                            it.onError(json.optString("reason").ifBlank { "call_request_failed" })
                        }
                    }

                    else -> Log.d(TAG, "unknown type=$type")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "handle message failed", e)
            post { notifyListeners { it.onError(e.message ?: "message parse error") } }
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
