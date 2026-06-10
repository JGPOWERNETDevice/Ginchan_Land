package net.jgpower.gichan_land.data.alert

data class WorkerAlert(
    val alertId: String,
    val eventId: String,
    val receiverId: String,
    val receiveType: String,
    val targetType: String?,
    val message: String,
    val occurredAt: String,
    val status: String
)