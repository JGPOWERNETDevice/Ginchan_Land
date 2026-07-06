package net.jgpower.gichan_land.navigation

import net.jgpower.gichan_land.data.walkie.WalkieGlobalState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import android.content.pm.PackageManager
import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Process
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
import net.jgpower.gichan_land.data.datastore.PendingLogoutStore
import net.jgpower.gichan_land.data.textalert.TextAlertState
import net.jgpower.gichan_land.network.ApiServiceManager
import net.jgpower.gichan_land.network.AppWebSocketManager
import net.jgpower.gichan_land.repository.AuthRepository
import net.jgpower.gichan_land.service.WebSocketForegroundService
import net.jgpower.gichan_land.ui.action_report.ActionReportScreen
import net.jgpower.gichan_land.ui.alert_detail.AlertDetailScreen
import net.jgpower.gichan_land.ui.components.AppAlertPopupHost
import net.jgpower.gichan_land.ui.event_create.EventCreateScreen
import net.jgpower.gichan_land.ui.group.GroupEditScreen
import net.jgpower.gichan_land.ui.main.MainScreen
import net.jgpower.gichan_land.ui.notice.NoticeScreen
import net.jgpower.gichan_land.ui.signin.SignInScreen
import net.jgpower.gichan_land.ui.walkietalkie.WalkieTalkieScreen
import net.jgpower.gichan_land.ui.walkietalkie.WalkieIncomingCallPopupHost
import net.jgpower.gichan_land.ui.watch.TWatchConnectScreen
import kotlinx.coroutines.delay
import net.jgpower.gichan_land.network.WalkieTalkieManager
import net.jgpower.gichan_land.network.WalkieSignalingClient

object Routes {
    const val SIGN_IN = "sign_in"
    const val MAIN = "main"
    const val ALERT_DETAIL = "alert_detail"
    const val ACTION_REPORT = "action_report"
    const val EVENT_CREATE = "event_create"
    const val NOTICE = "notice"
    const val WALKIE_TALKIE = "walkie_talkie"
    const val T_WATCH = "t_watch"
    const val GROUP_EDIT = "group_edit"
}

@Composable
fun AppNavigation(
    startAlertId: String? = null,
    startWorkerId: String? = null,
    startWalkie: Boolean = false,
    startActionReport: Boolean = false,
    onStartAlertConsumed: () -> Unit = {},
    onStartWalkieConsumed: () -> Unit = {}
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val activity = context.findActivity()
    val loginDataStore = remember { LoginDataStore(context) }
    val lifecycleOwner = LocalLifecycleOwner.current

    val currentRoute = remember { mutableStateOf(Routes.SIGN_IN) }
    val loginWorkerId = remember { mutableStateOf("") }
    val selectedAlertId = remember { mutableStateOf("") }
    val isCheckingLogin = remember { mutableStateOf(true) }
    val groupRefreshKey = remember { mutableStateOf(0) }

    val coroutineScope = rememberCoroutineScope()

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

    fun startWalkieReceiver(workerId: String) {
        if (workerId.isBlank()) {
            Log.d("WALKIE_NAV", "startWalkieReceiver skipped. workerId blank")
            return
        }

        coroutineScope.launch {
            repeat(10) { index ->
                try {
                    val workers = ApiServiceManager.apiService.getOnlineWorkers()
                    val me = workers.firstOrNull { it.workerId?.trim() == workerId }
                    val areaGroup = me?.primaryGroupName()

                    if (!areaGroup.isNullOrBlank()) {
                        WalkieTalkieManager.start(
                            context = appContext,
                            workerId = workerId,
                            areaGroup = areaGroup
                        )

                        Log.d(
                            "WALKIE_NAV",
                            "walkie receiver started workerId=$workerId areaGroup=$areaGroup"
                        )

                        return@launch
                    }

                    Log.d(
                        "WALKIE_NAV",
                        "worker not found in online list yet. retry=$index workerId=$workerId"
                    )
                } catch (e: Exception) {
                    Log.e("WALKIE_NAV", "startWalkieReceiver failed retry=$index", e)
                }

                delay(1000L)
            }

            Log.d("WALKIE_NAV", "startWalkieReceiver failed after retries workerId=$workerId")
        }
    }

    fun stopWebSocketService() {
        Log.d("WS_NAV", "stopWebSocketService")

        try {
            WebSocketForegroundService.requestStop(appContext)
            Log.d("WS_NAV", "requestStop called")
        } catch (e: Exception) {
            Log.e("WS_NAV", "requestStop failed", e)
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
            startWalkieReceiver(savedWorkerId)
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
                startWalkieReceiver(startWorkerId)
            }

            currentRoute.value = if (startActionReport) {
                Routes.ACTION_REPORT
            } else {
                Routes.ALERT_DETAIL
            }
            onStartAlertConsumed()
        }
    }

    LaunchedEffect(startWalkie, loginWorkerId.value) {
        if (startWalkie && loginWorkerId.value.isNotBlank()) {
            currentRoute.value = Routes.WALKIE_TALKIE
            onStartWalkieConsumed()
        }
    }

    if (isCheckingLogin.value) {
        Text("로그인 상태 확인 중...")
        return
    }

    fun startMicFromTopBar() {
        if (!WalkieGlobalState.isCallActive.value || WalkieGlobalState.activeCallId.value.isNullOrBlank()) {
            WalkieGlobalState.lastStatusText.value = "먼저 통화를 연결하세요."
            return
        }

        val started = WalkieTalkieManager.startTransmit(context)
        WalkieGlobalState.isMicOn.value = started
        WalkieGlobalState.lastStatusText.value = if (started) "내 MIC ON" else "송신 시작 실패"
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startMicFromTopBar()
        } else {
            WalkieGlobalState.lastStatusText.value = "마이크 권한이 필요합니다."
        }
    }

    fun toggleMicFromTopBar() {
        if (WalkieGlobalState.isMicOn.value) {
            WalkieTalkieManager.stopTransmit()
            WalkieGlobalState.isMicOn.value = false
            WalkieGlobalState.lastStatusText.value = "통화 연결됨"
            return
        }

        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            startMicFromTopBar()
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun endCallFromTopBar() {
        val callId = WalkieGlobalState.activeCallId.value
        val workerId = loginWorkerId.value

        if (!callId.isNullOrBlank() && workerId.isNotBlank()) {
            WalkieSignalingClient.endCall(
                callId = callId,
                workerId = workerId
            )
        }

        WalkieTalkieManager.stopTransmit()
        WalkieGlobalState.isMicOn.value = false
        WalkieGlobalState.clearCall("통화 종료됨")
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
    ) {
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
                        startWalkieReceiver(workerId)
                    }
                }
            )
        }

        Routes.MAIN -> {
            MainScreen(
                workerId = loginWorkerId.value,
                groupRefreshKey = groupRefreshKey.value,
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
                onWalkieTalkieClick = {
                    currentRoute.value = Routes.WALKIE_TALKIE
                },
                onWatchConnectClick = {
                    currentRoute.value = Routes.T_WATCH
                },
                onGroupEditClick = {
                    currentRoute.value = Routes.GROUP_EDIT
                },
                onLogoutClick = {
                    val workerId = loginWorkerId.value

                    Log.d("WS_NAV", "onLogoutClick workerId=$workerId")

                    coroutineScope.launch {
                        var serverLogoutSucceeded = workerId.isBlank()

                        try {
                            if (workerId.isNotBlank()) {
                                ApiServiceManager.init(appContext)
                                val response = AuthRepository(ApiServiceManager.apiService).logout(workerId)
                                serverLogoutSucceeded = response.isSuccessful

                                if (serverLogoutSucceeded) {
                                    PendingLogoutStore.clearIfMatches(appContext, workerId)
                                } else {
                                    PendingLogoutStore.save(appContext, workerId)
                                    Log.e("WS_NAV", "logout server response failed code=${response.code()}")
                                }
                            }
                        } catch (e: Exception) {
                            if (workerId.isNotBlank()) {
                                PendingLogoutStore.save(appContext, workerId)
                            }
                            Log.e("WS_NAV", "logout server request failed", e)
                        } finally {
                            try {
                                stopWebSocketService()
                                AppWebSocketManager.disconnect()
                                WalkieSignalingClient.disconnect()
                                WalkieTalkieManager.stop()
                            } catch (e: Exception) {
                                Log.e("WS_NAV", "logout local cleanup failed", e)
                            }

                            if (!serverLogoutSucceeded && workerId.isNotBlank()) {
                                Log.d("WS_NAV", "pending logout saved workerId=$workerId")
                            }

                            loginDataStore.clearLogin()
                            loginWorkerId.value = ""
                            selectedAlertId.value = ""
                            currentRoute.value = Routes.SIGN_IN

                            AppAlertPopupState.clear()
                            TextAlertState.clear()
                        }
                    }
                },
                onExitAppClick = {
                    Log.d("WS_NAV", "onExitAppClick workerId=${loginWorkerId.value}")

                    coroutineScope.launch {
                        try {
                            stopWebSocketService()
                            AppWebSocketManager.disconnect()
                            WalkieSignalingClient.disconnect()
                            WalkieTalkieManager.stop()
                            AppVisibilityState.setForeground(false)
                            AppAlertPopupState.clear()
                            TextAlertState.clear()
                        } catch (e: Exception) {
                            Log.e("WS_NAV", "exit app cleanup failed", e)
                        } finally {
                            activity?.finishAndRemoveTask() ?: activity?.finish()
                            Process.killProcess(Process.myPid())
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

        Routes.WALKIE_TALKIE -> {
            WalkieTalkieScreen(
                workerId = loginWorkerId.value,
                onBackClick = {
                    currentRoute.value = Routes.MAIN
                }
            )
        }

        Routes.T_WATCH -> {
            TWatchConnectScreen(
                onBackClick = {
                    currentRoute.value = Routes.MAIN
                }
            )
        }

        Routes.GROUP_EDIT -> {
            GroupEditScreen(
                workerId = loginWorkerId.value,
                onBackClick = {
                    currentRoute.value = Routes.MAIN
                },
                onGroupsChanged = {
                    groupRefreshKey.value += 1
                    startWalkieReceiver(loginWorkerId.value)
                }
            )
        }
    }


        // Keep the alert popup host outside the login/route guard.
        // On Redmi Note8 / Android 10, login state restoration can lag behind activity resume,
        // so gating this host can make a stored background alert invisible.
        AppAlertPopupHost()

        if (
            loginWorkerId.value.isNotBlank() &&
            currentRoute.value != Routes.SIGN_IN
        ) {
            WalkieIncomingCallPopupHost(
                workerId = loginWorkerId.value,
                enabled = currentRoute.value != Routes.WALKIE_TALKIE,
                onOpenWalkie = {
                    currentRoute.value = Routes.WALKIE_TALKIE
                }
            )
        }
    }
}

@Composable
private fun WalkieActiveCallTopBar(
    visible: Boolean,
    onOpenWalkie: () -> Unit,
    onToggleMic: () -> Unit,
    onEndCall: () -> Unit,
    modifier: Modifier = Modifier
) {
    val callId = WalkieGlobalState.activeCallId.value
    val isCallActive = WalkieGlobalState.isCallActive.value
    val isEmergencyBroadcastActive = WalkieGlobalState.isEmergencyBroadcastActive.value

    if (!visible || !isCallActive || callId.isNullOrBlank() || isEmergencyBroadcastActive) {
        return
    }

    val peerWorkerId = WalkieGlobalState.activePeerWorkerId.value ?: "상대"
    val isMicOn = WalkieGlobalState.isMicOn.value

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "통화중: $peerWorkerId / MIC ${if (isMicOn) "ON" else "OFF"}",
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodySmall
            )

            OutlinedButton(onClick = onOpenWalkie) {
                Text("무전기")
            }

            OutlinedButton(onClick = onToggleMic) {
                Text(if (isMicOn) "MIC OFF" else "MIC ON")
            }

            Button(onClick = onEndCall) {
                Text("통화 종료")
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
