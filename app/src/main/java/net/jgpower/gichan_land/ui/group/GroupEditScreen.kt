package net.jgpower.gichan_land.ui.group

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.launch
import net.jgpower.gichan_land.data.group.AppGroupItem
import net.jgpower.gichan_land.network.ApiServiceManager
import net.jgpower.gichan_land.repository.GroupRepository
import org.json.JSONObject

@Composable
fun GroupEditScreen(
    workerId: String,
    onBackClick: () -> Unit,
    onGroupsChanged: () -> Unit = {}
) {
    BackHandler { onBackClick() }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val groups = remember { mutableStateListOf<AppGroupItem>() }
    val isLoading = remember { mutableStateOf(false) }
    val isSaving = remember { mutableStateOf(false) }
    val statusMessage = remember { mutableStateOf<String?>(null) }
    val repository = remember { GroupRepository(ApiServiceManager.apiService) }

    fun loadGroups() {
        coroutineScope.launch {
            isLoading.value = true
            statusMessage.value = null

            try {
                val response = repository.getMyGroups(workerId)
                if (response.success) {
                    groups.clear()
                    groups.addAll(response.data?.groups.orEmpty())
                } else {
                    statusMessage.value = response.message ?: "그룹 목록 조회 실패"
                }
            } catch (_: Exception) {
                statusMessage.value = "서버 요청 중 오류가 발생했습니다."
            } finally {
                isLoading.value = false
            }
        }
    }

    fun addGroupByCode(rawCode: String) {
        val groupCode = extractGroupCodeFromQr(rawCode)
        if (groupCode.isBlank()) {
            statusMessage.value = "QR에서 그룹 코드를 읽을 수 없습니다."
            return
        }

        if (groups.any { it.groupCode == groupCode }) {
            statusMessage.value = "이미 포함된 그룹입니다: $groupCode"
            return
        }

        coroutineScope.launch {
            isSaving.value = true
            statusMessage.value = null

            try {
                val response = repository.addGroup(workerId, groupCode)
                if (response.success) {
                    groups.clear()
                    groups.addAll(response.data?.groups.orEmpty())
                    statusMessage.value = "그룹이 추가되었습니다: $groupCode"
                    onGroupsChanged()
                } else {
                    val invalid = response.data?.invalidGroupCodes.orEmpty()
                    statusMessage.value = if (invalid.isNotEmpty()) {
                        "등록되지 않았거나 비활성 그룹입니다: ${invalid.joinToString(", ")}"
                    } else {
                        response.message ?: "그룹 추가 실패"
                    }
                }
            } catch (_: Exception) {
                statusMessage.value = "서버 요청 중 오류가 발생했습니다."
            } finally {
                isSaving.value = false
            }
        }
    }

    fun removeGroup(groupCode: String) {
        coroutineScope.launch {
            isSaving.value = true
            statusMessage.value = null

            try {
                val response = repository.removeGroup(workerId, groupCode)
                if (response.success) {
                    groups.clear()
                    groups.addAll(response.data?.groups.orEmpty())
                    statusMessage.value = "그룹이 삭제되었습니다: $groupCode"
                    onGroupsChanged()
                } else {
                    statusMessage.value = response.message ?: "그룹 삭제 실패"
                }
            } catch (_: Exception) {
                statusMessage.value = "서버 요청 중 오류가 발생했습니다."
            } finally {
                isSaving.value = false
            }
        }
    }

    val qrScannerLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val contents = result.contents
        if (contents.isNullOrBlank()) {
            statusMessage.value = "QR 스캔이 취소되었습니다."
        } else {
            addGroupByCode(contents)
        }
    }

    fun launchQrScanner() {
        val options = ScanOptions().apply {
            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            setPrompt("그룹 QR 코드를 스캔하세요")
            setBeepEnabled(false)
            setOrientationLocked(false)
        }
        qrScannerLauncher.launch(options)
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchQrScanner()
        } else {
            statusMessage.value = "QR 스캔에는 카메라 권한이 필요합니다."
        }
    }

    fun requestScan() {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            launchQrScanner()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(workerId) {
        loadGroups()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "그룹 수정",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "직원 ID: $workerId",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            OutlinedButton(onClick = onBackClick) {
                Text("뒤로")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "QR로 기존 그룹 코드를 인식해 내 소속 그룹만 변경합니다. 앱에서는 새 그룹을 생성하지 않습니다.",
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { requestScan() },
            enabled = !isSaving.value,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("+ QR 스캔으로 그룹 추가")
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = { loadGroups() },
            enabled = !isLoading.value && !isSaving.value,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("새로고침")
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "포함중인 그룹 리스트",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(8.dp))

        statusMessage.value?.let { message ->
            Text(
                text = message,
                color = if (message.contains("실패") || message.contains("없") || message.contains("필요") || message.contains("취소")) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (isLoading.value) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (groups.isEmpty()) {
            Text("현재 포함된 그룹이 없습니다.")
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(groups, key = { it.groupCode }) { group ->
                GroupListItem(
                    group = group,
                    enabled = !isSaving.value,
                    onRemoveClick = {
                        removeGroup(group.groupCode)
                    }
                )
            }
        }
    }
}

@Composable
private fun GroupListItem(
    group: AppGroupItem,
    enabled: Boolean,
    onRemoveClick: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = group.groupName.ifBlank { group.groupCode },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "그룹 코드: ${group.groupCode}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            OutlinedButton(
                onClick = onRemoveClick,
                enabled = enabled
            ) {
                Text("삭제")
            }
        }
    }
}

private fun extractGroupCodeFromQr(raw: String): String {
    val text = raw.trim()
    if (text.isBlank()) return ""

    runCatching {
        val json = JSONObject(text)
        val candidates = listOf(
            json.optString("groupCode"),
            json.optString("group_code"),
            json.optString("areaGroup"),
            json.optString("area_group"),
            json.optString("code")
        )
        candidates.firstOrNull { it.isNotBlank() }?.let { return sanitizeGroupCode(it) }
    }

    val queryCandidate = Regex("(?:groupCode|group_code|areaGroup|area_group|code)=([^&?#]+)", RegexOption.IGNORE_CASE)
        .find(text)
        ?.groupValues
        ?.getOrNull(1)
        ?.let { java.net.URLDecoder.decode(it, "UTF-8") }
    if (!queryCandidate.isNullOrBlank()) return sanitizeGroupCode(queryCandidate)

    val prefixed = Regex("^(?:GROUP|GROUP_CODE|AREA_GROUP|CODE)[:=](.+)$", RegexOption.IGNORE_CASE)
        .find(text)
        ?.groupValues
        ?.getOrNull(1)
    if (!prefixed.isNullOrBlank()) return sanitizeGroupCode(prefixed)

    return sanitizeGroupCode(text)
}

private fun sanitizeGroupCode(value: String): String {
    return value
        .trim()
        .removePrefix("\"")
        .removeSuffix("\"")
        .trim()
}
