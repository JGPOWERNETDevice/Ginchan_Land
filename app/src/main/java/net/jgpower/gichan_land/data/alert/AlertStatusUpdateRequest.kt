package net.jgpower.gichan_land.data.alert

data class AlertStatusUpdateRequest(
    val alertId: String,
    val workerId: String,
    val status: AlertStatus,
    val resultMessage: String?,
    val updatedAtMs: Long = System.currentTimeMillis()
)