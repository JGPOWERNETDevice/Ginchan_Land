package net.jgpower.gichan_land.repository

import net.jgpower.gichan_land.data.textalert.TextAlertListResponse
import net.jgpower.gichan_land.network.ApiService

class TextAlertRepository(
    private val apiService: ApiService
) {
    suspend fun getMyTextAlerts(workerId: String): TextAlertListResponse {
        return apiService.getMyTextAlerts(workerId)
    }
}