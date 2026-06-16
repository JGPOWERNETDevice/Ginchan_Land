package net.jgpower.gichan_land

import android.Manifest
import android.content.Intent
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
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import net.jgpower.gichan_land.navigation.AppNavigation
import net.jgpower.gichan_land.network.ApiServiceManager
import net.jgpower.gichan_land.network.AppWebSocketManager
import net.jgpower.gichan_land.service.AppNotificationManager
import net.jgpower.gichan_land.ui.theme.Gichan_LandTheme

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

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // 허용/거부 결과는 지금 단계에서는 별도 처리하지 않음
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ApiServiceManager.init(applicationContext)
        registerNetworkCallback()

        AppNotificationManager.createChannel(this)
        requestNotificationPermissionIfNeeded()
        readIntent(intent)

        enableEdgeToEdge()

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
        startWalkie.value = intent?.getBooleanExtra("openWalkie", false) == true
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}