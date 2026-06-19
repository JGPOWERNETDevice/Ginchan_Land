package net.jgpower.gichan_land.ui.action_report

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.jgpower.gichan_land.data.alert.WorkerAlert
import net.jgpower.gichan_land.network.ApiServiceManager
import net.jgpower.gichan_land.repository.AlertRepository
import net.jgpower.gichan_land.repository.EmergencyRepository
@Composable
fun ActionReportScreen(
    alertId: String,
    workerId: String,
    onBackClick: () -> Unit,
    onCompleteSuccess: () -> Unit
) {
    BackHandler {
        onBackClick()
    }

    val alert = remember { mutableStateOf<WorkerAlert?>(null) }
    val actionContent = remember { mutableStateOf("") }

    val errorMessage = remember { mutableStateOf<String?>(null) }
    val resultMessage = remember { mutableStateOf<String?>(null) }
    val isLoading = remember { mutableStateOf(false) }
    val isEmergencyCalling = remember { mutableStateOf(false) }

    val forceCloseDialog = remember { mutableStateOf(false) }
    val forceCloseTitle = remember { mutableStateOf("") }
    val forceCloseMessage = remember { mutableStateOf("") }

    val coroutineScope = rememberCoroutineScope()

    val alertRepository = remember {
        AlertRepository(ApiServiceManager.apiService)
    }

    val emergencyRepository = remember {
        EmergencyRepository(ApiServiceManager.apiService)
    }

    fun isBlockedStatus(status: String?): Boolean {
        return status == "중앙 확인 중" || status == "조치 완료"
    }

    fun showBlockedDialog(status: String?) {
        when (status) {
            "중앙 확인 중" -> {
                forceCloseTitle.value = "중앙 확인 중"
                forceCloseMessage.value =
                    "해당 이벤트는 중앙 관제 확인 중입니다. 대응 처리 화면을 종료합니다."
                forceCloseDialog.value = true
            }

            "조치 완료" -> {
                forceCloseTitle.value = "조치 완료"
                forceCloseMessage.value =
                    "해당 이벤트는 이미 조치 완료되었습니다. 대응 처리 화면을 종료합니다."
                forceCloseDialog.value = true
            }

            else -> {
                forceCloseTitle.value = "접근 불가"
                forceCloseMessage.value =
                    "현재 상태에서는 대응 처리 화면에 접근할 수 없습니다."
                forceCloseDialog.value = true
            }
        }
    }

    fun loadAlertStatus() {
        coroutineScope.launch {
            isLoading.value = true
            errorMessage.value = null

            try {
                val response = alertRepository.getAlertDetail(alertId)

                if (response.isSuccessful) {
                    val latestAlert = response.body()
                    alert.value = latestAlert

                    val status = latestAlert?.status

                    if (isBlockedStatus(status)) {
                        showBlockedDialog(status)
                    } else if (status != "조치 중") {
                        errorMessage.value = "현재 상태에서는 대응 처리를 진행할 수 없습니다."
                    }
                } else {
                    errorMessage.value = "알림 상태 확인 실패: ${response.code()}"
                }
            } catch (_: Exception) {
                errorMessage.value = "서버에 연결할 수 없습니다."
            } finally {
                isLoading.value = false
            }
        }
    }

    fun callEmergency(type: String) {
        val currentAlert = alert.value
        val currentStatus = currentAlert?.status

        if (isBlockedStatus(currentStatus)) {
            showBlockedDialog(currentStatus)
            return
        }

        if (currentStatus != "조치 중") {
            errorMessage.value = "조치 중 상태에서만 응급 호출을 할 수 있습니다."
            return
        }

        val eventId = currentAlert.eventId

        if (eventId.isBlank()) {
            errorMessage.value = "이벤트 정보를 찾을 수 없습니다."
            return
        }

        coroutineScope.launch {
            isEmergencyCalling.value = true
            errorMessage.value = null
            resultMessage.value = null

            try {
                val latestResponse = alertRepository.getAlertDetail(alertId)
                val latestAlert = latestResponse.body()
                val latestStatus = latestAlert?.status

                if (latestAlert != null) {
                    alert.value = latestAlert
                }

                if (isBlockedStatus(latestStatus)) {
                    showBlockedDialog(latestStatus)
                    return@launch
                }

                if (latestStatus != "조치 중") {
                    errorMessage.value = "현재 상태에서는 응급 호출을 할 수 없습니다."
                    return@launch
                }

                val response = emergencyRepository.callEmergency(
                    eventId = eventId,
                    type = type
                )

                if (response.success) {
                    resultMessage.value = when (type) {
                        "rescue" -> "구조대 호출이 접수되었습니다."
                        "police" -> "경찰 호출이 접수되었습니다."
                        else -> "응급 호출이 접수되었습니다."
                    }
                } else {
                    errorMessage.value = response.message.ifBlank {
                        "응급 호출 실패"
                    }
                }
            } catch (_: Exception) {
                errorMessage.value = "서버에 연결할 수 없습니다."
            } finally {
                isEmergencyCalling.value = false
            }
        }
    }

    fun submitActionReport() {
        val currentStatus = alert.value?.status

        if (isBlockedStatus(currentStatus)) {
            showBlockedDialog(currentStatus)
            return
        }

        if (currentStatus != "조치 중") {
            errorMessage.value = "조치 중 상태에서만 완료 보고를 할 수 있습니다."
            return
        }

        if (actionContent.value.isBlank()) {
            errorMessage.value = "조치 내용을 입력하세요."
            return
        }

        coroutineScope.launch {
            isLoading.value = true
            errorMessage.value = null
            resultMessage.value = null

            try {
                val latestResponse = alertRepository.getAlertDetail(alertId)
                val latestStatus = latestResponse.body()?.status

                if (isBlockedStatus(latestStatus)) {
                    showBlockedDialog(latestStatus)
                    return@launch
                }

                if (latestStatus != "조치 중") {
                    errorMessage.value = "현재 상태에서는 완료 보고를 할 수 없습니다."
                    latestResponse.body()?.let {
                        alert.value = it
                    }
                    return@launch
                }

                val response = alertRepository.completeAlert(
                    alertId = alertId,
                    workerId = workerId,
                    actionContent = actionContent.value.trim()
                )

                if (response.isSuccessful) {
                    resultMessage.value = "조치 내용이 중앙 관제 확인 요청되었습니다."
                    onCompleteSuccess()
                } else {
                    errorMessage.value = "완료 보고 실패: ${response.code()}"
                }
            } catch (_: Exception) {
                errorMessage.value = "서버에 연결할 수 없습니다."
            } finally {
                isLoading.value = false
            }
        }
    }

    LaunchedEffect(alertId) {
        loadAlertStatus()
    }

    LaunchedEffect(alertId) {
        while (true) {
            try {
                val response = alertRepository.getAlertDetail(alertId)

                if (response.isSuccessful) {
                    val latestAlert = response.body()

                    if (latestAlert != null) {
                        alert.value = latestAlert

                        if (isBlockedStatus(latestAlert.status)) {
                            showBlockedDialog(latestAlert.status)
                            break
                        }
                    }
                }
            } catch (_: Exception) {
                // 다음 주기에 재시도
            }

            delay(3000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
            .padding(bottom = 56.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "대응 처리 보고",
            style = MaterialTheme.typography.headlineSmall
        )

        if (isLoading.value && alert.value == null) {
            Text("알림 상태를 확인하는 중...")
        }

        errorMessage.value?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error
            )
        }

        resultMessage.value?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.primary
            )
        }

        val currentAlert = alert.value

        if (currentAlert != null) {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("알림 ID: ${currentAlert.alertId}")
                    Text("이벤트 ID: ${currentAlert.eventId}")
                    Text("수신자: ${currentAlert.receiverId}")
                    Text("전파 방식: ${currentAlert.receiveType}")
                    Text("발생 시간: ${currentAlert.occurredAt}")
                    Text("대상: ${currentAlert.targetType ?: "미지정"}")
                    Text("내용: ${currentAlert.message}")
                    Text("상태: ${currentAlert.status}")
                }
            }

            if (currentAlert.status == "조치 중") {
                OutlinedTextField(
                    value = actionContent.value,
                    onValueChange = {
                        actionContent.value = it
                    },
                    label = {
                        Text("조치 내용")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 5
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "응급 호출",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            callEmergency("rescue")
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !isLoading.value && !isEmergencyCalling.value
                    ) {
                        Text(if (isEmergencyCalling.value) "호출 중..." else "구조대 호출")
                    }

                    Button(
                        onClick = {
                            callEmergency("police")
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !isLoading.value && !isEmergencyCalling.value
                    ) {
                        Text(if (isEmergencyCalling.value) "호출 중..." else "경찰 호출")
                    }
                }

                Button(
                    onClick = {
                        submitActionReport()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading.value && !isEmergencyCalling.value
                ) {
                    Text(if (isLoading.value) "보고 중..." else "완료 보고")
                }
            }

            if (currentAlert.status == "중앙 확인 중") {
                Text(
                    text = "중앙 관제 확인 중입니다. 대응 처리 화면 접근이 제한됩니다.",
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (currentAlert.status == "조치 완료") {
                Text(
                    text = "이미 조치 완료된 알림입니다.",
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        OutlinedButton(
            onClick = onBackClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("뒤로가기")
        }
    }

    if (forceCloseDialog.value) {
        AlertDialog(
            onDismissRequest = {
                forceCloseDialog.value = false
                onCompleteSuccess()
            },
            title = {
                Text(forceCloseTitle.value)
            },
            text = {
                Text(forceCloseMessage.value)
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        forceCloseDialog.value = false
                        onCompleteSuccess()
                    }
                ) {
                    Text("확인")
                }
            }
        )
    }
}