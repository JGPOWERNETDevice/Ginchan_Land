package net.jgpower.gichan_land.ui.walkietalkie

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import net.jgpower.gichan_land.data.walkie.OnlineWorkerDto
import net.jgpower.gichan_land.data.walkie.WalkieTarget
import net.jgpower.gichan_land.data.walkie.WalkieTargetType
import net.jgpower.gichan_land.network.ApiServiceManager
import net.jgpower.gichan_land.network.WalkieTalkieManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalkieTalkieScreen(
    workerId: String,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current

    val onlineWorkers = remember { mutableStateListOf<OnlineWorkerDto>() }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var myAreaGroup by remember { mutableStateOf<String?>(null) }
    var selectedTarget by remember { mutableStateOf<WalkieTarget?>(null) }
    var isMicOn by remember { mutableStateOf(false) }

    fun exitScreen() {
        WalkieTalkieManager.stopTransmit()
        isMicOn = false
        onBackClick()
    }

    BackHandler {
        exitScreen()
    }

    val micPermissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                val started = WalkieTalkieManager.startTransmit(context)
                isMicOn = started

                if (!started) {
                    errorMessage = "무전 송신을 시작할 수 없습니다."
                }
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

            val me = list.firstOrNull { it.workerId == workerId }
            myAreaGroup = me?.areaGroup

            if (selectedTarget == null && !myAreaGroup.isNullOrBlank()) {
                selectedTarget = WalkieTarget(
                    targetType = WalkieTargetType.GROUP,
                    targetAreaGroup = myAreaGroup
                )
                WalkieTalkieManager.setTarget(selectedTarget)
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

    LaunchedEffect(workerId, myAreaGroup) {
        val group = myAreaGroup

        if (!group.isNullOrBlank()) {
            WalkieTalkieManager.start(
                context = context,
                workerId = workerId,
                areaGroup = group
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            WalkieTalkieManager.stopTransmit()
        }
    }

    val groupWorkers = onlineWorkers.filter {
        it.workerId != workerId &&
                !it.areaGroup.isNullOrBlank() &&
                it.areaGroup == myAreaGroup
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("무전기")
                },
                navigationIcon = {
                    OutlinedButton(
                        onClick = {
                            exitScreen()
                        },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "내 그룹: ${myAreaGroup ?: "-"}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            if (isLoading && onlineWorkers.isEmpty()) {
                CircularProgressIndicator()
            }

            errorMessage?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error
                )
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
                        selected = selectedTarget?.targetType == WalkieTargetType.GROUP,
                        onClick = {
                            selectedTarget = WalkieTarget(
                                targetType = WalkieTargetType.GROUP,
                                targetAreaGroup = myAreaGroup
                            )
                            WalkieTalkieManager.setTarget(selectedTarget)
                        },
                        label = {
                            Text("그룹 전체")
                        },
                        enabled = !myAreaGroup.isNullOrBlank()
                    )

                    if (groupWorkers.isEmpty()) {
                        Text("현재 같은 그룹에 접속 중인 직원이 없습니다.")
                    } else {
                        groupWorkers.forEach { worker ->
                            FilterChip(
                                selected =
                                    selectedTarget?.targetType == WalkieTargetType.USER &&
                                            selectedTarget?.targetWorkerId == worker.workerId,
                                onClick = {
                                    selectedTarget = WalkieTarget(
                                        targetType = WalkieTargetType.USER,
                                        targetWorkerId = worker.workerId,
                                        targetWorkerName = worker.name,
                                        targetAreaGroup = worker.areaGroup
                                    )
                                    WalkieTalkieManager.setTarget(selectedTarget)
                                },
                                label = {
                                    Text("${worker.name} (${worker.workerId})")
                                }
                            )
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
                        if (isMicOn) {
                            WalkieTalkieManager.stopTransmit()
                            isMicOn = false
                        } else {
                            if (selectedTarget == null) {
                                errorMessage = "송신 대상을 선택하세요."
                                return@Button
                            }

                            val granted =
                                ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED

                            if (granted) {
                                val started =
                                    WalkieTalkieManager.startTransmit(context)

                                isMicOn = started

                                if (!started) {
                                    errorMessage = "무전 송신을 시작할 수 없습니다."
                                }
                            } else {
                                micPermissionLauncher.launch(
                                    Manifest.permission.RECORD_AUDIO
                                )
                            }
                        }
                    },
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
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "MIC",
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isMicOn) "OFF" else "ON"
                        )
                    }
                }
            }

            Text(
                text = if (isMicOn) {
                    "송신 중입니다. 말을 마치면 MIC 버튼을 다시 눌러 종료하세요."
                } else {
                    "대상을 선택한 후 MIC 버튼을 눌러 송신을 시작하세요."
                },
                modifier = Modifier.fillMaxWidth()
            )


        }
    }
}