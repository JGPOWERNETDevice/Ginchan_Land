package net.jgpower.gichan_land.data.datastore

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Small login-state store.
 *
 * This intentionally uses SharedPreferences instead of AndroidX DataStore because
 * some DataStore versions package libdatastore_shared_counter.so, which can trigger
 * Android 15+/One UI 16KB page-size compatibility warnings on 16KB-page devices.
 *
 * Public suspend functions are kept the same so AppNavigation and existing call
 * sites do not need to change.
 */
class LoginDataStore(
    private val context: Context
) {
    private val appContext = context.applicationContext

    private val preferences by lazy {
        appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    suspend fun saveLogin(workerId: String) {
        withContext(Dispatchers.IO) {
            preferences.edit()
                .putString(KEY_WORKER_ID, workerId)
                .apply()
        }
    }

    suspend fun getSavedWorkerId(): String? {
        return withContext(Dispatchers.IO) {
            preferences.getString(KEY_WORKER_ID, null)
        }
    }

    suspend fun clearLogin() {
        withContext(Dispatchers.IO) {
            preferences.edit()
                .remove(KEY_WORKER_ID)
                .apply()
        }
    }

    private companion object {
        const val PREF_NAME = "login_data"
        const val KEY_WORKER_ID = "worker_id"
    }
}
