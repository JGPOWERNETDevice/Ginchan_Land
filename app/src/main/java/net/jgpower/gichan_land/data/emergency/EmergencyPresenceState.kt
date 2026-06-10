package net.jgpower.gichan_land.data.emergency

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

data class EmergencyPresenceUiState(
    val rescueStatus: String = "-",
    val rescueUpdatedAt: String = "",
    val policeStatus: String = "-",
    val policeUpdatedAt: String = ""
)

object EmergencyPresenceState {
    private val _state = MutableStateFlow(EmergencyPresenceUiState())
    val state: StateFlow<EmergencyPresenceUiState> = _state

    fun update(
        rescueStatus: String?,
        rescueUpdatedAt: String?,
        policeStatus: String?,
        policeUpdatedAt: String?
    ) {
        _state.update { current ->
            current.copy(
                rescueStatus = rescueStatus?.takeIf { it.isNotBlank() } ?: current.rescueStatus,
                rescueUpdatedAt = rescueUpdatedAt?.takeIf { it.isNotBlank() } ?: current.rescueUpdatedAt,
                policeStatus = policeStatus?.takeIf { it.isNotBlank() } ?: current.policeStatus,
                policeUpdatedAt = policeUpdatedAt?.takeIf { it.isNotBlank() } ?: current.policeUpdatedAt
            )
        }
    }
}