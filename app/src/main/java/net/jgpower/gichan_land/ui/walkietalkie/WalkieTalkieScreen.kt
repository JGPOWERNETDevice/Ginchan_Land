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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import net.jgpower.gichan_land.data.walkie.OnlineWorkerDto
import net.jgpower.gichan_land.data.walkie.WalkieTarget
import net.jgpower.gichan_land.data.walkie.WalkieTargetType
import net.jgpower.gichan_land.network.ApiServiceManager
import net.jgpower.gichan_land.network.ServerConfig
import net.jgpower.gichan_land.network.WalkieSignalingClient
import net.jgpower.gichan_land.network.WalkieTalkieManager

private const val MONITOR_WORKER_ID = "monitor"
private const val MONITOR_NAME = "중앙관제"
private const val MONITOR_GROUP = "중앙관제"

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
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var myAreaGroup by remember { mutableStateOf<String?>(null) }
    var isGroupSelected by remember { mutableStateOf(true) }
    var selectedTarget by remember { mutableStateOf<WalkieTarget?>(null) }

    var isMicOn by remember { mutableStateOf(false) }
    var activeCallId by remember { mutableStateOf<String?>(null) }
    var isCallActive by remember { mutableStateOf(false) }
    var activePeerWorkerId by remember { mutableStateOf<String?>(null) }

    var pendingIncomingCallId by remember { mutableStateOf<String?>(null) }
    var pendingIncomingFromWorkerId by remember { mutableStateOf<String?>(null) }
    var pendingIncomingFromName by remember { mutableStateOf<String?>(null) }

    var callStatusText by remember { mutableStateOf("대기 중") }

    val latestActiveCallId by rememberUpdatedState(activeCallId)

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

        return if (isGroupSelected) {
            "현재 선택 대상: 그룹 전체"
        } else {
            val names = selectedWorkerIds.values.joinToString(", ") { workerDisplayName(it) }
            "현재 선택 대상: ${names.ifBlank { "선택 없음" }}"
        }
    }

    fun clearCallState(message: String = "대기 중") {
        activeCallId = null
        isCallActive = false
        activePeerWorkerId = null
        pendingIncomingCallId = null
        pendingIncomingFromWorkerId = null
        pendingIncomingFromName = null
        isMicOn = false
        callStatusText = message
        WalkieTalkieManager.stopTransmit()
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
        WalkieSignalingClient.disconnect()
        onBackClick()
    }

    fun startMicNow() {
        val callId = activeCallId

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
    }

    fun stopMicNow() {
        WalkieTalkieManager.stopTransmit()
        isMicOn = false
        callStatusText = "통화 연결됨"
    }

    BackHandler { exitScreen() }

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
            errorMessage = null

            val list = ApiServiceManager.apiService.getOnlineWorkers()

            onlineWorkers.clear()
            onlineWorkers.addAll(list)

            val me = list.firstOrNull { it.workerId?.trim() == currentWorkerId }
            myAreaGroup = me?.areaGroup?.trim()

            if (selectedTarget == null && !myAreaGroup.isNullOrBlank()) {
                selectGroupAll()
            }
        } catch (e: Exception) {
            errorMessage = "온라인 직원 목록 조회 실패: ${e.message}"
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

    LaunchedEffect(currentWorkerId, myAreaGroup) {
        val group = myAreaGroup

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
                callStatusText = "신호 서버 연결됨"
            }

            override fun onDisconnected() {
                if (activeCallId != null) {
                    clearCallState("신호 서버 연결 끊김")
                } else {
                    callStatusText = "신호 서버 연결 끊김"
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
                pendingIncomingCallId = callId
                isCallActive = false
                pendingIncomingFromWorkerId = fromWorkerId
                pendingIncomingFromName = fromName ?: fromWorkerId
                callStatusText = "${fromName ?: fromWorkerId} 연결 요청"
            }

            override fun onCallActive(
                callId: String,
                peerWorkerId: String,
                talkerId: String?
            ) {
                activeCallId = callId
                isCallActive = true
                activePeerWorkerId = peerWorkerId
                callStatusText = "통화 연결됨"
                setTargetForPeer(peerWorkerId)
            }

            override fun onCallRejected(callId: String, byWorkerId: String?) {
                clearCallState("통화 거절됨")
            }

            override fun onCallEnded(callId: String, reason: String?) {
                clearCallState("통화 종료됨: ${reason ?: "-"}")
            }

            override fun onMicState(callId: String, workerId: String, micOn: Boolean) {
                // MIC 상태 WebSocket 알림은 사용하지 않음
            }

            override fun onError(message: String) {
                errorMessage = "신호 오류: $message"
                callStatusText = "신호 오류"
            }
        }

        WalkieSignalingClient.connect(
            serverBaseUrl = ServerConfig.getBaseHttpUrl(context),
            workerId = currentWorkerId
        )
    }

    LaunchedEffect(currentWorkerId) {
        while (true) {
            WalkieSignalingClient.ping()
            delay(5000L)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            val callId = latestActiveCallId

            if (!callId.isNullOrBlank()) {
                WalkieSignalingClient.endCall(
                    callId = callId,
                    workerId = currentWorkerId
                )
            }

            WalkieTalkieManager.stopTransmit()
            WalkieSignalingClient.disconnect()
            WalkieSignalingClient.listener = null
        }
    }

    val groupWorkers = onlineWorkers.filter { worker ->
        val otherWorkerId = worker.workerId?.trim()
        val otherAreaGroup = worker.areaGroup?.trim()
        val myGroup = myAreaGroup?.trim()

        !otherWorkerId.isNullOrBlank() &&
                otherWorkerId != currentWorkerId &&
                otherWorkerId != MONITOR_WORKER_ID &&
                (
                        myGroup.isNullOrBlank() ||
                                otherAreaGroup == myGroup
                        )
    }

    val hasMonitorInOnlineWorkers = onlineWorkers.any {
        it.workerId?.trim() == MONITOR_WORKER_ID
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("무전기") },
                navigationIcon = {
                    OutlinedButton(
                        onClick = { exitScreen() },
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
                text = "내 그룹: ${myAreaGroup ?: "-"}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

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

            pendingIncomingCallId?.let { callId ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "${pendingIncomingFromName ?: pendingIncomingFromWorkerId} 연결 요청",
                            fontWeight = FontWeight.Bold
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    WalkieSignalingClient.acceptCall(
                                        callId = callId,
                                        workerId = currentWorkerId
                                    )

                                    pendingIncomingCallId = null
                                    pendingIncomingFromWorkerId = null
                                    pendingIncomingFromName = null
                                    callStatusText = "통화 수락 중..."
                                }
                            ) {
                                Text("수신")
                            }

                            OutlinedButton(
                                onClick = {
                                    WalkieSignalingClient.rejectCall(
                                        callId = callId,
                                        workerId = currentWorkerId
                                    )

                                    pendingIncomingCallId = null
                                    pendingIncomingFromWorkerId = null
                                    pendingIncomingFromName = null
                                    callStatusText = "대기 중"
                                }
                            ) {
                                Text("거절")
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
                        text = "송신 대상 선택",
                        fontWeight = FontWeight.Bold
                    )

                    FilterChip(
                        selected = isGroupSelected,
                        onClick = {
                            if (activeCallId == null) selectGroupAll()
                        },
                        label = { Text("그룹 전체") },
                        enabled = !myAreaGroup.isNullOrBlank() && activeCallId == null
                    )

                    FilterChip(
                        selected = isMonitorSelected(),
                        onClick = {
                            if (activeCallId == null) selectMonitor()
                        },
                        label = { Text(MONITOR_NAME) },
                        enabled = hasMonitorInOnlineWorkers && activeCallId == null
                    )

                    if (!hasMonitorInOnlineWorkers) {
                        Text(
                            text = "중앙관제가 현재 연결되어 있지 않습니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Text(
                        text = "1:1 연결할 직원 선택",
                        fontWeight = FontWeight.Bold
                    )

                    if (groupWorkers.isEmpty()) {
                        Text(
                            text = "현재 선택 가능한 직원이 없습니다.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        groupWorkers.forEach { worker ->
                            val targetWorkerId = worker.workerId?.trim() ?: return@forEach
                            val checked = selectedWorkerIds.containsKey(targetWorkerId)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = activeCallId == null) {
                                        toggleWorker(worker)
                                    },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = {
                                        if (activeCallId == null) toggleWorker(worker)
                                    },
                                    enabled = activeCallId == null
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
                            enabled = activeCallId == null
                        ) {
                            Text("연결 요청")
                        }

                        OutlinedButton(
                            onClick = { endCurrentCall() },
                            enabled = activeCallId != null
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
                    enabled = isCallActive,
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
                    activeCallId == null -> "대상을 선택한 후 연결 요청을 보내세요."
                    isMicOn -> "내 MIC가 켜져 있습니다. 다시 누르면 꺼집니다."
                    else -> "통화 연결됨. MIC 버튼을 눌러 자유롭게 송신하세요."
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
