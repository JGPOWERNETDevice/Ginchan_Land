package net.jgpower.gichan_land.ui.walkietalkie

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import net.jgpower.gichan_land.data.walkie.WalkieGlobalState
import net.jgpower.gichan_land.network.WalkieSignalingClient

@Composable
fun WalkieIncomingCallPopupHost(
    workerId: String,
    enabled: Boolean,
    onOpenWalkie: () -> Unit
) {
    if (!enabled || workerId.isBlank()) return

    if (WalkieGlobalState.showPeerEndedPopup.value) {
        AlertDialog(
            onDismissRequest = { WalkieGlobalState.showPeerEndedPopup.value = false },
            title = { Text("통화 종료") },
            text = { Text("상대방이 통화를 종료했습니다.") },
            confirmButton = {
                Button(onClick = { WalkieGlobalState.showPeerEndedPopup.value = false }) {
                    Text("확인")
                }
            }
        )
        return
    }

    val calls = WalkieGlobalState.pendingIncomingCalls
    if (calls.isEmpty()) return

    val first = calls.first()
    val title = if (calls.size == 1) {
        "무전 연결 요청"
    } else {
        "무전 연결 요청 ${calls.size}건"
    }

    AlertDialog(
        onDismissRequest = {},
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                calls.take(3).forEach { call ->
                    Text("${call.fromName ?: call.fromWorkerId} / ${call.fromAreaGroup ?: "-"}")
                }
                if (calls.size > 3) {
                    Text("외 ${calls.size - 3}건")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    WalkieSignalingClient.acceptCall(
                        callId = first.callId,
                        workerId = workerId
                    )
                    onOpenWalkie()
                }
            ) {
                Text("수신")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        WalkieSignalingClient.rejectCall(
                            callId = first.callId,
                            workerId = workerId
                        )
                        WalkieGlobalState.removeIncomingCall(first.callId)
                    }
                ) {
                    Text("거절")
                }

                OutlinedButton(onClick = onOpenWalkie) {
                    Text("목록 보기")
                }
            }
        }
    )
}
