package net.jgpower.gichan_land.data.textalert

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

object TextAlertState {
    private val _alerts = MutableStateFlow<List<TextAlert>>(emptyList())
    val alerts: StateFlow<List<TextAlert>> = _alerts

    fun setAlerts(items: List<TextAlert>) {
        _alerts.value = items
            .distinctBy { it.textAlertId }
            .sortedByDescending { it.createdAt }
    }

    fun addAlert(alert: TextAlert) {
        _alerts.update { current ->
            listOf(alert)
                .plus(current)
                .distinctBy { it.textAlertId }
                .sortedByDescending { it.createdAt }
        }
    }

    fun clear() {
        _alerts.value = emptyList()
    }
}