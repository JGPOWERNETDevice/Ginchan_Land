package net.jgpower.gichan_land.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import net.jgpower.gichan_land.MainActivity
import net.jgpower.gichan_land.data.walkie.WalkieGlobalState
import net.jgpower.gichan_land.network.ServerConfig
import net.jgpower.gichan_land.network.WalkieSignalingClient

class WalkieIncomingCallActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val callId = intent.getStringExtra(EXTRA_CALL_ID).orEmpty()
        val workerId = intent.getStringExtra(EXTRA_WORKER_ID).orEmpty()

        if (callId.isBlank() || workerId.isBlank()) {
            Log.d(TAG, "ignore action=$action callId=$callId workerId=$workerId")
            return
        }

        when (action) {
            ACTION_ACCEPT -> {
                if (!ServerConfig.isWalkieNetworkAvailable(context) || !WalkieSignalingClient.isConnected()) {
                    Log.d(TAG, "accept blocked. walkie network unavailable callId=$callId workerId=$workerId")
                    WalkieGlobalState.removeIncomingCall(callId)
                    AppNotificationManager.cancelWalkieIncomingCallNotification(context, callId)
                    return
                }

                Log.d(TAG, "accept callId=$callId workerId=$workerId")
                WalkieSignalingClient.acceptCall(callId = callId, workerId = workerId)
                WalkieGlobalState.removeIncomingCall(callId)
                AppNotificationManager.cancelWalkieIncomingCallNotification(context, callId)
                openWalkieScreen(context, callId)
            }

            ACTION_REJECT -> {
                Log.d(TAG, "reject callId=$callId workerId=$workerId")
                WalkieSignalingClient.rejectCall(callId = callId, workerId = workerId)
                WalkieGlobalState.removeIncomingCall(callId)
                AppNotificationManager.cancelWalkieIncomingCallNotification(context, callId)
            }
        }
    }

    private fun openWalkieScreen(context: Context, callId: String) {
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra("openWalkie", true)
            putExtra("walkieCallId", callId)
        }
        context.startActivity(openIntent)
    }

    companion object {
        private const val TAG = "WALKIE_ACTION"

        const val ACTION_ACCEPT = "net.jgpower.gichan_land.action.WALKIE_ACCEPT"
        const val ACTION_REJECT = "net.jgpower.gichan_land.action.WALKIE_REJECT"

        const val EXTRA_CALL_ID = "callId"
        const val EXTRA_WORKER_ID = "workerId"
    }
}
