package net.jgpower.gichan_land.ui.notice

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.jgpower.gichan_land.data.textalert.TextAlert
import net.jgpower.gichan_land.data.textalert.TextAlertState
import net.jgpower.gichan_land.network.ApiServiceManager
import net.jgpower.gichan_land.repository.TextAlertRepository

@Composable
fun NoticeScreen(
    workerId: String,
    onBackClick: () -> Unit
) {
    BackHandler {
        onBackClick()
    }

    val coroutineScope = rememberCoroutineScope()

    val repository = remember {
        TextAlertRepository(ApiServiceManager.apiService)
    }

    val notices by TextAlertState.alerts.collectAsState()

    val isLoading = remember { mutableStateOf(false) }
    val errorMessage = remember { mutableStateOf<String?>(null) }

    fun loadTextAlerts() {
        coroutineScope.launch {
            isLoading.value = true
            errorMessage.value = null

            try {
                val response = repository.getMyTextAlerts(workerId)

                if (response.success) {
                    TextAlertState.setAlerts(response.data)
                } else {
                    errorMessage.value = response.message
                }
            } catch (_: Exception) {
                errorMessage.value = "공지사항을 불러오지 못했습니다."
            } finally {
                isLoading.value = false
            }
        }
    }

    LaunchedEffect(workerId) {
        loadTextAlerts()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "공지사항",
            style = MaterialTheme.typography.headlineSmall
        )

        OutlinedButton(
            onClick = {
                loadTextAlerts()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("새로고침")
        }

        if (isLoading.value && notices.isEmpty()) {
            Text("공지사항을 불러오는 중...")
        }

        errorMessage.value?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error
            )
        }

        if (!isLoading.value && notices.isEmpty() && errorMessage.value == null) {
            Text("수신한 공지사항이 없습니다.")
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(notices) { item ->
                NoticeListItem(item)
            }
        }

        OutlinedButton(
            onClick = onBackClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("뒤로가기")
        }
    }
}

@Composable
private fun NoticeListItem(
    item: TextAlert
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = item.message,
                style = MaterialTheme.typography.titleMedium
            )

            Text(
                text = "전파 방식: ${item.receiveType}",
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = "수신자: ${item.receiverId}",
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = "시간: ${item.createdAt}",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}