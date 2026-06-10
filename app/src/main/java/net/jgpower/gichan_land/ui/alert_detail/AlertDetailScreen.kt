package net.jgpower.gichan_land.ui.alert_detail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.jgpower.gichan_land.data.alert.WorkerAlert
import net.jgpower.gichan_land.network.ApiServiceManager
import net.jgpower.gichan_land.repository.AlertRepository

@Composable
fun AlertDetailScreen(
    alertId: String,
    workerId: String,
    onBackClick: () -> Unit,
    onActionStartSuccess: () -> Unit
) {
    BackHandler {
        onBackClick()
    }

    val alert = remember { mutableStateOf<WorkerAlert?>(null) }
    val errorMessage = remember { mutableStateOf<String?>(null) }
    val isLoading = remember { mutableStateOf(false) }

    val forceCloseDialog = remember { mutableStateOf(false) }
    val forceCloseTitle = remember { mutableStateOf("") }
    val forceCloseMessage = remember { mutableStateOf("") }

    val coroutineScope = rememberCoroutineScope()

    val alertRepository = remember {
        AlertRepository(ApiServiceManager.apiService)
    }

    fun shouldBlockAction(status: String?): Boolean {
        return status == "중앙 확인 중" || status == "조치 완료"
    }

    fun showForceCloseByStatus(status: String?) {
        when (status) {
            "중앙 확인 중" -> {
                forceCloseTitle.value = "중앙 확인 중"
                forceCloseMessage.value =
                    "해당 이벤트는 중앙 관제 확인 중입니다. 대응 처리 화면 접근이 제한됩니다."
                forceCloseDialog.value = true
            }

            "조치 완료" -> {
                forceCloseTitle.value = "조치 완료"
                forceCloseMessage.value =
                    "해당 이벤트는 조치 완료되었습니다. 대응 처리 화면 접근이 제한됩니다."
                forceCloseDialog.value = true
            }
        }
    }

    fun loadDetail() {
        coroutineScope.launch {
            isLoading.value = true
            errorMessage.value = null

            try {
                val response = alertRepository.getAlertDetail(alertId)

                if (response.isSuccessful) {
                    val latestAlert = response.body()
                    alert.value = latestAlert

                    if (shouldBlockAction(latestAlert?.status)) {
                        // 상세 화면에서는 바로 닫지 않고 안내 문구 표시만 유지
                        // 다이얼로그는 폴링 중 상태 변경 감지 시 표시
                    }
                } else {
                    errorMessage.value = "알림 상세 조회 실패: ${response.code()}"
                }
            } catch (_: Exception) {
                errorMessage.value = "서버에 연결할 수 없습니다."
            } finally {
                isLoading.value = false
            }
        }
    }

    fun startAction() {
        coroutineScope.launch {
            isLoading.value = true
            errorMessage.value = null

            try {
                val latestResponse = alertRepository.getAlertDetail(alertId)
                val latestStatus = latestResponse.body()?.status

                if (shouldBlockAction(latestStatus)) {
                    showForceCloseByStatus(latestStatus)
                    return@launch
                }

                if (latestStatus != "조치 전") {
                    errorMessage.value = "현재 상태에서는 대응을 시작할 수 없습니다."
                    return@launch
                }

                val response = alertRepository.startAlert(
                    alertId = alertId,
                    workerId = workerId
                )

                if (response.isSuccessful) {
                    onActionStartSuccess()
                } else {
                    errorMessage.value = "대응 시작 실패: ${response.code()}"
                }
            } catch (_: Exception) {
                errorMessage.value = "서버에 연결할 수 없습니다."
            } finally {
                isLoading.value = false
            }
        }
    }

    fun moveActionReport() {
        coroutineScope.launch {
            isLoading.value = true
            errorMessage.value = null

            try {
                val latestResponse = alertRepository.getAlertDetail(alertId)
                val latestStatus = latestResponse.body()?.status

                if (shouldBlockAction(latestStatus)) {
                    showForceCloseByStatus(latestStatus)
                    return@launch
                }

                if (latestStatus == "조치 중") {
                    onActionStartSuccess()
                } else {
                    errorMessage.value = "현재 상태에서는 대응 처리 화면으로 이동할 수 없습니다."
                    latestResponse.body()?.let {
                        alert.value = it
                    }
                }
            } catch (_: Exception) {
                errorMessage.value = "서버에 연결할 수 없습니다."
            } finally {
                isLoading.value = false
            }
        }
    }

    LaunchedEffect(alertId) {
        loadDetail()
    }

    LaunchedEffect(alertId) {
        var previousStatus: String? = null

        while (true) {
            try {
                val response = alertRepository.getAlertDetail(alertId)

                if (response.isSuccessful) {
                    val latestAlert = response.body()

                    if (latestAlert != null) {
                        val latestStatus = latestAlert.status

                        alert.value = latestAlert

                        if (
                            previousStatus != null &&
                            previousStatus != latestStatus &&
                            shouldBlockAction(latestStatus)
                        ) {
                            showForceCloseByStatus(latestStatus)
                            break
                        }

                        previousStatus = latestStatus
                    }
                }
            } catch (_: Exception) {
                // 조회 실패 시 다음 주기에 재시도
            }

            delay(3000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "알림 상세",
            style = MaterialTheme.typography.headlineSmall
        )

        if (isLoading.value && alert.value == null) {
            Text("알림 상세를 불러오는 중...")
        }

        errorMessage.value?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error
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

            when (currentAlert.status) {
                "조치 전" -> {
                    Button(
                        onClick = { startAction() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading.value
                    ) {
                        Text("대응 시작")
                    }
                }

                "조치 중" -> {
                    Button(
                        onClick = { moveActionReport() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading.value
                    ) {
                        Text("대응 처리 화면으로 이동")
                    }
                }

                "중앙 확인 중" -> {
                    Text(
                        text = "중앙 관제 확인 중입니다. 확인 결과에 따라 조치 완료 또는 조치 중 상태로 변경됩니다.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                "조치 완료" -> {
                    Text(
                        text = "이미 조치 완료된 알림입니다.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                else -> {
                    Text(
                        text = "현재 상태에서는 대응 처리를 진행할 수 없습니다.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
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
                onBackClick()
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
                        onBackClick()
                    }
                ) {
                    Text("확인")
                }
            }
        )
    }
}