package net.jgpower.gichan_land.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import net.jgpower.gichan_land.network.ApiServiceManager
import net.jgpower.gichan_land.repository.EmergencyRepository

@Composable
fun EmergencyCallSection(
    eventId: String,
    modifier: Modifier = Modifier
) {
    val emergencyRepository = remember {
        EmergencyRepository(ApiServiceManager.apiService)
    }

    val scope = rememberCoroutineScope()

    var showDialog by remember { mutableStateOf(false) }
    var isCalling by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf<String?>(null) }

    fun requestEmergencyCall(type: String) {
        if (isCalling) return

        scope.launch {
            isCalling = true

            try {
                val response = emergencyRepository.callEmergency(
                    eventId = eventId,
                    type = type
                )

                resultMessage = response.message
            } catch (e: Exception) {
                resultMessage = "응급 호출 요청 중 오류가 발생했습니다."
            } finally {
                isCalling = false
                showDialog = false
            }
        }
    }

    Button(
        onClick = { showDialog = true },
        enabled = !isCalling && eventId.isNotBlank(),
        modifier = modifier.fillMaxWidth()
    ) {
        Text("응급 호출")
    }

    resultMessage?.let { message ->
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            fontSize = 13.sp
        )
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isCalling) {
                    showDialog = false
                }
            },
            title = {
                Text("응급 호출")
            },
            text = {
                Text("호출할 대상을 선택하세요.")
            },
            confirmButton = {
                TextButton(
                    onClick = { requestEmergencyCall("rescue") },
                    enabled = !isCalling
                ) {
                    Text("구조대")
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = { requestEmergencyCall("police") },
                        enabled = !isCalling
                    ) {
                        Text("경찰")
                    }

                    TextButton(
                        onClick = { showDialog = false },
                        enabled = !isCalling
                    ) {
                        Text("취소")
                    }
                }
            }
        )
    }
}