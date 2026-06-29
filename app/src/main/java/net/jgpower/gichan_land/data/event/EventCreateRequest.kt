package net.jgpower.gichan_land.data.event

data class EventCreateRequest(
    val eventType: String,
    val areaCode: String?,
    val targetType: String?,
    val workerId: String
)