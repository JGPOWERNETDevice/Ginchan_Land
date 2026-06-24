package net.jgpower.gichan_land.ui.walkietalkie

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import net.jgpower.gichan_land.data.walkie.IncomingWalkieCallState
import net.jgpower.gichan_land.data.walkie.OnlineWorkerDto
import net.jgpower.gichan_land.data.walkie.OnlineWorkerGroupDto
import net.jgpower.gichan_land.data.walkie.WalkieGlobalState
import net.jgpower.gichan_land.data.walkie.WalkieMissedCallState
import net.jgpower.gichan_land.data.walkie.WalkieTarget
import net.jgpower.gichan_land.data.walkie.WalkieTargetType
import net.jgpower.gichan_land.network.ApiServiceManager
import net.jgpower.gichan_land.network.ServerConfig
import net.jgpower.gichan_land.network.WalkieSignalingClient
import net.jgpower.gichan_land.network.WalkieTalkieManager
import net.jgpower.gichan_land.service.WebSocketForegroundService
import net.jgpower.gichan_land.util.ErrorMessageSanitizer

private const val MONITOR_WORKER_ID = "monitor"
private const val MONITOR_NAME = "중앙관제"
private const val MONITOR_GROUP = "중앙관제"

private data class MissedCallUiState(
    val missedId: String,
    val callId: String,
    val fromWorkerId: String,
    val fromName: String?,
    val fromAreaGroup: String?,
    val reason: String,
    val createdAt: Long,
    val endedAt: Long,
    val read: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalkieTalkieScreen(
    workerId: String,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val currentWorkerId = workerId.trim()

    val onlineWorkers = remember { mutableStateListOf<OnlineWorkerDto>() }
    val selectedWorkerIds = remember { mutableStateMapOf<String, OnlineWorkerDto>() }

    var isLoading by remember { mutableStateOf(false) }
    var isWalkieNetworkAvailable by remember { mutableStateOf(ServerConfig.isWalkieNetworkAvailable(context)) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var lastErrorMessageAt by remember { mutableStateOf(0L) }

    var myAreaGroup by remember { mutableStateOf<String?>(null) }
    var myGroups by remember { mutableStateOf<List<OnlineWorkerGroupDto>>(emptyList()) }
    var selectedGroupCode by remember { mutableStateOf<String?>(null) }
    var isGroupSelected by remember { mutableStateOf(false) }
    var selectedTarget by remember { mutableStateOf<WalkieTarget?>(null) }

    var isMicOn by WalkieGlobalState.isMicOn
    var activeCallId by WalkieGlobalState.activeCallId
    var isCallActive by WalkieGlobalState.isCallActive
    var activePeerWorkerId by WalkieGlobalState.activePeerWorkerId

    val pendingIncomingCalls = WalkieGlobalState.pendingIncomingCalls
    val missedCallItems = WalkieMissedCallState.items
    var isEmergencyBroadcastActive by WalkieGlobalState.isEmergencyBroadcastActive

    var callStatusText by WalkieGlobalState.lastStatusText
    var peerEndedDialogText by remember { mutableStateOf<String?>(null) }

    fun workerDisplayName(worker: OnlineWorkerDto): String {
        val id = worker.workerId?.trim()
        if (id == MONITOR_WORKER_ID) return MONITOR_NAME

        val name = worker.name?.trim()
        return when {
            !name.isNullOrBlank() -> name
            !id.isNullOrBlank() -> id
            else -> "이름 없음"
        }
    }

    fun workerDisplayId(worker: OnlineWorkerDto): String {
        return worker.workerId?.trim()?.takeIf { it.isNotBlank() } ?: "-"
    }

    fun groupDisplayName(group: OnlineWorkerGroupDto): String {
        return group.groupName?.trim()?.takeIf { it.isNotBlank() }
            ?: group.groupCode?.trim()?.takeIf { it.isNotBlank() }
            ?: "-"
    }

    fun groupKey(group: OnlineWorkerGroupDto): String {
        return group.groupCode?.trim()?.takeIf { it.isNotBlank() }
            ?: group.groupName?.trim()?.takeIf { it.isNotBlank() }
            ?: ""
    }


    fun missedReasonText(reason: String): String {
        return when (reason) {
            "ring_timeout" -> "응답 없음"
            "callee_selected_other" -> "다른 요청 연결"
            "callee_busy" -> "통화 중 차단"
            "caller_cancelled" -> "요청자 취소"
            else -> reason
        }
    }

    fun callFailedReasonText(reason: String?): String {
        return when (reason) {
            "ring_timeout" -> "상대방이 응답하지 않았습니다."
            "caller_cancelled" -> "연결 요청이 취소되었습니다."
            "callee_selected_other" -> "상대방이 다른 요청을 수락했습니다."
            "callee_busy" -> "상대방이 통화 중입니다."
            else -> "연결 요청이 종료되었습니다."
        }
    }

    fun callEndedStatusText(reason: String?): String {
        return when (reason) {
            "self_ended" -> "통화 종료됨"
            "peer_ended" -> "상대방이 통화를 종료했습니다."
            "peer_disconnected" -> "상대방 연결이 끊어졌습니다."
            "emergency" -> "긴급 전파 수신으로 통화 종료"
            else -> "통화 종료됨"
        }
    }

    fun requestFailedReasonText(reason: String?): String {
        return when (reason) {
            "caller_busy" -> "이미 통화 중입니다."
            "callee_busy" -> "상대방이 통화 중입니다."
            "caller_waiting" -> "이미 연결 요청 중입니다."
            "caller_offline" -> "신호 연결을 확인하는 중입니다. 잠시 후 다시 시도하세요."
            "callee_offline" -> "상대방이 현재 연결되어 있지 않습니다."
            "missing_worker_id" -> "연결 대상 정보가 올바르지 않습니다."
            "same_worker" -> "자기 자신에게는 연결할 수 없습니다."
            "emergency_active" -> "긴급 전파 중에는 연결할 수 없습니다."
            else -> ErrorMessageSanitizer.signalErrorMessage(reason)
        }
    }

    fun showUserError(message: String, force: Boolean = false) {
        val text = message.trim()
        if (text.isBlank()) return

        val nowMs = System.currentTimeMillis()
        if (force || errorMessage != text || nowMs - lastErrorMessageAt > 2500L) {
            errorMessage = text
            lastErrorMessageAt = nowMs
        }
    }

    fun isPersistentErrorText(text: String?): Boolean {
        val value = text ?: return false
        return value.contains("신호 서버") ||
                value.contains("신호 연결") ||
                value.contains("네트워크") ||
                value.contains("내부 Wi-Fi")
    }

    fun showStableSignalError() {
        showUserError(ErrorMessageSanitizer.stableSignalNetworkError())
    }

    fun showTransientUserError(message: String) {
        showUserError(message, force = true)
    }

    fun showWalkieNetworkBlocked() {
        showUserError(ServerConfig.walkieNetworkErrorMessage())
        callStatusText = ServerConfig.walkieNetworkErrorMessage()
    }

    fun formatMissedTime(epochMillis: Long): String {
        if (epochMillis <= 0L) return "-"
        return try {
            SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).format(Date(epochMillis))
        } catch (_: Exception) {
            "-"
        }
    }

    fun toMissedUiState(item: WalkieSignalingClient.MissedCallDto): MissedCallUiState {
        return MissedCallUiState(
            missedId = item.missedId,
            callId = item.callId,
            fromWorkerId = item.fromWorkerId,
            fromName = item.fromName ?: item.fromWorkerId,
            fromAreaGroup = item.fromAreaGroup,
            reason = item.reason,
            createdAt = item.createdAt,
            endedAt = item.endedAt,
            read = item.read
        )
    }

    fun isMonitorSelected(): Boolean {
        return selectedTarget?.targetType == WalkieTargetType.USER &&
                selectedTarget?.targetWorkerId
                    ?.split(",")
                    ?.map { it.trim() }
                    ?.contains(MONITOR_WORKER_ID) == true
    }

    fun setTargetForPeer(peerWorkerId: String) {
        val target = WalkieTarget(
            targetType = WalkieTargetType.USER,
            targetWorkerId = peerWorkerId,
            targetWorkerName = if (peerWorkerId == MONITOR_WORKER_ID) MONITOR_NAME else peerWorkerId,
            targetAreaGroup = if (peerWorkerId == MONITOR_WORKER_ID) MONITOR_GROUP else myAreaGroup
        )

        selectedTarget = target
        WalkieTalkieManager.setTarget(target)
    }

    fun selectGroupAll() {
        isGroupSelected = true
        selectedWorkerIds.clear()

        selectedTarget = WalkieTarget(
            targetType = WalkieTargetType.GROUP,
            targetAreaGroup = myAreaGroup
        )

        WalkieTalkieManager.setTarget(selectedTarget)
    }

    fun selectMonitor() {
        isGroupSelected = false
        selectedWorkerIds.clear()

        selectedTarget = WalkieTarget(
            targetType = WalkieTargetType.USER,
            targetWorkerId = MONITOR_WORKER_ID,
            targetWorkerName = MONITOR_NAME,
            targetAreaGroup = MONITOR_GROUP
        )

        WalkieTalkieManager.setTarget(selectedTarget)
    }

    fun updateWalkieTarget() {
        selectedTarget = if (isGroupSelected) {
            WalkieTarget(
                targetType = WalkieTargetType.GROUP,
                targetAreaGroup = myAreaGroup
            )
        } else {
            val ids = selectedWorkerIds.keys
                .filter { it.isNotBlank() }
                .joinToString(",")

            if (ids.isBlank()) {
                null
            } else {
                WalkieTarget(
                    targetType = WalkieTargetType.USER,
                    targetWorkerId = ids,
                    targetWorkerName = selectedWorkerIds.values.joinToString(",") { workerDisplayName(it) },
                    targetAreaGroup = myAreaGroup
                )
            }
        }

        WalkieTalkieManager.setTarget(selectedTarget)
    }

    fun toggleWorker(worker: OnlineWorkerDto) {
        val targetWorkerId = worker.workerId?.trim() ?: return

        if (targetWorkerId.isBlank()) return
        if (targetWorkerId == currentWorkerId) return
        if (targetWorkerId == MONITOR_WORKER_ID) return
        if (activeCallId != null) return

        isGroupSelected = false

        // 1:1 연결용: 개별 대상은 항상 한 명만 선택
        if (selectedWorkerIds.containsKey(targetWorkerId)) {
            selectedWorkerIds.clear()
        } else {
            selectedWorkerIds.clear()
            selectedWorkerIds[targetWorkerId] = worker
        }

        updateWalkieTarget()
    }

    fun selectedTargetText(): String {
        if (isMonitorSelected()) return "현재 선택 대상: $MONITOR_NAME"

        val names = selectedWorkerIds.values.joinToString(", ") { workerDisplayName(it) }
        return "현재 선택 대상: ${names.ifBlank { "선택 없음" }}"
    }

    fun clearCallState(message: String = "대기 중") {
        activeCallId = null
        isCallActive = false
        activePeerWorkerId = null
        pendingIncomingCalls.clear()
        isMicOn = false
        callStatusText = message
        WalkieTalkieManager.stopTransmit()
        WebSocketForegroundService.refreshWalkieNotification(context)
    }

    fun endCurrentCall() {
        val callId = activeCallId

        if (!callId.isNullOrBlank()) {
            WalkieSignalingClient.endCall(
                callId = callId,
                workerId = currentWorkerId
            )
        }

        clearCallState("통화 종료됨")
    }

    fun exitScreen() {
        endCurrentCall()
        WalkieTalkieManager.stopTransmit()
        onBackClick()
    }

    fun leaveScreenWithoutEndingCall() {
        // 화면만 벗어나고 통화 상태는 유지합니다.
        // 기존처럼 call_end를 보내지 않으므로 상대방과의 1:1 연결이 끊기지 않습니다.
        onBackClick()
    }

    fun startMicNow() {
        isWalkieNetworkAvailable = ServerConfig.isWalkieNetworkAvailable(context)
        if (!isWalkieNetworkAvailable) {
            showWalkieNetworkBlocked()
            WalkieTalkieManager.stopTransmit()
            isMicOn = false
            return
        }

        val callId = activeCallId

        if (isEmergencyBroadcastActive) {
            errorMessage = "긴급 전파 수신 중에는 MIC를 사용할 수 없습니다."
            return
        }

        if (!isCallActive || callId.isNullOrBlank()) {
            errorMessage = "먼저 통화를 연결하세요."
            return
        }

        val started = WalkieTalkieManager.startTransmit(context)
        isMicOn = started

        callStatusText =
            if (started) {
                "내 MIC ON"
            } else {
                "송신 시작 실패"
            }
        WebSocketForegroundService.refreshWalkieNotification(context)
    }

    fun stopMicNow() {
        WalkieTalkieManager.stopTransmit()
        isMicOn = false
        callStatusText = "통화 연결됨"
        WebSocketForegroundService.refreshWalkieNotification(context)
    }

    BackHandler { leaveScreenWithoutEndingCall() }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startMicNow()
        } else {
            errorMessage = "마이크 권한이 필요합니다."
        }
    }

    suspend fun loadOnlineWorkers() {
        try {
            isLoading = true
            isWalkieNetworkAvailable = ServerConfig.isWalkieNetworkAvailable(context)

            if (!isWalkieNetworkAvailable) {
                // 내부 Wi-Fi가 끊긴 상태에서는 무전기 기능만 차단합니다.
                // 단, 직전에 받아둔 그룹/중앙관제/직원 목록까지 지우면
                // 사용자에게 "배정된 그룹 없음", "중앙관제 미연결"처럼 잘못 보일 수 있으므로 캐시는 유지합니다.
                selectedWorkerIds.clear()
                selectedTarget = null
                WalkieTalkieManager.stopTransmit()
                WalkieTalkieManager.stop()
                WalkieSignalingClient.disconnect(sendDisconnect = false)
                if (activeCallId != null || isCallActive || pendingIncomingCalls.isNotEmpty()) {
                    clearCallState(ServerConfig.walkieNetworkErrorMessage())
                } else {
                    callStatusText = ServerConfig.walkieNetworkErrorMessage()
                }
                showUserError(ServerConfig.walkieNetworkErrorMessage())
                return
            }

            val list = ApiServiceManager.apiService.getOnlineWorkers()

            onlineWorkers.clear()
            onlineWorkers.addAll(list)
            if (isWalkieNetworkAvailable && (errorMessage?.contains("네트워크") == true || errorMessage?.contains("신호 서버") == true || errorMessage?.contains("온라인 직원") == true || errorMessage?.contains("내부 Wi-Fi") == true)) {
                errorMessage = null
            }

            val me = list.firstOrNull { it.workerId?.trim() == currentWorkerId }
            val groups = me?.groupItems().orEmpty()
            myGroups = groups
            myAreaGroup = me?.primaryGroupName()
            val myGroupText = me?.groupNamesText()?.takeIf { it.isNotBlank() } ?: myAreaGroup
            WalkieSignalingClient.updateWorkerInfo(myGroupText)

            val selectedStillExists = groups.any { groupKey(it) == selectedGroupCode }
            if (!selectedStillExists) {
                selectedGroupCode = groups.firstOrNull()?.let { groupKey(it) }
            }
        } catch (e: Exception) {
            showStableSignalError()
        } finally {
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            loadOnlineWorkers()
            delay(5000L)
        }
    }

    LaunchedEffect(errorMessage, lastErrorMessageAt) {
        val shownMessage = errorMessage
        val shownAt = lastErrorMessageAt

        if (!shownMessage.isNullOrBlank() && !isPersistentErrorText(shownMessage)) {
            delay(3000L)

            if (errorMessage == shownMessage && lastErrorMessageAt == shownAt) {
                errorMessage = null
            }
        }
    }

    LaunchedEffect(currentWorkerId, myAreaGroup) {
        val group = myAreaGroup

        isWalkieNetworkAvailable = ServerConfig.isWalkieNetworkAvailable(context)
        if (!isWalkieNetworkAvailable) {
            WalkieTalkieManager.stopTransmit()
            WalkieTalkieManager.stop()
            callStatusText = ServerConfig.walkieNetworkErrorMessage()
            return@LaunchedEffect
        }

        if (!group.isNullOrBlank()) {
            WalkieTalkieManager.start(
                context = context,
                workerId = currentWorkerId,
                areaGroup = group
            )
        }
    }

    LaunchedEffect(currentWorkerId) {
        WalkieSignalingClient.listener = object : WalkieSignalingClient.Listener {
            override fun onConnected() {
                isWalkieNetworkAvailable = ServerConfig.isWalkieNetworkAvailable(context)
                if (!isWalkieNetworkAvailable) {
                    WalkieSignalingClient.disconnect(sendDisconnect = false)
                    WalkieTalkieManager.stopTransmit()
                    WalkieTalkieManager.stop()
                    callStatusText = ServerConfig.walkieNetworkErrorMessage()
                    showUserError(ServerConfig.walkieNetworkErrorMessage())
                    return
                }

                callStatusText = "신호 서버 연결됨"
                WalkieSignalingClient.missedCallsGet(currentWorkerId)
            }

            override fun onDisconnected() {
                if (activeCallId != null) {
                    clearCallState("신호 연결이 끊겼습니다. 다시 연결 중입니다.")
                } else {
                    callStatusText = "신호 연결 재시도 중"
                }
            }

            override fun onCallRinging(callId: String, toWorkerId: String) {
                activeCallId = callId
                isCallActive = false
                activePeerWorkerId = toWorkerId
                callStatusText = if (toWorkerId == MONITOR_WORKER_ID) {
                    "중앙관제 수신 요청 중..."
                } else {
                    "$toWorkerId 수신 요청 중..."
                }
            }

            override fun onIncomingCall(
                callId: String,
                fromWorkerId: String,
                fromName: String?,
                fromAreaGroup: String?
            ) {
                if (isEmergencyBroadcastActive || isCallActive || activeCallId != null) {
                    WalkieSignalingClient.rejectCall(
                        callId = callId,
                        workerId = currentWorkerId
                    )
                    return
                }

                val index = pendingIncomingCalls.indexOfFirst { it.callId == callId }
                val item = IncomingWalkieCallState(
                    callId = callId,
                    fromWorkerId = fromWorkerId,
                    fromName = fromName ?: fromWorkerId,
                    fromAreaGroup = fromAreaGroup
                )

                if (index >= 0) {
                    pendingIncomingCalls[index] = item
                } else {
                    pendingIncomingCalls.add(item)
                }

                isCallActive = false
                callStatusText = if (pendingIncomingCalls.size == 1) {
                    "${item.fromName ?: item.fromWorkerId} 연결 요청"
                } else {
                    "${pendingIncomingCalls.size}건 연결 요청"
                }
            }

            override fun onCallActive(
                callId: String,
                peerWorkerId: String,
                talkerId: String?
            ) {
                activeCallId = callId
                isCallActive = true
                activePeerWorkerId = peerWorkerId
                pendingIncomingCalls.clear()
                callStatusText = "통화 연결됨"
                setTargetForPeer(peerWorkerId)
                WebSocketForegroundService.refreshWalkieNotification(context)
            }

            override fun onCallRejected(callId: String, byWorkerId: String?) {
                pendingIncomingCalls.removeAll { it.callId == callId }

                if (activeCallId == callId) {
                    clearCallState("통화 거절됨")
                } else if (pendingIncomingCalls.isEmpty() && activeCallId == null) {
                    callStatusText = "대기 중"
                } else if (pendingIncomingCalls.isNotEmpty()) {
                    callStatusText = "${pendingIncomingCalls.size}건 연결 요청"
                }
            }

            override fun onCallFailed(callId: String, reason: String?, peerWorkerId: String?) {
                pendingIncomingCalls.removeAll { it.callId == callId }
                val message = callFailedReasonText(reason)

                if (activeCallId == callId) {
                    clearCallState(message)
                } else if (activeCallId == null && pendingIncomingCalls.isEmpty()) {
                    callStatusText = message
                } else if (pendingIncomingCalls.isNotEmpty()) {
                    callStatusText = "${pendingIncomingCalls.size}건 연결 요청"
                }
            }

            override fun onCallEnded(
                callId: String,
                reason: String?,
                byWorkerId: String?,
                peerWorkerId: String?
            ) {
                pendingIncomingCalls.removeAll { it.callId == callId }

                if (activeCallId == callId) {
                    val message = callEndedStatusText(reason)
                    clearCallState(message)

                    if (reason == "peer_ended") {
                        peerEndedDialogText = "상대방이 통화를 종료했습니다."
                    }
                } else if (pendingIncomingCalls.isEmpty() && activeCallId == null) {
                    callStatusText = "대기 중"
                } else if (pendingIncomingCalls.isNotEmpty()) {
                    callStatusText = "${pendingIncomingCalls.size}건 연결 요청"
                }
            }

            override fun onEmergencyBroadcastStarted(
                broadcastId: String,
                fromWorkerId: String,
                targetType: String?,
                targetAreaGroup: String?
            ) {
                stopMicNow()
                WalkieGlobalState.startEmergencyBroadcast(
                    broadcastId = broadcastId,
                    fromWorkerId = fromWorkerId,
                    targetType = targetType,
                    targetAreaGroup = targetAreaGroup
                )
                callStatusText = "긴급 전파 수신 중"
            }

            override fun onEmergencyBroadcastEnded(broadcastId: String, reason: String?) {
                WalkieGlobalState.endEmergencyBroadcast("긴급 전파 종료")
                callStatusText = "긴급 전파 종료"
            }

            override fun onMicState(callId: String, workerId: String, micOn: Boolean) {
                // MIC 상태 WebSocket 알림은 사용하지 않음
            }

            override fun onMissedCallsList(items: List<WalkieSignalingClient.MissedCallDto>) {
                WalkieMissedCallState.replaceAll(items)
            }

            override fun onMissedCallAdded(item: WalkieSignalingClient.MissedCallDto) {
                WalkieMissedCallState.upsert(item)
            }

            override fun onError(message: String) {
                val requestMessage = requestFailedReasonText(message)
                val isNetworkSignalError = requestMessage.contains("신호 서버") ||
                    requestMessage.contains("신호 연결") ||
                    requestMessage.contains("네트워크")

                if (isNetworkSignalError) {
                    showStableSignalError()
                    callStatusText = "신호 연결 재시도 중"
                } else {
                    showTransientUserError(requestMessage)
                }
            }
        }

    }


    LaunchedEffect(currentWorkerId) {
        // 화면 진입 시 서비스가 이미 연결되어 있으면 onConnected가 다시 호출되지 않을 수 있어
        // 현재 부재중 목록을 한 번 더 요청합니다.
        WalkieSignalingClient.missedCallsGet(currentWorkerId)
    }


    DisposableEffect(Unit) {
        onDispose {
            // 화면을 벗어나도 통화와 MIC 송신 상태는 유지합니다.
            // 실제 종료는 통화 종료 버튼, 긴급 전파, 로그아웃/앱 종료에서만 처리합니다.
            WalkieSignalingClient.listener = null
        }
    }

    val missedCalls = missedCallItems.map { toMissedUiState(it) }
    val missedCallListScrollState = rememberScrollState()

    val selectedGroup = myGroups.firstOrNull { groupKey(it) == selectedGroupCode }
    val groupWorkers = onlineWorkers.filter { worker ->
        val otherWorkerId = worker.workerId?.trim()

        selectedGroup != null &&
                !otherWorkerId.isNullOrBlank() &&
                otherWorkerId != currentWorkerId &&
                otherWorkerId != MONITOR_WORKER_ID &&
                worker.belongsToGroup(
                    groupCode = selectedGroup.groupCode,
                    groupName = selectedGroup.groupName
                )
    }

    val hasMonitorInOnlineWorkers = onlineWorkers.any {
        it.workerId?.trim() == MONITOR_WORKER_ID
    }

    peerEndedDialogText?.let { text ->
        AlertDialog(
            onDismissRequest = { peerEndedDialogText = null },
            title = { Text("통화 종료") },
            text = { Text(text) },
            confirmButton = {
                Button(onClick = { peerEndedDialogText = null }) {
                    Text("확인")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("무전기") },
                navigationIcon = {
                    OutlinedButton(
                        onClick = { leaveScreenWithoutEndingCall() },
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text("뒤로")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "통화 상태: $callStatusText",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )

            activePeerWorkerId?.let {
                Text(
                    text = "상대: ${if (it == MONITOR_WORKER_ID) MONITOR_NAME else it}",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (isLoading && onlineWorkers.isEmpty()) {
                CircularProgressIndicator()
            }

            errorMessage?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (isEmergencyBroadcastActive) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "긴급 전파 수신 중",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = "중앙관제 방송을 수신 중입니다. 이 동안 MIC 송신과 1:1 연결 요청은 비활성화됩니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }


            if (pendingIncomingCalls.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "연결 요청 목록 (${pendingIncomingCalls.size})",
                            fontWeight = FontWeight.Bold
                        )

                        pendingIncomingCalls.forEach { incoming ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "${incoming.fromName ?: incoming.fromWorkerId} 연결 요청",
                                        fontWeight = FontWeight.Bold
                                    )

                                    Text(
                                        text = "작업자 ID: ${incoming.fromWorkerId}",
                                        style = MaterialTheme.typography.bodySmall
                                    )

                                    incoming.fromAreaGroup?.takeIf { it.isNotBlank() }?.let { group ->
                                        Text(
                                            text = "그룹: $group",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = {
                                                WalkieSignalingClient.acceptCall(
                                                    callId = incoming.callId,
                                                    workerId = currentWorkerId
                                                )

                                                activeCallId = incoming.callId
                                                isCallActive = false
                                                activePeerWorkerId = incoming.fromWorkerId
                                                pendingIncomingCalls.removeAll { it.callId == incoming.callId }
                                                callStatusText = "통화 수락 중..."
                                            },
                                            enabled = activeCallId == null
                                        ) {
                                            Text("수신")
                                        }

                                        OutlinedButton(
                                            onClick = {
                                                WalkieSignalingClient.rejectCall(
                                                    callId = incoming.callId,
                                                    workerId = currentWorkerId
                                                )

                                                pendingIncomingCalls.removeAll { it.callId == incoming.callId }
                                                callStatusText = if (pendingIncomingCalls.isEmpty()) {
                                                    "대기 중"
                                                } else {
                                                    "${pendingIncomingCalls.size}건 연결 요청"
                                                }
                                            },
                                            enabled = activeCallId == null
                                        ) {
                                            Text("거절")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (missedCalls.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val unreadCount = missedCalls.count { !it.read }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "부재중 목록 (${missedCalls.size})" + if (unreadCount > 0) " / 미확인 $unreadCount" else "",
                                fontWeight = FontWeight.Bold
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        WalkieMissedCallState.replaceAll(emptyList())
                                        WalkieSignalingClient.missedCallsClear(currentWorkerId)
                                    }
                                ) {
                                    Text("모두 닫기")
                                }
                            }
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp)
                                .verticalScroll(missedCallListScrollState),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            missedCalls.forEach { missed ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "${missed.fromName ?: missed.fromWorkerId} 부재중 요청",
                                            fontWeight = if (missed.read) FontWeight.Normal else FontWeight.Bold
                                        )

                                        Text(
                                            text = "사유: ${missedReasonText(missed.reason)} / 시간: ${formatMissedTime(missed.endedAt)}",
                                            style = MaterialTheme.typography.bodySmall
                                        )

                                        missed.fromAreaGroup?.takeIf { it.isNotBlank() }?.let { group ->
                                            Text(
                                                text = "그룹: $group",
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }

                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Button(
                                                onClick = {
                                                    isWalkieNetworkAvailable = ServerConfig.isWalkieNetworkAvailable(context)
                                                    if (!isWalkieNetworkAvailable || !WalkieSignalingClient.isConnected()) {
                                                        showWalkieNetworkBlocked()
                                                        WalkieSignalingClient.disconnect(sendDisconnect = false)
                                                        WalkieTalkieManager.stopTransmit()
                                                        WalkieTalkieManager.stop()
                                                        return@Button
                                                    }

                                                    WalkieSignalingClient.requestCall(
                                                        fromWorkerId = currentWorkerId,
                                                        fromName = currentWorkerId,
                                                        fromAreaGroup = myAreaGroup,
                                                        toWorkerId = missed.fromWorkerId
                                                    )
                                                    WalkieSignalingClient.missedCallsMarkRead(
                                                        workerId = currentWorkerId,
                                                        missedId = missed.missedId
                                                    )
                                                    callStatusText = "${missed.fromWorkerId} 수신 요청 중..."
                                                },
                                                enabled = isWalkieNetworkAvailable && WalkieSignalingClient.isConnected() && !isEmergencyBroadcastActive && activeCallId == null && pendingIncomingCalls.isEmpty()
                                            ) {
                                                Text("다시 연결")
                                            }

                                            OutlinedButton(
                                                onClick = {
                                                    WalkieMissedCallState.remove(
                                                        missedId = missed.missedId,
                                                        callId = missed.callId
                                                    )
                                                    WalkieSignalingClient.missedCallsRemove(
                                                        workerId = currentWorkerId,
                                                        missedId = missed.missedId,
                                                        callId = missed.callId
                                                    )
                                                }
                                            ) {
                                                Text("닫기")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "그룹 선택",
                        fontWeight = FontWeight.Bold
                    )

                    if (myGroups.isEmpty()) {
                        Text(
                            text = if (isWalkieNetworkAvailable) {
                                "배정된 그룹이 없습니다."
                            } else {
                                "내부 Wi-Fi 연결 후 그룹 정보를 확인할 수 있습니다."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isWalkieNetworkAvailable) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    } else {
                        myGroups.forEach { group ->
                            val key = groupKey(group)
                            FilterChip(
                                selected = selectedGroupCode == key,
                                onClick = {
                                    if (activeCallId == null && pendingIncomingCalls.isEmpty()) {
                                        selectedGroupCode = key
                                        selectedWorkerIds.clear()
                                        selectedTarget = null
                                        WalkieTalkieManager.setTarget(null)
                                    }
                                },
                                label = { Text(groupDisplayName(group)) },
                                enabled = isWalkieNetworkAvailable && activeCallId == null && pendingIncomingCalls.isEmpty()
                            )
                        }
                    }

                    Text(
                        text = "송신 대상 선택",
                        fontWeight = FontWeight.Bold
                    )

                    FilterChip(
                        selected = isMonitorSelected(),
                        onClick = {
                            if (activeCallId == null) selectMonitor()
                        },
                        label = { Text(MONITOR_NAME) },
                        enabled = isWalkieNetworkAvailable && hasMonitorInOnlineWorkers && activeCallId == null && pendingIncomingCalls.isEmpty()
                    )

                    if (!isWalkieNetworkAvailable) {
                        Text(
                            text = "내부 Wi-Fi 연결 후 중앙관제 연결 상태를 확인할 수 있습니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else if (!hasMonitorInOnlineWorkers) {
                        Text(
                            text = "중앙관제가 현재 연결되어 있지 않습니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Text(
                        text = "선택 그룹 직원",
                        fontWeight = FontWeight.Bold
                    )

                    if (groupWorkers.isEmpty()) {
                        Text(
                            text = if (isWalkieNetworkAvailable) {
                                "선택한 그룹에 현재 접속 중인 직원이 없습니다."
                            } else {
                                "내부 Wi-Fi 연결 후 선택 그룹 직원 목록을 확인할 수 있습니다."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isWalkieNetworkAvailable) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    } else {
                        groupWorkers.forEach { worker ->
                            val targetWorkerId = worker.workerId?.trim() ?: return@forEach
                            val checked = selectedWorkerIds.containsKey(targetWorkerId)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = isWalkieNetworkAvailable && activeCallId == null && pendingIncomingCalls.isEmpty()) {
                                        toggleWorker(worker)
                                    },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = {
                                        if (isWalkieNetworkAvailable && activeCallId == null && pendingIncomingCalls.isEmpty()) toggleWorker(worker)
                                    },
                                    enabled = isWalkieNetworkAvailable && !isEmergencyBroadcastActive && activeCallId == null && pendingIncomingCalls.isEmpty()
                                )

                                Text(text = "${workerDisplayName(worker)} (${workerDisplayId(worker)})")
                            }
                        }
                    }

                    Text(
                        text = selectedTargetText(),
                        style = MaterialTheme.typography.bodySmall
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val target = selectedTarget

                                if (isEmergencyBroadcastActive) {
                                    errorMessage = "긴급 전파 수신 중에는 연결 요청을 보낼 수 없습니다."
                                    return@Button
                                }

                                isWalkieNetworkAvailable = ServerConfig.isWalkieNetworkAvailable(context)
                                if (!isWalkieNetworkAvailable || !WalkieSignalingClient.isConnected()) {
                                    showWalkieNetworkBlocked()
                                    WalkieSignalingClient.disconnect(sendDisconnect = false)
                                    WalkieTalkieManager.stopTransmit()
                                    WalkieTalkieManager.stop()
                                    return@Button
                                }

                                if (target == null) {
                                    errorMessage = "연결 대상을 선택하세요."
                                    return@Button
                                }

                                val targetWorkerId = when (target.targetType) {
                                    WalkieTargetType.USER -> target.targetWorkerId
                                        ?.split(",")
                                        ?.map { it.trim() }
                                        ?.firstOrNull { it.isNotBlank() }

                                    WalkieTargetType.GROUP -> null
                                    WalkieTargetType.ALL -> null
                                }

                                if (targetWorkerId.isNullOrBlank()) {
                                    errorMessage = "1대1 연결 대상만 선택할 수 있습니다."
                                    return@Button
                                }

                                WalkieSignalingClient.requestCall(
                                    fromWorkerId = currentWorkerId,
                                    fromName = currentWorkerId,
                                    fromAreaGroup = myAreaGroup,
                                    toWorkerId = targetWorkerId
                                )

                                callStatusText = if (targetWorkerId == MONITOR_WORKER_ID) {
                                    "중앙관제 수신 요청 중..."
                                } else {
                                    "$targetWorkerId 수신 요청 중..."
                                }
                            },
                            enabled = isWalkieNetworkAvailable && WalkieSignalingClient.isConnected() && !isEmergencyBroadcastActive && activeCallId == null && pendingIncomingCalls.isEmpty()
                        ) {
                            Text("연결 요청")
                        }

                        OutlinedButton(
                            onClick = { endCurrentCall() },
                            enabled = activeCallId != null && !isEmergencyBroadcastActive
                        ) {
                            Text("통화 종료")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Button(
                    onClick = {
                        val callId = activeCallId

                        if (isEmergencyBroadcastActive) {
                            errorMessage = "긴급 전파 수신 중에는 MIC를 사용할 수 없습니다."
                            return@Button
                        }

                        if (!isCallActive || callId.isNullOrBlank()) {
                            errorMessage = "먼저 통화를 연결하세요."
                            return@Button
                        }

                        if (isMicOn) {
                            stopMicNow()
                        } else {
                            val granted = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED

                            if (granted) {
                                startMicNow()
                            } else {
                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    },
                    enabled = isWalkieNetworkAvailable && isCallActive && !isEmergencyBroadcastActive,
                    modifier = Modifier
                        .size(140.dp)
                        .clip(CircleShape)
                        .border(
                            width = 3.dp,
                            color = if (isMicOn) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            shape = CircleShape
                        ),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isMicOn) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {

                        Text(
                            text = "MIC",
                            fontWeight = FontWeight.Bold
                        )

                        Text(text = if (isMicOn) "OFF" else "ON")
                    }
                }
            }

            Text(
                text = when {
                    isEmergencyBroadcastActive -> "긴급 전파 수신 중입니다. 중앙관제 음성만 수신합니다."
                    activeCallId == null -> "대상을 선택한 후 연결 요청을 보내세요."
                    isMicOn -> "내 MIC가 켜져 있습니다. 다시 누르면 꺼집니다."
                    else -> "통화 연결됨. MIC 버튼을 눌러 자유롭게 송신하세요."
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
