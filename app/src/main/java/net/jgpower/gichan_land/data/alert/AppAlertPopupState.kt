package net.jgpower.gichan_land.data.alert

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import net.jgpower.gichan_land.data.textalert.TextAlert

sealed class AppAlertPopupItem {
    abstract val uniqueId: String
    abstract val createdAt: String

    data class Safety(
        val alert: WorkerAlert
    ) : AppAlertPopupItem() {
        override val uniqueId: String = "safety-${alert.alertId}"
        override val createdAt: String = alert.occurredAt
    }

    data class Text(
        val alert: TextAlert
    ) : AppAlertPopupItem() {
        override val uniqueId: String = "text-${alert.textAlertId}"
        override val createdAt: String = alert.createdAt
    }
}

object AppAlertPopupState {
    private val _popupQueue = MutableStateFlow<List<AppAlertPopupItem>>(emptyList())
    val popupQueue: StateFlow<List<AppAlertPopupItem>> = _popupQueue

    fun enqueueSafety(alert: WorkerAlert) {
        enqueue(AppAlertPopupItem.Safety(alert))
    }

    fun enqueueText(alert: TextAlert) {
        enqueue(AppAlertPopupItem.Text(alert))
    }

    private fun enqueue(item: AppAlertPopupItem) {
        _popupQueue.update { current ->
            listOf(item)
                .plus(current)
                .distinctBy { it.uniqueId }
        }
    }

    fun dismissCurrentPopup() {
        _popupQueue.update { current ->
            if (current.isEmpty()) {
                emptyList()
            } else {
                current.drop(1)
            }
        }
    }

    fun clear() {
        _popupQueue.value = emptyList()
    }
}