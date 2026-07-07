package net.jgpower.gichan_land

import android.Manifest
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.core.view.WindowCompat
import net.jgpower.gichan_land.navigation.AppNavigation
import net.jgpower.gichan_land.network.ApiServiceManager
import net.jgpower.gichan_land.network.AppWebSocketManager
import net.jgpower.gichan_land.service.AppNotificationManager
import net.jgpower.gichan_land.data.alert.PendingAlertStore
import net.jgpower.gichan_land.data.alert.AppAlertPopupState
import net.jgpower.gichan_land.data.alert.WorkerAlert
import net.jgpower.gichan_land.data.textalert.TextAlert
import net.jgpower.gichan_land.ui.theme.Gichan_LandTheme
import net.jgpower.gichan_land.watch.TWatchBleNotifier

class MainActivity : ComponentActivity() {

    private val startAlertId = mutableStateOf<String?>(null)
    private val startWorkerId = mutableStateOf<String?>(null)
    private val startWalkie = mutableStateOf(false)
    private val startActionReport = mutableStateOf(false)

    private var connectivityManager: ConnectivityManager? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var refreshRunnable: Runnable? = null

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {

        override fun onAvailable(network: Network) {
            refreshServerConnectionDebounced()
        }

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities
        ) {
            refreshServerConnectionDebounced()
        }

        override fun onLost(network: Network) {
            refreshServerConnectionDebounced()
        }
    }

    private val appPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            TWatchBleNotifier.start(applicationContext)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ApiServiceManager.init(applicationContext)
        registerNetworkCallback()

        AppNotificationManager.createChannel(this)
        requestAppPermissionsIfNeeded()
        TWatchBleNotifier.start(applicationContext)
        readIntent(intent)
        schedulePendingAlertPopupRecovery()

        configureSystemBars()
        restoreForegroundBrightness()

        setContent {
            Gichan_LandTheme {
                AppNavigation(
                    startAlertId = startAlertId.value,
                    startWorkerId = startWorkerId.value,
                    startWalkie = startWalkie.value,
                    startActionReport = startActionReport.value,
                    onStartAlertConsumed = {
                        startAlertId.value = null
                        startWorkerId.value = null
                        startActionReport.value = false
                    },
                    onStartWalkieConsumed = {
                        startWalkie.value = false
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readIntent(intent)
        schedulePendingAlertPopupRecovery()
    }

    override fun onStart() {
        super.onStart()
        restoreForegroundBrightness()
    }

    override fun onPostResume() {
        super.onPostResume()
        restoreForegroundBrightness()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            restoreForegroundBrightness()
        }
    }

    private fun configureSystemBars() {
        // One UI 8.5 / 최신 Android에서 enableEdgeToEdge() 사용 시
        // Compose 화면이 상태바/내비게이션바 밑으로 들어가 하단 내용이 가려지고,
        // 흰 배경 위 상태바 아이콘도 흰색으로 보이는 문제가 발생합니다.
        // 기존 앱처럼 시스템 바 영역을 침범하지 않도록 decor fitting을 켜고,
        // 밝은 배경에 맞춰 상태바/내비게이션바 아이콘을 어둡게 고정합니다.
        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.setBackgroundDrawable(ColorDrawable(Color.WHITE))
        window.decorView.alpha = 1.0f



        window.statusBarColor = Color.WHITE
        window.navigationBarColor = Color.WHITE

        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightStatusBars = true
        controller.isAppearanceLightNavigationBars = true
    }

    override fun onResume() {
        super.onResume()
        schedulePendingAlertPopupRecovery()
        restoreForegroundBrightness()
    }

    private fun restoreForegroundBrightness() {
        try {
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

            fun applyBrightness() {
                try {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                    window.setBackgroundDrawable(ColorDrawable(Color.WHITE))
                    window.decorView.alpha = 1.0f
                    val attrs = window.attributes
                    // Redmi Note8 / MIUI can reopen the app from a lock-screen notification with
                    // a dimmed window. Force full app-window brightness while the activity is visible.
                    attrs.screenBrightness = 1.0f
                    attrs.dimAmount = 0.0f
                    window.attributes = attrs
                } catch (_: Exception) {
                }
            }

            applyBrightness()
            mainHandler.postDelayed({ applyBrightness() }, 150L)
            mainHandler.postDelayed({ applyBrightness() }, 500L)
            mainHandler.postDelayed({ applyBrightness() }, 1200L)
        } catch (_: Exception) {
        }
    }

    private fun schedulePendingAlertPopupRecovery() {
        // Redmi Note8 / Android 10 can resume the activity before Compose is ready.
        // Retry several times and do not delete stored alerts until the user closes the popup.
        listOf(0L, 250L, 800L, 1600L, 3000L).forEach { delayMs ->
            mainHandler.postDelayed({ restorePendingAlertPopups() }, delayMs)
        }
    }

    private fun restorePendingAlertPopups() {
        try {
            PendingAlertStore.peekSafetyAlerts(applicationContext).forEach { alert ->
                AppAlertPopupState.enqueueSafety(alert)
            }
            PendingAlertStore.peekTextAlerts(applicationContext).forEach { alert ->
                AppAlertPopupState.enqueueText(alert)
            }
        } catch (_: Exception) {
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        refreshRunnable?.let {
            mainHandler.removeCallbacks(it)
        }

        try {
            connectivityManager?.unregisterNetworkCallback(networkCallback)
        } catch (_: Exception) {
        }
    }

    private fun registerNetworkCallback() {
        connectivityManager =
            getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager?.registerNetworkCallback(request, networkCallback)
    }

    private fun refreshServerConnectionDebounced() {
        refreshRunnable?.let {
            mainHandler.removeCallbacks(it)
        }

        refreshRunnable = Runnable {
            ApiServiceManager.init(applicationContext)
            AppWebSocketManager.reconnectCurrent()
        }

        mainHandler.postDelayed(refreshRunnable!!, 1000L)
    }

    private fun readIntent(intent: Intent?) {
        if (intent == null) {
            return
        }

        val openAlertPopup = intent.getBooleanExtra("openAlertPopup", false)
        val openTextPopup = intent.getBooleanExtra("openTextPopup", false)

        if (openAlertPopup) {
            val alertId = intent.getStringExtra("alertId").orEmpty()
            val workerId = intent.getStringExtra("workerId").orEmpty()
            val message = intent.getStringExtra("alertMessage").orEmpty()
            if (alertId.isNotBlank()) {
                AppAlertPopupState.enqueueSafety(
                    WorkerAlert(
                        alertId = alertId,
                        eventId = intent.getStringExtra("alertEventId").orEmpty(),
                        receiverId = workerId,
                        receiveType = intent.getStringExtra("alertReceiveType").orEmpty(),
                        targetType = intent.getStringExtra("alertTargetType").takeIf { !it.isNullOrBlank() },
                        message = message,
                        occurredAt = intent.getStringExtra("alertOccurredAt").orEmpty(),
                        status = intent.getStringExtra("alertStatus").orEmpty()
                    )
                )
            }

            // Open main screen only. Do not navigate directly to detail/action report from
            // lock-screen notification on Redmi Note8, because MIUI can leave the activity dim/black.
            startAlertId.value = null
            startWorkerId.value = workerId
            startActionReport.value = false
        } else if (openTextPopup) {
            val textAlertId = intent.getStringExtra("textAlertId").orEmpty()
            if (textAlertId.isNotBlank()) {
                AppAlertPopupState.enqueueText(
                    TextAlert(
                        textAlertId = textAlertId,
                        receiverId = intent.getStringExtra("textReceiverId").orEmpty(),
                        receiveType = intent.getStringExtra("textReceiveType").orEmpty(),
                        message = intent.getStringExtra("textAlertMessage").orEmpty(),
                        createdAt = intent.getStringExtra("textCreatedAt").orEmpty()
                    )
                )
            }

            startAlertId.value = null
            startWorkerId.value = null
            startActionReport.value = false
        } else {
            startAlertId.value = intent.getStringExtra("alertId")
            startWorkerId.value = intent.getStringExtra("workerId")
            startActionReport.value = intent.getBooleanExtra("openActionReport", false)
        }

        if (intent.getBooleanExtra("openWalkie", false)) {
            startWalkie.value = true
        }
    }

    private fun requestAppPermissionsIfNeeded() {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions += Manifest.permission.POST_NOTIFICATIONS
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions += Manifest.permission.BLUETOOTH_SCAN
            permissions += Manifest.permission.BLUETOOTH_CONNECT
            // Nearby Devices is normally enough on Android 12+, but several Samsung/Android builds
            // return an empty BLE scan list unless Location permission is also granted.
            permissions += Manifest.permission.ACCESS_FINE_LOCATION
        } else {
            permissions += Manifest.permission.ACCESS_FINE_LOCATION
        }

        if (permissions.isNotEmpty()) {
            appPermissionLauncher.launch(permissions.toTypedArray())
        }
    }
}