package net.jgpower.gichan_land.data.alert

data class AlertCompleteRequest(
    val alertId: String,
    val workerId: String,
    val actionContent: String
)