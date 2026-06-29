package net.jgpower.gichan_land

import android.Manifest
import android.content.Intent
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.core.view.WindowCompat
import net.jgpower.gichan_land.navigation.AppNavigation
import net.jgpower.gichan_land.network.ApiServiceManager
import net.jgpower.gichan_land.network.AppWebSocketManager
import net.jgpower.gichan_land.service.AppNotificationManager
import net.jgpower.gichan_land.ui.theme.Gichan_LandTheme
import net.jgpower.gichan_land.watch.TWatchBleNotifier

class MainActivity : ComponentActivity() {

    private val startAlertId = mutableStateOf<String?>(null)
    private val startWorkerId = mutableStateOf<String?>(null)
    private val startWalkie = mutableStateOf(false)

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

        configureSystemBars()

        setContent {
            Gichan_LandTheme {
                AppNavigation(
                    startAlertId = startAlertId.value,
                    startWorkerId = startWorkerId.value,
                    startWalkie = startWalkie.value,
                    onStartAlertConsumed = {
                        startAlertId.value = null
                        startWorkerId.value = null
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
        readIntent(intent)
    }

    private fun configureSystemBars() {
        // One UI 8.5 / 최신 Android에서 enableEdgeToEdge() 사용 시
        // Compose 화면이 상태바/내비게이션바 밑으로 들어가 하단 내용이 가려지고,
        // 흰 배경 위 상태바 아이콘도 흰색으로 보이는 문제가 발생합니다.
        // 기존 앱처럼 시스템 바 영역을 침범하지 않도록 decor fitting을 켜고,
        // 밝은 배경에 맞춰 상태바/내비게이션바 아이콘을 어둡게 고정합니다.
        WindowCompat.setDecorFitsSystemWindows(window, true)

        window.statusBarColor = Color.WHITE
        window.navigationBarColor = Color.WHITE

        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightStatusBars = true
        controller.isAppearanceLightNavigationBars = true
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
        startAlertId.value = intent?.getStringExtra("alertId")
        startWorkerId.value = intent?.getStringExtra("workerId")
        if (intent?.getBooleanExtra("openWalkie", false) == true) {
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