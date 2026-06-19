package net.jgpower.gichan_land.data.datastore

import android.content.Context

object PendingLogoutStore {
    private const val PREF_NAME = "pending_logout_store"
    private const val KEY_WORKER_ID = "pending_logout_worker_id"

    fun save(context: Context, workerId: String) {
        val id = workerId.trim()
        if (id.isBlank()) return

        context.applicationContext
            .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_WORKER_ID, id)
            .apply()
    }

    fun get(context: Context): String? {
        return context.applicationContext
            .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_WORKER_ID, null)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    fun clear(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_WORKER_ID)
            .apply()
    }

    fun clearIfMatches(context: Context, workerId: String) {
        val saved = get(context)
        if (saved == workerId.trim()) {
            clear(context)
        }
    }
}
