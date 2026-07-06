package net.jgpower.gichan_land.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat

class WebSocketKeepAliveReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        val workerId = intent?.getStringExtra(WebSocketForegroundService.EXTRA_WORKER_ID)
            ?: context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(PREF_WORKER_ID, null)

        if (action != ACTION_KEEP_ALIVE && action != Intent.ACTION_BOOT_COMPLETED) {
            return
        }

        if (workerId.isNullOrBlank()) {
            Log.d(TAG, "skip restart. workerId blank action=$action")
            return
        }

        val serviceIntent = Intent(context, WebSocketForegroundService::class.java).apply {
            putExtra(WebSocketForegroundService.EXTRA_WORKER_ID, workerId)
        }

        try {
            ContextCompat.startForegroundService(context, serviceIntent)
            Log.d(TAG, "foreground service restart requested action=$action workerId=$workerId")
        } catch (e: Exception) {
            Log.e(TAG, "startForegroundService failed", e)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                try {
                    context.startService(serviceIntent)
                } catch (startError: Exception) {
                    Log.e(TAG, "startService fallback failed", startError)
                }
            }
        }
    }

    companion object {
        const val ACTION_KEEP_ALIVE = "net.jgpower.gichan_land.action.WS_KEEP_ALIVE"
        private const val TAG = "WS_KEEP_ALIVE"
        private const val PREFS_NAME = "gichan_land_ws_service"
        private const val PREF_WORKER_ID = "workerId"
    }
}
