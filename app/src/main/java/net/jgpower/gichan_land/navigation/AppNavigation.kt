package net.jgpower.gichan_land.navigation

import android.content.Intent
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch
import net.jgpower.gichan_land.data.alert.AppAlertPopupState
import net.jgpower.gichan_land.data.app.AppVisibilityState
import net.jgpower.gichan_land.data.datastore.LoginDataStore
import net.jgpower.gichan_land.data.textalert.TextAlertState
import net.jgpower.gichan_land.network.ApiServiceManager
import net.jgpower.gichan_land.network.AppWebSocketManager
import net.jgpower.gichan_land.repository.AuthRepository
import net.jgpower.gichan_land.service.WebSocketForegroundService
import net.jgpower.gichan_land.ui.action_report.ActionReportScreen
import net.jgpower.gichan_land.ui.alert_detail.AlertDetailScreen
import net.jgpower.gichan_land.ui.components.AppAlertPopupHost
import net.jgpower.gichan_land.ui.event_create.EventCreateScreen
import net.jgpower.gichan_land.ui.main.MainScreen
import net.jgpower.gichan_land.ui.notice.NoticeScreen
import net.jgpower.gichan_land.ui.signin.SignInScreen

@Composable
fun AppNavigation(
    startAlertId: String? = null,
    startWorkerId: String? = null,
    onStartAlertConsumed: () -> Unit = {}
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val loginDataStore = remember { LoginDataStore(context) }
    val lifecycleOwner = LocalLifecycleOwner.current

    val currentRoute = remember { mutableStateOf(Routes.SIGN_IN) }
    val loginWorkerId = remember { mutableStateOf("") }
    val selectedAlertId = remember { mutableStateOf("") }
    val isCheckingLogin = remember { mutableStateOf(true) }

    val coroutineScope = rememberCoroutineScope()

    val authRepository = remember {
        AuthRepository(ApiServiceManager.apiService)
    }

    fun startWebSocketService(workerId: String) {
        if (workerId.isBlank()) {
            Log.d("WS_NAV", "startWebSocketService skipped. workerId blank")
            return
        }

        Log.d("WS_NAV", "startWebSocketService workerId=$workerId")

        val intent = Intent(appContext, WebSocketForegroundService::class.java).apply {
            putExtra(WebSocketForegroundService.EXTRA_WORKER_ID, workerId)
        }

        try {
            ContextCompat.startForegroundService(appContext, intent)
            Log.d("WS_NAV", "startForegroundService called")
        } catch (e: Exception) {
            Log.e("WS_NAV", "startForegroundService failed", e)
        }
    }

    fun stopWebSocketService() {
        Log.d("WS_NAV", "stopWebSocketService")

        val intent = Intent(appContext, WebSocketForegroundService::class.java)

        try {
            appContext.stopService(intent)
            Log.d("WS_NAV", "stopService called")
        } catch (e: Exception) {
            Log.e("WS_NAV", "stopService failed", e)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    Log.d("WS_NAV", "Lifecycle ON_START")
                    AppVisibilityState.setForeground(true)
                }

                Lifecycle.Event.ON_STOP -> {
                    Log.d("WS_NAV", "Lifecycle ON_STOP")
                    AppVisibilityState.setForeground(false)
                }

                Lifecycle.Event.ON_DESTROY -> {
                    Log.d("WS_NAV", "Lifecycle ON_DESTROY")
                    AppVisibilityState.setForeground(false)
                }

                else -> Unit
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        Log.d("WS_NAV", "LaunchedEffect Unit start")

        val savedWorkerId = loginDataStore.getSavedWorkerId()

        Log.d("WS_NAV", "savedWorkerId=$savedWorkerId")

        if (!savedWorkerId.isNullOrBlank()) {
            loginWorkerId.value = savedWorkerId
            currentRoute.value = Routes.MAIN
            startWebSocketService(savedWorkerId)
        }

        isCheckingLogin.value = false
    }

    LaunchedEffect(startAlertId) {
        if (!startAlertId.isNullOrBlank()) {
            Log.d("WS_NAV", "startAlertId=$startAlertId startWorkerId=$startWorkerId")

            selectedAlertId.value = startAlertId

            if (!startWorkerId.isNullOrBlank()) {
                loginWorkerId.value = startWorkerId
                loginDataStore.saveLogin(startWorkerId)
                startWebSocketService(startWorkerId)
            }

            currentRoute.value = Routes.ALERT_DETAIL
            onStartAlertConsumed()
        }
    }

    if (isCheckingLogin.value) {
        androidx.compose.material3.Text("로그인 상태 확인 중...")
        return
    }

    when (currentRoute.value) {
        Routes.SIGN_IN -> {
            SignInScreen(
                onLoginSuccess = { workerId ->
                    Log.d("WS_NAV", "onLoginSuccess workerId=$workerId")

                    coroutineScope.launch {
                        loginDataStore.saveLogin(workerId)
                        loginWorkerId.value = workerId
                        selectedAlertId.value = ""
                        currentRoute.value = Routes.MAIN

                        startWebSocketService(workerId)
                    }
                }
            )
        }

        Routes.MAIN -> {
            MainScreen(
                workerId = loginWorkerId.value,
                onAlertClick = { alertId ->
                    selectedAlertId.value = alertId
                    currentRoute.value = Routes.ALERT_DETAIL
                },
                onActionReportClick = { alertId ->
                    selectedAlertId.value = alertId
                    currentRoute.value = Routes.ACTION_REPORT
                },
                onCreateEventClick = {
                    currentRoute.value = Routes.EVENT_CREATE
                },
                onNoticeClick = {
                    currentRoute.value = Routes.NOTICE
                },
                onLogoutClick = {
                    val workerId = loginWorkerId.value

                    Log.d("WS_NAV", "onLogoutClick workerId=$workerId")

                    coroutineScope.launch {
                        try {
                            stopWebSocketService()
                            AppWebSocketManager.disconnect()

                            if (workerId.isNotBlank()) {
                                authRepository.logout(workerId)
                            }
                        } catch (e: Exception) {
                            Log.e("WS_NAV", "logout failed", e)
                        } finally {
                            loginDataStore.clearLogin()
                            loginWorkerId.value = ""
                            selectedAlertId.value = ""
                            currentRoute.value = Routes.SIGN_IN

                            AppAlertPopupState.clear()
                            TextAlertState.clear()
                        }
                    }
                }
            )
        }

        Routes.ALERT_DETAIL -> {
            AlertDetailScreen(
                alertId = selectedAlertId.value,
                workerId = loginWorkerId.value,
                onBackClick = {
                    currentRoute.value = Routes.MAIN
                },
                onActionStartSuccess = {
                    currentRoute.value = Routes.ACTION_REPORT
                }
            )
        }

        Routes.ACTION_REPORT -> {
            ActionReportScreen(
                alertId = selectedAlertId.value,
                workerId = loginWorkerId.value,
                onBackClick = {
                    currentRoute.value = Routes.ALERT_DETAIL
                },
                onCompleteSuccess = {
                    currentRoute.value = Routes.MAIN
                }
            )
        }

        Routes.EVENT_CREATE -> {
            EventCreateScreen(
                workerId = loginWorkerId.value,
                onBackClick = {
                    currentRoute.value = Routes.MAIN
                }
            )
        }

        Routes.NOTICE -> {
            NoticeScreen(
                workerId = loginWorkerId.value,
                onBackClick = {
                    currentRoute.value = Routes.MAIN
                }
            )
        }
    }

    if (
        loginWorkerId.value.isNotBlank() &&
        currentRoute.value != Routes.SIGN_IN
    ) {
        AppAlertPopupHost()
    }
}
