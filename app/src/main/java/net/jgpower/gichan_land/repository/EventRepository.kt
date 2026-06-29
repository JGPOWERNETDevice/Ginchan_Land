package net.jgpower.gichan_land.repository

import net.jgpower.gichan_land.data.area.AreaTypeItem
import net.jgpower.gichan_land.data.event.EventCreateRequest
import net.jgpower.gichan_land.data.event.EventTypeItem
import net.jgpower.gichan_land.network.ApiService

class EventRepository(
    private val apiService: ApiService
) {
    suspend fun getEventTypes(): List<EventTypeItem> {
        return apiService.getEventTypes()
    }

    suspend fun getAreaTypes(): List<AreaTypeItem> {
        return apiService.getAreaTypes()
    }

    suspend fun createEvent(
        eventType: String,
        areaCode: String?,
        targetType: String?,
        workerId: String
    ) = apiService.createEvent(
        EventCreateRequest(
            eventType = eventType,
            areaCode = areaCode,
            targetType = targetType,
            workerId = workerId
        )
    )

    suspend fun createSosEvent(
        workerId: String
    ) = apiService.createEvent(
        EventCreateRequest(
            eventType = "sos",
            areaCode = null,
            targetType = null,
            workerId = workerId
        )
    )
}
