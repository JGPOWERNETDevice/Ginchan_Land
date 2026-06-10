package net.jgpower.gichan_land.repository

import net.jgpower.gichan_land.data.alert.AlertCompleteRequest
import net.jgpower.gichan_land.data.alert.AlertStartRequest
import net.jgpower.gichan_land.network.ApiService

class AlertRepository(
    private val apiService: ApiService
) {
    suspend fun getMyAlerts(workerId: String) =
        apiService.getAlerts(workerId)

    suspend fun getAlertDetail(alertId: String) =
        apiService.getAlertDetail(alertId)

    suspend fun startAlert(
        alertId: String,
        workerId: String
    ) = apiService.startAlert(
        AlertStartRequest(
            alertId = alertId,
            workerId = workerId
        )
    )

    suspend fun completeAlert(
        alertId: String,
        workerId: String,
        actionContent: String
    ) = apiService.completeAlert(
        AlertCompleteRequest(
            alertId = alertId,
            workerId = workerId,
            actionContent = actionContent
        )
    )
}