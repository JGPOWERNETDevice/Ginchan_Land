package net.jgpower.gichan_land

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import net.jgpower.gichan_land.navigation.AppNavigation
import net.jgpower.gichan_land.service.AppNotificationManager
import net.jgpower.gichan_land.ui.theme.Gichan_LandTheme

class MainActivity : ComponentActivity() {

    private val startAlertId = mutableStateOf<String?>(null)
    private val startWorkerId = mutableStateOf<String?>(null)

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // 허용/거부 결과는 지금 단계에서는 별도 처리하지 않음
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        AppNotificationManager.createChannel(this)
        requestNotificationPermissionIfNeeded()
        readIntent(intent)

        enableEdgeToEdge()

        setContent {
            Gichan_LandTheme {
                AppNavigation(
                    startAlertId = startAlertId.value,
                    startWorkerId = startWorkerId.value,
                    onStartAlertConsumed = {
                        startAlertId.value = null
                        startWorkerId.value = null
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readIntent(intent)
    }

    private fun readIntent(intent: Intent?) {
        startAlertId.value = intent?.getStringExtra("alertId")
        startWorkerId.value = intent?.getStringExtra("workerId")
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}