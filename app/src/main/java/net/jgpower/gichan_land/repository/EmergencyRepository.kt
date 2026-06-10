package net.jgpower.gichan_land.repository

import net.jgpower.gichan_land.data.emergency.EmergencyCallRequest
import net.jgpower.gichan_land.data.emergency.EmergencyCallResponse
import net.jgpower.gichan_land.data.emergency.PresenceStatusResponse
import net.jgpower.gichan_land.network.ApiService

class EmergencyRepository(
    private val apiService: ApiService
) {
    suspend fun getPresenceStatus(): PresenceStatusResponse {
        return apiService.getPresenceStatus()
    }

    suspend fun callEmergency(
        eventId: String,
        type: String
    ): EmergencyCallResponse {
        return apiService.callEmergency(
            EmergencyCallRequest(
                eventId = eventId,
                type = type
            )
        )
    }
}