package net.jgpower.gichan_land.data.walkie

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import net.jgpower.gichan_land.network.WalkieSignalingClient

data class IncomingWalkieCallState(
    val callId: String,
    val fromWorkerId: String,
    val fromName: String?,
    val fromAreaGroup: String?
)

data class EmergencyBroadcastState(
    val broadcastId: String,
    val fromWorkerId: String,
    val targetType: String?,
    val targetAreaGroup: String?
)

object WalkieGlobalState {
    val pendingIncomingCalls = mutableStateListOf<IncomingWalkieCallState>()
    val activeCallId = mutableStateOf<String?>(null)
    val activePeerWorkerId = mutableStateOf<String?>(null)
    val isCallActive = mutableStateOf(false)
    val lastStatusText = mutableStateOf("대기 중")
    val showPeerEndedPopup = mutableStateOf(false)
    val isEmergencyBroadcastActive = mutableStateOf(false)
    val emergencyBroadcast = mutableStateOf<EmergencyBroadcastState?>(null)

    fun upsertIncomingCall(
        callId: String,
        fromWorkerId: String,
        fromName: String?,
        fromAreaGroup: String?
    ) {
        val item = IncomingWalkieCallState(
            callId = callId,
            fromWorkerId = fromWorkerId,
            fromName = fromName ?: fromWorkerId,
            fromAreaGroup = fromAreaGroup
        )

        val index = pendingIncomingCalls.indexOfFirst { it.callId == callId }
        if (index >= 0) {
            pendingIncomingCalls[index] = item
        } else {
            pendingIncomingCalls.add(item)
        }

        if (!isCallActive.value) {
            lastStatusText.value = if (pendingIncomingCalls.size == 1) {
                "${item.fromName ?: item.fromWorkerId} 연결 요청"
            } else {
                "${pendingIncomingCalls.size}건 연결 요청"
            }
        }
    }

    fun removeIncomingCall(callId: String) {
        pendingIncomingCalls.removeAll { it.callId == callId }
        if (!isCallActive.value && pendingIncomingCalls.isEmpty()) {
            lastStatusText.value = "대기 중"
        }
    }

    fun setRinging(callId: String, toWorkerId: String) {
        activeCallId.value = callId
        activePeerWorkerId.value = toWorkerId
        isCallActive.value = false
        lastStatusText.value = "$toWorkerId 수신 요청 중..."
    }

    fun setActive(callId: String, peerWorkerId: String) {
        activeCallId.value = callId
        activePeerWorkerId.value = peerWorkerId
        isCallActive.value = true
        pendingIncomingCalls.clear()
        lastStatusText.value = "통화 연결됨"
    }

    fun startEmergencyBroadcast(
        broadcastId: String,
        fromWorkerId: String,
        targetType: String?,
        targetAreaGroup: String?
    ) {
        activeCallId.value = null
        activePeerWorkerId.value = null
        isCallActive.value = false
        pendingIncomingCalls.clear()
        isEmergencyBroadcastActive.value = true
        emergencyBroadcast.value = EmergencyBroadcastState(
            broadcastId = broadcastId,
            fromWorkerId = fromWorkerId,
            targetType = targetType,
            targetAreaGroup = targetAreaGroup
        )
        lastStatusText.value = "긴급 전파 수신 중"
    }

    fun endEmergencyBroadcast(message: String = "긴급 전파 종료") {
        isEmergencyBroadcastActive.value = false
        emergencyBroadcast.value = null
        if (activeCallId.value == null && pendingIncomingCalls.isEmpty()) {
            lastStatusText.value = message
        }
    }

    fun clearCall(message: String = "대기 중") {
        activeCallId.value = null
        activePeerWorkerId.value = null
        isCallActive.value = false
        pendingIncomingCalls.clear()
        lastStatusText.value = message
    }
}

object WalkieMissedCallState {
    val items = mutableStateListOf<WalkieSignalingClient.MissedCallDto>()

    fun replaceAll(newItems: List<WalkieSignalingClient.MissedCallDto>) {
        items.clear()
        items.addAll(newItems)
    }

    fun upsert(item: WalkieSignalingClient.MissedCallDto) {
        val key = item.missedId.ifBlank { item.callId }
        val index = items.indexOfFirst { (it.missedId.ifBlank { it.callId }) == key }
        if (index >= 0) {
            items[index] = item
        } else {
            items.add(0, item)
        }
    }

    fun remove(missedId: String?, callId: String?) {
        items.removeAll {
            (!missedId.isNullOrBlank() && it.missedId == missedId) ||
                    (!callId.isNullOrBlank() && it.callId == callId)
        }
    }
}
