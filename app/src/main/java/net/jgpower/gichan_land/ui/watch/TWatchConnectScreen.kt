package net.jgpower.gichan_land.ui.watch

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.jgpower.gichan_land.watch.TWatchBleNotifier

@Composable
fun TWatchConnectScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val state by TWatchBleNotifier.uiState.collectAsState()

    BackHandler {
        TWatchBleNotifier.stopScanForSelection()
        onBackClick()
    }

    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    } else {
        arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // Even when a permission is denied, startScanForSelection() will display the exact reason.
        TWatchBleNotifier.startScanForSelection(context.applicationContext)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "워치 BLE 연결",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            OutlinedButton(onClick = onBackClick) {
                Text("뒤로")
            }
        }

        Text(
            text = state.lastStatus,
            style = MaterialTheme.typography.bodyMedium
        )

        Text(
            text = "Redmi/MIUI Android 10/11은 BLE 스캔에 위치 권한과 휴대폰 위치 서비스 ON이 필요합니다. 스캔이 비어 있으면 앱 정보 > 권한 > 위치 허용, 배터리 절약 > 제한 없음도 확인하세요.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (state.connectedAddress != null) {
            Text(
                text = "연결 기기: ${state.connectedName ?: "이름 없음"} / ${state.connectedAddress}",
                style = MaterialTheme.typography.bodySmall
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    permissionLauncher.launch(permissions)
                },
                modifier = Modifier.weight(1f)
            ) {
                Text(if (state.isScanning) "재스캔" else "스캔 시작")
            }

            OutlinedButton(
                onClick = { TWatchBleNotifier.stopScanForSelection() },
                modifier = Modifier.weight(1f)
            ) {
                Text("스캔 중지")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { TWatchBleNotifier.sendTestAlert() },
                enabled = state.isConnected,
                modifier = Modifier.weight(1f)
            ) {
                Text("테스트 알림")
            }

            OutlinedButton(
                onClick = { TWatchBleNotifier.clearSavedDevice(context.applicationContext) },
                modifier = Modifier.weight(1f)
            ) {
                Text("연결 초기화")
            }
        }

        HorizontalDivider()

        Text(
            text = "발견된 BLE 기기",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        if (state.devices.isEmpty()) {
            Text(
                text = if (state.isScanning) "주변 BLE 기기를 찾는 중입니다." else "아직 발견된 기기가 없습니다. 워치를 켠 뒤 스캔 시작을 누르세요.",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(state.devices, key = { it.address }) { device ->
                WatchDeviceCard(
                    device = device,
                    isConnected = state.connectedAddress == device.address && state.isConnected,
                    onClick = {
                        TWatchBleNotifier.connectToScannedDevice(
                            context = context.applicationContext,
                            address = device.address,
                            name = device.name
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun WatchDeviceCard(
    device: TWatchBleNotifier.ScannedDevice,
    isConnected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (device.isTargetWatch) FontWeight.Bold else FontWeight.Normal
                )

                Text(
                    text = when {
                        isConnected -> "연결됨"
                        device.isTargetWatch -> "워치"
                        else -> "BLE"
                    },
                    color = if (device.isTargetWatch || isConnected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    style = MaterialTheme.typography.labelMedium
                )
            }

            Text(
                text = device.address,
                style = MaterialTheme.typography.bodySmall
            )

            Text(
                text = "RSSI: ${device.rssi}",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
