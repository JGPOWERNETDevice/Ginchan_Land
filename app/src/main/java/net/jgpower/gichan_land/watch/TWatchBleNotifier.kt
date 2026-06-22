package net.jgpower.gichan_land.watch

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Android app -> LilyGO T-Watch-S3 BLE notifier.
 *
 * Watch firmware acts as a BLE GATT server.
 * The app scans BLE devices, lets the user select JG_TWATCH, connects as BLE central,
 * and writes compact JSON to ALERT_CHAR_UUID.
 */
object TWatchBleNotifier {
    private const val TAG = "TWatchBleNotifier"
    private const val PREFS_NAME = "twatch_ble"
    private const val PREF_ADDRESS = "selected_address"
    private const val PREF_NAME = "selected_name"

    // Same SharedPreferences used by data/datastore/LoginDataStore.kt
    private const val LOGIN_PREF_NAME = "login_data"
    private const val LOGIN_KEY_WORKER_ID = "worker_id"

    private const val WATCH_NAME_SHORT = "JG_TWATCH"
    private const val WATCH_NAME_LONG = "JGPOWER_TWATCH"

    val ALERT_SERVICE_UUID: UUID = UUID.fromString("7b2f7b90-7d55-4d93-8d17-2fd4ef3fbb10")
    val ALERT_CHAR_UUID: UUID = UUID.fromString("a3f22c34-3b66-4cb2-b028-9b236f407730")

    data class ScannedDevice(
        val name: String,
        val address: String,
        val rssi: Int,
        val isTargetWatch: Boolean
    )

    data class UiState(
        val isScanning: Boolean = false,
        val isConnected: Boolean = false,
        val connectedName: String? = null,
        val connectedAddress: String? = null,
        val devices: List<ScannedDevice> = emptyList(),
        val lastStatus: String = "워치 미연결"
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState

    private val mainHandler = Handler(Looper.getMainLooper())
    private val pendingPayloads = ConcurrentLinkedQueue<String>()
    private val scannedDevices = linkedMapOf<String, ScannedDevice>()

    private var appContext: Context? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var gatt: BluetoothGatt? = null
    private var alertCharacteristic: BluetoothGattCharacteristic? = null
    private var isScanning = false
    private var isConnected = false
    private var isWriting = false

    fun start(context: Context) {
        init(context)

        if (!hasBlePermission(context)) {
            updateStatus("BLE 권한이 필요합니다.")
            return
        }

        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            updateStatus("Bluetooth가 꺼져 있습니다.")
            return
        }

        if (isConnected || isScanning) return

        val savedAddress = getSavedAddress(context)
        if (!savedAddress.isNullOrBlank()) {
            connectByAddress(context, savedAddress, getSavedName(context) ?: WATCH_NAME_SHORT)
        }
    }

    fun stop() {
        val context = appContext ?: return
        if (!hasBlePermission(context)) return

        try {
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
            gatt?.disconnect()
            gatt?.close()
        } catch (_: Exception) {
        }

        isScanning = false
        isConnected = false
        isWriting = false
        alertCharacteristic = null
        gatt = null
        _uiState.update {
            it.copy(
                isScanning = false,
                isConnected = false,
                connectedName = null,
                connectedAddress = null,
                lastStatus = "워치 연결 해제됨"
            )
        }
    }

    fun clearSavedDevice(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
        stop()
        updateStatus("저장된 워치 연결 정보를 삭제했습니다.")
    }

    fun startScanForSelection(context: Context) {
        init(context)

        if (!hasBlePermission(context)) {
            updateStatus("BLE 권한이 필요합니다.")
            return
        }

        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            updateStatus("Bluetooth가 꺼져 있습니다.")
            return
        }

        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            updateStatus("BLE 스캐너를 사용할 수 없습니다.")
            return
        }

        if (!isLocationServiceEnabled(context)) {
            updateStatus("휴대폰 위치 서비스가 꺼져 있습니다. BLE 목록이 비어 있으면 위치 서비스를 켜세요.")
        }

        try {
            scanner.stopScan(scanCallback)
        } catch (_: Exception) {
        }

        scannedDevices.clear()
        isScanning = true
        _uiState.update {
            it.copy(
                isScanning = true,
                devices = emptyList(),
                lastStatus = "BLE 기기 스캔 중..."
            )
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0L)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
            .setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
            .build()

        // Use an unfiltered scan. Some phones do not match scan-response UUIDs in ScanFilter,
        // while nRF Connect still shows the device. We list every BLE advertisement and then
        // mark JG_TWATCH / service-UUID matches inside handleScanResult().
        scanner.startScan(null, settings, scanCallback)

        mainHandler.postDelayed({
            stopScanOnly()
        }, 15000L)
    }

    fun stopScanForSelection() {
        stopScanOnly()
    }

    fun connectToScannedDevice(context: Context, address: String, name: String) {
        init(context)
        saveSelectedDevice(context, address, name)
        connectByAddress(context, address, name)
    }

    fun sendTestAlert() {
        sendAlert(
            id = "manual-test",
            type = "text_alert",
            title = "중앙 관제 알림",
            message = "테스트 알림",
            time = "TEST"
        )
    }

    fun notifySafetyAlert(alertId: String, message: String, occurredAt: String?) {
        sendAlert(
            id = alertId,
            type = "safety_alert",
            title = "위험 알림 발생",
            message = message,
            time = occurredAt
        )
    }

    fun notifyTextAlert(textAlertId: String, message: String, createdAt: String?) {
        sendAlert(
            id = textAlertId,
            type = "text_alert",
            title = "중앙 관제 알림",
            message = message,
            time = createdAt
        )
    }

    fun notifyWalkieCall(callId: String, fromWorkerId: String, fromName: String?, fromAreaGroup: String?) {
        val sender = fromName?.takeIf { it.isNotBlank() } ?: fromWorkerId
        val body = if (!fromAreaGroup.isNullOrBlank()) "$sender / $fromAreaGroup" else sender
        sendAlert(
            id = callId,
            type = "walkie_call",
            title = "전화 연결 요청",
            message = body,
            time = null
        )
    }

    fun notifyEmergencyBroadcast(broadcastId: String, targetType: String?, targetAreaGroup: String?) {
        val targetText = when (targetType) {
            "GROUP" -> "대상 그룹: ${targetAreaGroup ?: "-"}"
            "ALL" -> "대상: 전체"
            else -> "대상: 개별"
        }
        sendAlert(
            id = broadcastId,
            type = "emergency_broadcast",
            title = "중앙관제 긴급 음성 전파",
            message = targetText,
            time = null
        )
    }

    private fun init(context: Context) {
        appContext = context.applicationContext
        if (bluetoothAdapter == null) {
            val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            bluetoothAdapter = manager.adapter
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopScanOnly() {
        val context = appContext ?: return
        if (!hasBlePermission(context)) return

        if (!isScanning) return
        try {
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (_: Exception) {
        }
        isScanning = false
        _uiState.update {
            it.copy(
                isScanning = false,
                lastStatus = if (it.devices.isEmpty()) "스캔 완료: 발견된 기기가 없습니다." else "스캔 완료"
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectByAddress(context: Context, address: String, name: String) {
        if (!hasBlePermission(context)) {
            updateStatus("BLE 권한이 필요합니다.")
            return
        }

        val adapter = bluetoothAdapter ?: return
        val device = try {
            adapter.getRemoteDevice(address)
        } catch (e: Exception) {
            updateStatus("기기 주소가 올바르지 않습니다: $address")
            return
        }

        stopScanOnly()
        try {
            gatt?.disconnect()
            gatt?.close()
        } catch (_: Exception) {
        }

        isConnected = false
        alertCharacteristic = null
        _uiState.update {
            it.copy(
                isConnected = false,
                connectedName = name,
                connectedAddress = address,
                lastStatus = "$name 연결 중..."
            )
        }

        gatt = device.connectGatt(context.applicationContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    private fun sendAlert(id: String, type: String, title: String, message: String, time: String?) {
        val payload = JSONObject()
            .put("id", id)
            .put("type", type)
            .put("title", title)
            .put("message", message)
            .put("time", time ?: "")
            .toString()

        pendingPayloads.offer(payload)
        flushQueue()

        val context = appContext
        if (context != null && !isConnected) {
            start(context)
        }
    }

    private fun enqueueWatchStatus(context: Context) {
        val workerId = getCurrentWorkerId(context).ifBlank { "UNKNOWN" }
        val payload = JSONObject()
            .put("type", "watch_status")
            .put("worker_id", workerId)
            .put("message", "알림 수신 대기 중")
            .toString()

        pendingPayloads.offer(payload)
        Log.d(TAG, "watch status queued workerId=$workerId")
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            handleScanResult(result)
        }

        @SuppressLint("MissingPermission")
        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach { handleScanResult(it) }
        }

        override fun onScanFailed(errorCode: Int) {
            isScanning = false
            _uiState.update {
                it.copy(isScanning = false, lastStatus = "BLE 스캔 실패: $errorCode")
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun handleScanResult(result: ScanResult) {
        val context = appContext ?: return
        if (!hasBlePermission(context)) return

        val device = result.device ?: return
        val scanName = result.scanRecord?.deviceName
        val deviceName = try { device.name } catch (_: Exception) { null }
        val name = scanName ?: deviceName ?: "N/A"
        val serviceUuids = result.scanRecord?.serviceUuids.orEmpty()
        val hasService = serviceUuids.any { it.uuid == ALERT_SERVICE_UUID }
        val isTarget = hasService ||
            name.equals(WATCH_NAME_SHORT, true) ||
            name.equals(WATCH_NAME_LONG, true) ||
            name.contains("TWATCH", ignoreCase = true) ||
            name.contains("T-WATCH", ignoreCase = true)

        Log.d(TAG, "scan result name=$name address=${device.address} rssi=${result.rssi} hasService=$hasService uuids=$serviceUuids")

        val item = ScannedDevice(
            name = name,
            address = device.address,
            rssi = result.rssi,
            isTargetWatch = isTarget
        )

        scannedDevices[device.address] = item
        val list = scannedDevices.values
            .sortedWith(
                compareByDescending<ScannedDevice> { it.isTargetWatch }
                    .thenByDescending { it.rssi }
            )

        _uiState.update {
            it.copy(
                devices = list,
                lastStatus = if (isScanning) "BLE 기기 스캔 중... ${list.size}개 발견" else it.lastStatus
            )
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val context = appContext ?: return
            if (!hasBlePermission(context)) return

            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                isConnected = true
                _uiState.update {
                    it.copy(
                        isConnected = true,
                        connectedAddress = gatt.device.address,
                        connectedName = it.connectedName ?: getSavedName(context) ?: gatt.device.name ?: WATCH_NAME_SHORT,
                        lastStatus = "워치 연결됨. 서비스 확인 중..."
                    )
                }
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                isConnected = false
                isWriting = false
                alertCharacteristic = null
                try { gatt.close() } catch (_: Exception) {}
                this@TWatchBleNotifier.gatt = null

                _uiState.update {
                    it.copy(
                        isConnected = false,
                        lastStatus = "워치 연결 끊김"
                    )
                }
            } else if (status != BluetoothGatt.GATT_SUCCESS) {
                isConnected = false
                isWriting = false
                alertCharacteristic = null
                try { gatt.close() } catch (_: Exception) {}
                this@TWatchBleNotifier.gatt = null
                _uiState.update { it.copy(isConnected = false, lastStatus = "워치 연결 실패: $status") }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                updateStatus("GATT 서비스 검색 실패: $status")
                return
            }

            val service: BluetoothGattService = gatt.getService(ALERT_SERVICE_UUID)
                ?: run {
                    updateStatus("워치 알림 서비스를 찾지 못했습니다.")
                    return
                }

            alertCharacteristic = service.getCharacteristic(ALERT_CHAR_UUID)
            if (alertCharacteristic == null) {
                updateStatus("워치 알림 Characteristic을 찾지 못했습니다.")
                return
            }

            updateStatus("워치 연결 완료")
            appContext?.let { enqueueWatchStatus(it) }
            flushQueue()
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            isWriting = false
            if (status == BluetoothGatt.GATT_SUCCESS) {
                updateStatus("워치 알림 전송 완료")
            } else {
                updateStatus("워치 알림 전송 실패: $status")
            }
            flushQueue()
        }
    }

    @SuppressLint("MissingPermission")
    private fun flushQueue() {
        val context = appContext ?: return
        if (!hasBlePermission(context)) return
        if (isWriting) return

        val currentGatt = gatt ?: return
        val characteristic = alertCharacteristic ?: return
        val payload = pendingPayloads.poll() ?: return
        val bytes = payload.take(240).toByteArray(StandardCharsets.UTF_8)

        isWriting = true
        val ok = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            currentGatt.writeCharacteristic(
                characteristic,
                bytes,
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            ) == BluetoothGatt.GATT_SUCCESS
        } else {
            @Suppress("DEPRECATION")
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            @Suppress("DEPRECATION")
            characteristic.value = bytes
            @Suppress("DEPRECATION")
            currentGatt.writeCharacteristic(characteristic)
        }

        if (!ok) {
            isWriting = false
            pendingPayloads.offer(payload)
            updateStatus("워치 알림 전송 시작 실패")
        } else {
            updateStatus("워치 알림 전송 중...")
        }
    }

    private fun updateStatus(message: String) {
        Log.d(TAG, message)
        _uiState.update { it.copy(lastStatus = message) }
    }

    private fun saveSelectedDevice(context: Context, address: String, name: String) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(PREF_ADDRESS, address)
            .putString(PREF_NAME, name)
            .apply()
    }

    private fun getCurrentWorkerId(context: Context): String {
        return context.applicationContext
            .getSharedPreferences(LOGIN_PREF_NAME, Context.MODE_PRIVATE)
            .getString(LOGIN_KEY_WORKER_ID, null)
            ?.trim()
            .orEmpty()
    }

    private fun getSavedAddress(context: Context): String? =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(PREF_ADDRESS, null)

    private fun getSavedName(context: Context): String? =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(PREF_NAME, null)

    private fun hasBlePermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun isLocationServiceEnabled(context: Context): Boolean {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return true
        return try {
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        } catch (_: Exception) {
            true
        }
    }
}
