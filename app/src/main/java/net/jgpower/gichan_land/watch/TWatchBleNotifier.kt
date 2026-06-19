package net.jgpower.gichan_land.watch

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Android app -> LilyGO T-Watch-S3 BLE notifier.
 *
 * Watch firmware acts as a BLE GATT server.
 * This Android app scans/connects as a BLE central and writes compact JSON to ALERT_CHAR_UUID.
 */
object TWatchBleNotifier {
    private const val TAG = "TWatchBleNotifier"
    private const val WATCH_NAME = "GICHAN-TWATCH-S3"

    val ALERT_SERVICE_UUID: UUID = UUID.fromString("8f7a0001-6a7b-4e12-9f2d-4a0d5f3c9a01")
    val ALERT_CHAR_UUID: UUID = UUID.fromString("8f7a0002-6a7b-4e12-9f2d-4a0d5f3c9a01")

    private val mainHandler = Handler(Looper.getMainLooper())
    private val pendingPayloads = ConcurrentLinkedQueue<String>()

    private var appContext: Context? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var gatt: BluetoothGatt? = null
    private var alertCharacteristic: BluetoothGattCharacteristic? = null
    private var isScanning = false
    private var isConnected = false

    fun start(context: Context) {
        appContext = context.applicationContext
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = manager.adapter

        if (!hasBlePermission(context)) {
            Log.w(TAG, "BLE permission missing")
            return
        }

        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            Log.w(TAG, "Bluetooth disabled")
            return
        }

        if (isConnected || isScanning) return
        scanAndConnect()
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
        alertCharacteristic = null
        gatt = null
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

    @SuppressLint("MissingPermission")
    private fun scanAndConnect() {
        val context = appContext ?: return
        if (!hasBlePermission(context)) return

        val scanner = bluetoothAdapter?.bluetoothLeScanner ?: return

        val filters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(ALERT_SERVICE_UUID))
                .build(),
            ScanFilter.Builder()
                .setDeviceName(WATCH_NAME)
                .build()
        )

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        isScanning = true
        scanner.startScan(filters, settings, scanCallback)

        mainHandler.postDelayed({
            if (isScanning) {
                try { scanner.stopScan(scanCallback) } catch (_: Exception) {}
                isScanning = false
            }
        }, 15000L)
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val context = appContext ?: return
            if (!hasBlePermission(context)) return

            val device = result.device ?: return
            val name = device.name ?: result.scanRecord?.deviceName
            val hasService = result.scanRecord?.serviceUuids?.any { it.uuid == ALERT_SERVICE_UUID } == true

            if (name == WATCH_NAME || hasService) {
                try { bluetoothAdapter?.bluetoothLeScanner?.stopScan(this) } catch (_: Exception) {}
                isScanning = false
                connect(device)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun connect(device: BluetoothDevice) {
        val context = appContext ?: return
        if (!hasBlePermission(context)) return

        gatt?.close()
        gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val context = appContext ?: return
            if (!hasBlePermission(context)) return

            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                isConnected = true
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                isConnected = false
                alertCharacteristic = null
                try { gatt.close() } catch (_: Exception) {}
                this@TWatchBleNotifier.gatt = null

                mainHandler.postDelayed({ start(context) }, 3000L)
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) return

            val service: BluetoothGattService = gatt.getService(ALERT_SERVICE_UUID) ?: return
            alertCharacteristic = service.getCharacteristic(ALERT_CHAR_UUID)
            flushQueue()
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            flushQueue()
        }
    }

    @SuppressLint("MissingPermission")
    private fun flushQueue() {
        val context = appContext ?: return
        if (!hasBlePermission(context)) return

        val currentGatt = gatt ?: return
        val characteristic = alertCharacteristic ?: return
        val payload = pendingPayloads.poll() ?: return
        val bytes = payload.take(240).toByteArray(StandardCharsets.UTF_8)

        characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT

        val ok = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            currentGatt.writeCharacteristic(characteristic, bytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == BluetoothGatt.GATT_SUCCESS
        } else {
            @Suppress("DEPRECATION")
            characteristic.value = bytes
            @Suppress("DEPRECATION")
            currentGatt.writeCharacteristic(characteristic)
        }

        if (!ok) {
            pendingPayloads.offer(payload)
        }
    }

    private fun hasBlePermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }
}
