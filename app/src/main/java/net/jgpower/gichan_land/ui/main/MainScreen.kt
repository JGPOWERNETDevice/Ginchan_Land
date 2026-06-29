package net.jgpower.gichan_land.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import net.jgpower.gichan_land.data.alert.WorkerAlert
import net.jgpower.gichan_land.data.emergency.EmergencyPresenceState
import net.jgpower.gichan_land.network.ApiServiceManager
import net.jgpower.gichan_land.network.AppWebSocketManager
import net.jgpower.gichan_land.repository.AlertRepository
import net.jgpower.gichan_land.repository.EmergencyRepository
import net.jgpower.gichan_land.repository.EventRepository
import net.jgpower.gichan_land.service.AppNotificationManager

@Composable
fun MainScreen(
    workerId: String,
    groupRefreshKey: Int = 0,
    onAlertClick: (String) -> Unit,
    onActionReportClick: (String) -> Unit,
    onCreateEventClick: () -> Unit,
    onNoticeClick: () -> Unit,
    onWalkieTalkieClick: () -> Unit,
    onWatchConnectClick: () -> Unit,
    onGroupEditClick: () -> Unit,
    onLogoutClick: () -> Unit,
    onExitAppClick: () -> Unit
) {
    BackHandler {
        // 메인 화면에서는 뒤로가기 버튼으로 앱 종료 방지
    }

    val alerts = remember { mutableStateListOf<WorkerAlert>() }
    val errorMessage = remember { mutableStateOf<String?>(null) }
    val isLoading = remember { mutableStateOf(false) }
    val myGroupNames = remember { mutableStateOf("") }
    val sosMessage = remember { mutableStateOf<String?>(null) }
    val isSosReporting = remember { mutableStateOf(false) }

    val statusOptions = listOf("조치 전", "조치 중", "중앙 확인 중", "조치 완료")

    val selectedStatuses = remember {
        mutableStateListOf("조치 전", "조치 중", "중앙 확인 중", "조치 완료")
    }

    val filteredAlerts = alerts.filter { alert ->
        alert.status in selectedStatuses
    }

    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val knownEventIds = remember { mutableSetOf<String>() }
    val isFirstLoad = remember { mutableStateOf(true) }

    val alertRepository = remember {
        AlertRepository(ApiServiceManager.apiService)
    }

    val emergencyRepository = remember {
        EmergencyRepository(ApiServiceManager.apiService)
    }

    val eventRepository = remember {
        EventRepository(ApiServiceManager.apiService)
    }

    val emergencyPresence by EmergencyPresenceState.state.collectAsState()

    fun toggleStatus(status: String) {
        if (selectedStatuses.contains(status)) {
            selectedStatuses.remove(status)
        } else {
            selectedStatuses.add(status)
        }
    }

    fun isNewerAlert(newAlert: WorkerAlert, oldAlert: WorkerAlert): Boolean {
        return newAlert.occurredAt >= oldAlert.occurredAt
    }

    fun upsertAlertByEventId(newAlert: WorkerAlert) {
        val index = alerts.indexOfFirst { it.eventId == newAlert.eventId }

        if (index >= 0) {
            val oldAlert = alerts[index]

            if (isNewerAlert(newAlert, oldAlert)) {
                alerts[index] = newAlert
            }
        } else {
            alerts.add(0, newAlert)
        }

        alerts.sortByDescending { it.occurredAt }
        knownEventIds.add(newAlert.eventId)
    }

    fun latestAlertsByEventId(source: List<WorkerAlert>): List<WorkerAlert> {
        return source
            .groupBy { it.eventId }
            .mapNotNull { (_, groupedAlerts) ->
                groupedAlerts.maxByOrNull { it.occurredAt }
            }
            .sortedByDescending { it.occurredAt }
    }

    fun loadAlerts() {
        coroutineScope.launch {
            isLoading.value = true
            errorMessage.value = null

            try {
                val response = alertRepository.getMyAlerts(workerId)

                if (response.isSuccessful) {
                    val body: List<WorkerAlert> = response.body() ?: emptyList()
                    val latestList = latestAlertsByEventId(body)

                    if (isFirstLoad.value) {
                        knownEventIds.clear()
                        knownEventIds.addAll(latestList.map { it.eventId })
                        isFirstLoad.value = false
                    }

                    alerts.clear()
                    alerts.addAll(latestList)
                } else {
                    errorMessage.value = "알림 조회 실패"
                }
            } catch (_: Exception) {
                errorMessage.value = "서버에 연결할 수 없습니다."
            } finally {
                isLoading.value = false
            }
        }
    }

    fun loadPresenceStatus() {
        coroutineScope.launch {
            try {
                val response = emergencyRepository.getPresenceStatus()
                val data = response.data

                if (response.success && data != null) {
                    EmergencyPresenceState.update(
                        rescueStatus = data.rescueStatus,
                        rescueUpdatedAt = data.rescueUpdatedAt,
                        policeStatus = data.policeStatus,
                        policeUpdatedAt = data.policeUpdatedAt
                    )
                }
            } catch (_: Exception) {
                // 재실 상태 조회 실패 시 메인 화면 동작은 유지
            }
        }
    }

    fun loadMyGroups() {
        coroutineScope.launch {
            try {
                val response = ApiServiceManager.apiService.getMyGroups(workerId)
                if (response.isSuccessful) {
                    val body = response.body()
                    myGroupNames.value = body?.data
                        ?.groups
                        .orEmpty()
                        .joinToString(", ") { group ->
                            group.groupName.ifBlank { group.groupCode }
                        }
                } else {
                    myGroupNames.value = ""
                }
            } catch (_: Exception) {
                myGroupNames.value = ""
            }
        }
    }


    fun reportSosEvent() {
        if (isSosReporting.value) return

        coroutineScope.launch {
            isSosReporting.value = true
            sosMessage.value = null
            errorMessage.value = null

            try {
                val response = eventRepository.createSosEvent(workerId)

                if (response.isSuccessful) {
                    sosMessage.value = "" +
                            "SOS 현장 구조 상황이 즉시 보고되었습니다."
                    loadAlerts()
                } else {
                    errorMessage.value = "SOS 보고 실패: ${response.code()}"
                }
            } catch (_: Exception) {
                errorMessage.value = "서버에 연결할 수 없습니다."
            } finally {
                isSosReporting.value = false
            }
        }
    }

    LaunchedEffect(workerId) {
        loadAlerts()
        loadPresenceStatus()
    }

    LaunchedEffect(workerId, groupRefreshKey) {
        loadMyGroups()
    }

    DisposableEffect(workerId) {
        AppWebSocketManager.onSafetyAlertReceived = { data ->
            coroutineScope.launch {
                val alert = WorkerAlert(
                    alertId = data.optString("alertId"),
                    eventId = data.optString("eventId"),
                    receiverId = data.optString("receiverId"),
                    receiveType = data.optString("receiveType"),
                    targetType = data.optString("targetType").ifBlank { null },
                    message = data.optString("message"),
                    occurredAt = data.optString("occurredAt"),
                    status = data.optString("status")
                )

                if (alert.alertId.isNotBlank() && alert.eventId.isNotBlank()) {
                    upsertAlertByEventId(alert)

                    AppNotificationManager.showAlertNotification(
                        context = context,
                        alert = alert
                    )
                }
            }
        }

        AppWebSocketManager.onTextAlertReceived = null

        onDispose {
            AppWebSocketManager.onSafetyAlertReceived = null
            AppWebSocketManager.onTextAlertReceived = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "현장 직원 알림",
                    style = MaterialTheme.typography.headlineSmall
                )

                Text(
                    text = "직원 ID: $workerId",
                    style = MaterialTheme.typography.bodyMedium
                )

                if (myGroupNames.value.isNotBlank()) {
                    Text(
                        text = "소속 그룹: ${myGroupNames.value}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        onLogoutClick()
                    },
                    modifier = Modifier
                        .height(36.dp)
                        .widthIn(min = 72.dp)
                ) {
                    Text(
                        text = "로그아웃",
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1
                    )
                }

                OutlinedButton(
                    onClick = {
                        onExitAppClick()
                    },
                    modifier = Modifier
                        .height(36.dp)
                        .widthIn(min = 72.dp)
                ) {
                    Text(
                        text = "앱 종료",
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        /*
        Button(
            onClick = onCreateEventClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("상황 발견 보고")
        }

        Spacer(modifier = Modifier.height(12.dp))
        */

        Button(
            onClick = {
                reportSosEvent()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            enabled = !isSosReporting.value,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError
            )
        ) {
            Text(if (isSosReporting.value) "SOS 보고 중..." else "SOS")
        }

        sosMessage.value?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onNoticeClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("공지사항")
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onWalkieTalkieClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("무전기")
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onWatchConnectClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("워치 연결")
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onGroupEditClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("그룹 수정")
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {
                loadAlerts()
                loadPresenceStatus()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("새로고침")
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "내 알림 목록",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            statusOptions.forEach { status ->
                val isSelected = selectedStatuses.contains(status)

                OutlinedButton(
                    onClick = {
                        toggleStatus(status)
                    },
                    modifier = Modifier
                        .height(36.dp)
                        .alpha(if (isSelected) 1f else 0.75f)
                ) {
                    Text(
                        text = status,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isLoading.value && alerts.isEmpty()) {
            Text("알림을 불러오는 중...")
        }

        errorMessage.value?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error
            )
        }

        if (!isLoading.value && filteredAlerts.isEmpty() && errorMessage.value == null) {
            Text("선택한 상태의 알림이 없습니다.")
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(filteredAlerts) { alert ->
                AlertListItem(
                    alert = alert,
                    onClick = {
                        when (alert.status) {
                            "조치 중" -> {
                                onActionReportClick(alert.alertId)
                            }

                            else -> {
                                onAlertClick(alert.alertId)
                            }
                        }
                    }
                )
            }
        }

        EmergencyPresenceBottomLine(
            rescueStatus = emergencyPresence.rescueStatus,
            policeStatus = emergencyPresence.policeStatus
        )
    }
}

@Composable
private fun AlertListItem(
    alert: WorkerAlert,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = alert.message,
                style = MaterialTheme.typography.titleMedium
            )

            Text(
                text = "수신자: ${alert.receiverId}",
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = "전파 방식: ${alert.receiveType}",
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = "발생 시간: ${alert.occurredAt}",
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = "대상: ${alert.targetType ?: "미지정"}",
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = "상태: ${alert.status}",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun EmergencyPresenceBottomLine(
    rescueStatus: String,
    policeStatus: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) {
        HorizontalDivider()

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            EmergencyPresenceMiniItem(
                label = "구조대",
                status = rescueStatus,
                modifier = Modifier.weight(1f)
            )

            EmergencyPresenceMiniItem(
                label = "경찰",
                status = policeStatus,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun EmergencyPresenceMiniItem(
    label: String,
    status: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$label 재실 여부",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )

        Text(
            text = " / ",
            fontSize = 12.sp
        )

        Text(
            text = status,
            fontSize = 12.sp
        )
    }
}