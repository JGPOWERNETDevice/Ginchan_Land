package net.jgpower.gichan_land.data.alert

import android.content.Context
import net.jgpower.gichan_land.data.textalert.TextAlert
import org.json.JSONArray
import org.json.JSONObject

object PendingAlertStore {
    private const val PREF_NAME = "pending_alert_popup_store"
    private const val KEY_SAFETY = "safety_alerts"
    private const val KEY_TEXT = "text_alerts"
    private const val MAX_ITEMS = 20

    @Synchronized
    fun saveSafetyAlert(context: Context, alert: WorkerAlert) {
        val json = JSONObject().apply {
            put("alertId", alert.alertId)
            put("eventId", alert.eventId)
            put("receiverId", alert.receiverId)
            put("receiveType", alert.receiveType)
            put("targetType", alert.targetType ?: "")
            put("message", alert.message)
            put("occurredAt", alert.occurredAt)
            put("status", alert.status)
        }
        append(context, KEY_SAFETY, "alertId", alert.alertId, json)
    }

    @Synchronized
    fun saveTextAlert(context: Context, alert: TextAlert) {
        val json = JSONObject().apply {
            put("textAlertId", alert.textAlertId)
            put("receiverId", alert.receiverId)
            put("receiveType", alert.receiveType)
            put("message", alert.message)
            put("createdAt", alert.createdAt)
        }
        append(context, KEY_TEXT, "textAlertId", alert.textAlertId, json)
    }

    /**
     * Use peek for Redmi Note8 / Android 10 resume recovery.
     * Do not clear here. If the activity resumes before Compose is ready and we clear immediately,
     * the popup can be lost. Items are removed only when the user dismisses the in-app popup.
     */
    @Synchronized
    fun peekSafetyAlerts(context: Context): List<WorkerAlert> {
        return parseSafetyArray(readArray(context, KEY_SAFETY))
    }

    @Synchronized
    fun peekTextAlerts(context: Context): List<TextAlert> {
        return parseTextArray(readArray(context, KEY_TEXT))
    }

    @Synchronized
    fun removeSafetyAlert(context: Context, alertId: String) {
        removeById(context, KEY_SAFETY, "alertId", alertId)
    }

    @Synchronized
    fun removeTextAlert(context: Context, textAlertId: String) {
        removeById(context, KEY_TEXT, "textAlertId", textAlertId)
    }

    @Synchronized
    fun drainSafetyAlerts(context: Context): List<WorkerAlert> {
        val result = peekSafetyAlerts(context)
        clear(context, KEY_SAFETY)
        return result
    }

    @Synchronized
    fun drainTextAlerts(context: Context): List<TextAlert> {
        val result = peekTextAlerts(context)
        clear(context, KEY_TEXT)
        return result
    }

    private fun parseSafetyArray(array: JSONArray): List<WorkerAlert> {
        val result = mutableListOf<WorkerAlert>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val alertId = item.optString("alertId")
            if (alertId.isBlank()) continue
            result += WorkerAlert(
                alertId = alertId,
                eventId = item.optString("eventId"),
                receiverId = item.optString("receiverId"),
                receiveType = item.optString("receiveType"),
                targetType = item.optString("targetType").takeIf { it.isNotBlank() },
                message = item.optString("message"),
                occurredAt = item.optString("occurredAt"),
                status = item.optString("status")
            )
        }
        return result
    }

    private fun parseTextArray(array: JSONArray): List<TextAlert> {
        val result = mutableListOf<TextAlert>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val textAlertId = item.optString("textAlertId")
            if (textAlertId.isBlank()) continue
            result += TextAlert(
                textAlertId = textAlertId,
                receiverId = item.optString("receiverId"),
                receiveType = item.optString("receiveType"),
                message = item.optString("message"),
                createdAt = item.optString("createdAt")
            )
        }
        return result
    }

    private fun append(
        context: Context,
        key: String,
        idField: String,
        idValue: String,
        json: JSONObject
    ) {
        if (idValue.isBlank()) return

        val current = readArray(context, key)
        val merged = mutableListOf<JSONObject>()
        merged += json

        for (index in 0 until current.length()) {
            val item = current.optJSONObject(index) ?: continue
            if (item.optString(idField) == idValue) continue
            merged += item
            if (merged.size >= MAX_ITEMS) break
        }

        val out = JSONArray()
        merged.forEach { out.put(it) }
        prefs(context).edit().putString(key, out.toString()).apply()
    }

    private fun removeById(
        context: Context,
        key: String,
        idField: String,
        idValue: String
    ) {
        if (idValue.isBlank()) return
        val current = readArray(context, key)
        val out = JSONArray()
        for (index in 0 until current.length()) {
            val item = current.optJSONObject(index) ?: continue
            if (item.optString(idField) == idValue) continue
            out.put(item)
        }
        prefs(context).edit().putString(key, out.toString()).apply()
    }

    private fun readArray(context: Context, key: String): JSONArray {
        val raw = prefs(context).getString(key, null) ?: return JSONArray()
        return try {
            JSONArray(raw)
        } catch (_: Exception) {
            JSONArray()
        }
    }

    private fun clear(context: Context, key: String) {
        prefs(context).edit().remove(key).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
}
