package net.jgpower.gichan_land.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import net.jgpower.gichan_land.data.alert.AppAlertPopupItem
import net.jgpower.gichan_land.data.alert.AppAlertPopupState

@Composable
fun AppAlertPopupHost() {
    val popupQueue by AppAlertPopupState.popupQueue.collectAsState()
    val current = popupQueue.firstOrNull()

    if (current != null) {
        when (current) {
            is AppAlertPopupItem.Safety -> {
                AlertDialog(
                    onDismissRequest = {
                        AppAlertPopupState.dismissCurrentPopup()
                    },
                    title = {
                        Text("위험 알림 발생")
                    },
                    text = {
                        Column(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("이벤트 ID: ${current.alert.eventId}")
                            Text("전파 방식: ${current.alert.receiveType}")
                            Text("대상: ${current.alert.targetType ?: "미지정"}")
                            Text("내용: ${current.alert.message}")
                            Text("시간: ${current.alert.occurredAt}")
                            Text("상태: ${current.alert.status}")
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                AppAlertPopupState.dismissCurrentPopup()
                            }
                        ) {
                            Text("확인")
                        }
                    }
                )
            }

            is AppAlertPopupItem.Text -> {
                AlertDialog(
                    onDismissRequest = {
                        AppAlertPopupState.dismissCurrentPopup()
                    },
                    title = {
                        Text("중앙 관제 알림")
                    },
                    text = {
                        Column(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("내용: ${current.alert.message}")
                            Text("전파 방식: ${current.alert.receiveType}")
                            Text("시간: ${current.alert.createdAt}")
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                AppAlertPopupState.dismissCurrentPopup()
                            }
                        ) {
                            Text("확인")
                        }
                    }
                )
            }
        }
    }
}