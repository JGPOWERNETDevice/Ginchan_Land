package net.jgpower.gichan_land.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.loginDataStore by preferencesDataStore(name = "login_data")

class LoginDataStore(
    private val context: Context
) {
    private val workerIdKey = stringPreferencesKey("worker_id")

    suspend fun saveLogin(workerId: String) {
        context.loginDataStore.edit { preferences ->
            preferences[workerIdKey] = workerId
        }
    }

    suspend fun getSavedWorkerId(): String? {
        val preferences = context.loginDataStore.data.first()
        return preferences[workerIdKey]
    }

    suspend fun clearLogin() {
        context.loginDataStore.edit { preferences ->
            preferences.remove(workerIdKey)
        }
    }
}