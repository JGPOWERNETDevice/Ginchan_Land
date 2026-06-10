package net.jgpower.gichan_land.data.emergency

data class PresenceStatusResponse(
    val success: Boolean,
    val data: PresenceStatusData?,
    val message: String
)

data class PresenceStatusData(
    val rescueStatus: String?,
    val rescueUpdatedAt: String?,
    val policeStatus: String?,
    val policeUpdatedAt: String?
)

data class EmergencyCallRequest(
    val eventId: String,
    val type: String
)

data class EmergencyCallData(
    val alertId: String,
    val eventId: String,
    val receiverId: String,
    val receiveType: String,
    val targetType: String?,
    val message: String,
    val status: String,
    val occurredAt: String
)

data class EmergencyCallResponse(
    val success: Boolean,
    val data: EmergencyCallData?,
    val message: String
)