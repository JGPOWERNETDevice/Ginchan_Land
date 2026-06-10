package net.jgpower.gichan_land.ui.event_create

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.jgpower.gichan_land.data.area.AreaTypeItem
import net.jgpower.gichan_land.data.event.EventTypeItem
import net.jgpower.gichan_land.network.ApiServiceManager
import net.jgpower.gichan_land.repository.EventRepository

@Composable
fun EventCreateScreen(
    workerId: String,
    onBackClick: () -> Unit
) {
    BackHandler {
        onBackClick()
    }

    val eventTypes = remember { mutableStateListOf<EventTypeItem>() }
    val selectedEventType = remember { mutableStateOf<EventTypeItem?>(null) }

    val areaTypes = remember { mutableStateListOf<AreaTypeItem>() }
    val selectedArea = remember { mutableStateOf<AreaTypeItem?>(null) }

    val selectedTargetType = remember { mutableStateOf<String?>("어린이") }

    val resultMessage = remember { mutableStateOf<String?>(null) }
    val errorMessage = remember { mutableStateOf<String?>(null) }
    val isLoading = remember { mutableStateOf(false) }
    val isInitialLoading = remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    val eventRepository = remember {
        EventRepository(ApiServiceManager.apiService)
    }

    LaunchedEffect(Unit) {
        isInitialLoading.value = true
        errorMessage.value = null

        try {
            val loadedEventTypes = eventRepository.getEventTypes()
                .filter { it.isActive }

            eventTypes.clear()
            eventTypes.addAll(loadedEventTypes)
            selectedEventType.value = loadedEventTypes.firstOrNull()

            val loadedAreaTypes = eventRepository.getAreaTypes()
                .filter { it.isActive }

            areaTypes.clear()
            areaTypes.addAll(loadedAreaTypes)
            selectedArea.value = loadedAreaTypes.firstOrNull()

            if (loadedEventTypes.isEmpty()) {
                errorMessage.value = "사용 가능한 이벤트 종류가 없습니다."
            } else if (loadedAreaTypes.isEmpty()) {
                errorMessage.value = "사용 가능한 구역이 없습니다."
            }
        } catch (_: Exception) {
            errorMessage.value = "이벤트 종류 또는 구역 목록을 불러오지 못했습니다."
        } finally {
            isInitialLoading.value = false
        }
    }

    fun selectedEventTypeName(): String {
        return selectedEventType.value?.eventTypeName ?: "선택 안 됨"
    }

    fun selectedAreaName(): String {
        return selectedArea.value?.areaName ?: "선택 안 됨"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "상황 발견 보고",
            style = MaterialTheme.typography.headlineSmall
        )

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "무엇이 일어났나요?",
                    style = MaterialTheme.typography.titleMedium
                )

                if (isInitialLoading.value) {
                    Text("이벤트 종류를 불러오는 중...")
                } else if (eventTypes.isEmpty()) {
                    Text("사용 가능한 이벤트 종류가 없습니다.")
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        eventTypes.chunked(2).forEach { rowItems ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowItems.forEach { item ->
                                    FilterChip(
                                        selected = selectedEventType.value?.eventTypeCode == item.eventTypeCode,
                                        onClick = {
                                            selectedEventType.value = item
                                        },
                                        label = {
                                            Text(item.eventTypeName)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "어디서 발생했나요?",
                    style = MaterialTheme.typography.titleMedium
                )

                if (isInitialLoading.value) {
                    Text("구역 목록을 불러오는 중...")
                } else if (areaTypes.isEmpty()) {
                    Text("사용 가능한 구역이 없습니다.")
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        areaTypes.chunked(3).forEach { rowItems ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowItems.forEach { item ->
                                    FilterChip(
                                        selected = selectedArea.value?.areaCode == item.areaCode,
                                        onClick = {
                                            selectedArea.value = item
                                        },
                                        label = {
                                            Text(item.areaName)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "대상은 누구인가요?",
                    style = MaterialTheme.typography.titleMedium
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedTargetType.value == "어린이",
                        onClick = { selectedTargetType.value = "어린이" },
                        label = { Text("어린이") }
                    )

                    FilterChip(
                        selected = selectedTargetType.value == "성인",
                        onClick = { selectedTargetType.value = "성인" },
                        label = { Text("성인") }
                    )

                    FilterChip(
                        selected = selectedTargetType.value == null,
                        onClick = { selectedTargetType.value = null },
                        label = { Text("미지정") }
                    )
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("보고자: $workerId")
                Text("이벤트: ${selectedEventTypeName()}")
                Text("구역: ${selectedAreaName()}")
                Text("대상: ${selectedTargetType.value ?: "미지정"}")
                Text("상태: 조치 전")
            }
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

        Button(
            onClick = {
                val eventType = selectedEventType.value
                val area = selectedArea.value

                if (eventType == null) {
                    errorMessage.value = "이벤트 종류를 선택하세요."
                    return@Button
                }

                if (area == null) {
                    errorMessage.value = "구역을 선택하세요."
                    return@Button
                }

                coroutineScope.launch {
                    isLoading.value = true
                    errorMessage.value = null
                    resultMessage.value = null

                    try {
                        val response = eventRepository.createEvent(
                            eventType = eventType.eventTypeCode,
                            areaCode = area.areaCode,
                            targetType = selectedTargetType.value,
                            workerId = workerId
                        )

                        if (response.isSuccessful) {
                            resultMessage.value = "상황 보고가 저장되었습니다."
                        } else {
                            errorMessage.value = "상황 보고 실패: ${response.code()}"
                        }
                    } catch (_: Exception) {
                        errorMessage.value = "서버에 연결할 수 없습니다."
                    } finally {
                        isLoading.value = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading.value &&
                    !isInitialLoading.value &&
                    selectedEventType.value != null &&
                    selectedArea.value != null
        ) {
            Text(if (isLoading.value) "보고 중..." else "보고하기")
        }

        OutlinedButton(
            onClick = onBackClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("뒤로가기")
        }
    }
}