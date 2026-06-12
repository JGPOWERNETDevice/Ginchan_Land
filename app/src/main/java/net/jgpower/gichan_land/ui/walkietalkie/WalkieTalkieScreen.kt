package net.jgpower.gichan_land.ui.walkietalkie

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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

private const val MONITOR_WORKER_ID = "monitor"

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

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var myAreaGroup by remember { mutableStateOf<String?>(null) }
    var isGroupSelected by remember { mutableStateOf(true) }

    val selectedWorkerIds = remember {
        mutableStateMapOf<String, OnlineWorkerDto>()
    }

    var selectedTarget by remember { mutableStateOf<WalkieTarget?>(null) }
    var isMicOn by remember { mutableStateOf(false) }

    fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasBluetoothConnectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun requiredWalkiePermissions(): Array<String> {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }

        return permissions.toTypedArray()
    }

    fun workerDisplayName(worker: OnlineWorkerDto): String {
        val id = worker.workerId?.trim()

        if (id == MONITOR_WORKER_ID) {
            return "중앙관제"
        }

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

    fun updateWalkieTarget() {
        selectedTarget =
            if (isGroupSelected) {
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
                        targetWorkerName = selectedWorkerIds.values
                            .joinToString(",") { workerDisplayName(it) },
                        targetAreaGroup = myAreaGroup
                    )
                }
            }

        WalkieTalkieManager.setTarget(selectedTarget)
    }

    fun selectedTargetText(): String {
        return if (isGroupSelected) {
            "현재 송신 대상: 그룹 전체"
        } else {
            val names = selectedWorkerIds.values
                .joinToString(", ") { workerDisplayName(it) }

            "현재 송신 대상: ${names.ifBlank { "선택 없음" }}"
        }
    }

    fun selectGroupAll() {
        isGroupSelected = true
        selectedWorkerIds.clear()
        updateWalkieTarget()
    }

    fun toggleWorker(worker: OnlineWorkerDto) {
        val targetWorkerId = worker.workerId?.trim() ?: return

        if (targetWorkerId.isBlank()) return
        if (targetWorkerId == currentWorkerId) return

        isGroupSelected = false

        if (selectedWorkerIds.containsKey(targetWorkerId)) {
            selectedWorkerIds.remove(targetWorkerId)
        } else {
            selectedWorkerIds[targetWorkerId] = worker
        }

        updateWalkieTarget()
    }

    fun exitScreen() {
        WalkieTalkieManager.stopTransmit()
        isMicOn = false
        onBackClick()
    }

    fun startMicIfPossible() {
        if (selectedTarget == null) {
            errorMessage = "송신 대상을 선택하세요."
            return
        }

        if (!hasRecordAudioPermission() || !hasBluetoothConnectPermission()) {
            errorMessage = null
            return
        }

        val started = WalkieTalkieManager.startTransmit(context)
        isMicOn = started

        if (!started) {
            errorMessage = "무전 송신을 시작할 수 없습니다."
        }
    }

    BackHandler {
        exitScreen()
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { result ->
            val micGranted =
                result[Manifest.permission.RECORD_AUDIO] == true ||
                        hasRecordAudioPermission()

            val bluetoothGranted =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    result[Manifest.permission.BLUETOOTH_CONNECT] == true ||
                            hasBluetoothConnectPermission()
                } else {
                    true
                }

            if (micGranted && bluetoothGranted) {
                startMicIfPossible()
            } else {
                errorMessage =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        "마이크 및 블루투스 권한이 필요합니다."
                    } else {
                        "마이크 권한이 필요합니다."
                    }
            }
        }

    suspend fun loadOnlineWorkers() {
        try {
            isLoading = true
            errorMessage = null

            val list = ApiServiceManager.apiService.getOnlineWorkers()

            onlineWorkers.clear()
            onlineWorkers.addAll(list)

            val me = list.firstOrNull {
                it.workerId?.trim() == currentWorkerId
            }

            myAreaGroup = me?.areaGroup?.trim()

            if (selectedTarget == null && !myAreaGroup.isNullOrBlank()) {
                isGroupSelected = true
                selectedWorkerIds.clear()
                updateWalkieTarget()
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

    DisposableEffect(Unit) {
        onDispose {
            WalkieTalkieManager.stopTransmit()
        }
    }

    val groupWorkers = onlineWorkers.filter { worker ->
        val targetWorkerId = worker.workerId?.trim()
        val workerAreaGroup = worker.areaGroup?.trim()
        val myGroup = myAreaGroup?.trim()

        val isMonitor = targetWorkerId == MONITOR_WORKER_ID

        isMonitor ||
                (
                        !targetWorkerId.isNullOrBlank() &&
                                targetWorkerId != currentWorkerId &&
                                !myGroup.isNullOrBlank() &&
                                workerAreaGroup == myGroup
                        )
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
                .verticalScroll(scrollState)
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
                        selected = isGroupSelected,
                        onClick = {
                            selectGroupAll()
                        },
                        label = {
                            Text("그룹 전체")
                        },
                        enabled = !myAreaGroup.isNullOrBlank()
                    )

                    Text(
                        text = "개별 직원 선택",
                        fontWeight = FontWeight.Bold
                    )

                    if (groupWorkers.isEmpty()) {
                        Text(
                            text = "현재 같은 그룹에 접속 중인 직원이 없습니다.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        groupWorkers.forEach { worker ->
                            val targetWorkerId = worker.workerId?.trim() ?: return@forEach
                            val checked = selectedWorkerIds.containsKey(targetWorkerId)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = {
                                        toggleWorker(worker)
                                    }
                                )

                                Text(
                                    text = "${workerDisplayName(worker)} (${workerDisplayId(worker)})"
                                )
                            }
                        }
                    }

                    Text(
                        text = selectedTargetText(),
                        style = MaterialTheme.typography.bodySmall
                    )
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

                            if (hasRecordAudioPermission() && hasBluetoothConnectPermission()) {
                                startMicIfPossible()
                            } else {
                                permissionLauncher.launch(requiredWalkiePermissions())
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
