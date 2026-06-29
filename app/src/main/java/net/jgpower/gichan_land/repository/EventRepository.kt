package net.jgpower.gichan_land.repository

import net.jgpower.gichan_land.data.area.AreaTypeItem
import net.jgpower.gichan_land.data.event.EventCreateRequest
import net.jgpower.gichan_land.data.event.EventTypeItem
import net.jgpower.gichan_land.network.ApiService
import net.jgpower.gichan_land.network.ApiServiceManager
import net.jgpower.gichan_land.network.ServerConfig
import retrofit2.Response
import java.io.IOException

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
    ) = createSosEventOnAvailableNetwork(
        EventCreateRequest(
            eventType = "sos",
            areaCode = null,
            targetType = null,
            workerId = workerId
        )
    )

    private suspend fun createSosEventOnAvailableNetwork(
        request: EventCreateRequest
    ): Response<net.jgpower.gichan_land.data.common.ApiResponse<Unit>> {
        return try {
            val firstResponse = apiService.createEvent(request)

            // 400/500 같은 서버 응답은 연결 실패가 아니므로 그대로 화면에 전달합니다.
            // 그래야 Node-RED/DB 오류 메시지가 내부망 재시도 실패에 가려지지 않습니다.
            firstResponse
        } catch (e: IOException) {
            // 실제 네트워크 연결 실패, 타임아웃일 때만 반대망으로 재시도합니다.
            createSosEventWithFallback(request)
        }
    }

    private suspend fun createSosEventWithFallback(
        request: EventCreateRequest
    ): Response<net.jgpower.gichan_land.data.common.ApiResponse<Unit>> {
        return if (ApiServiceManager.getCurrentBaseUrl() == ServerConfig.getPublicBaseHttpUrl()) {
            ApiServiceManager.localApiService.createEvent(request)
        } else {
            ApiServiceManager.publicApiService.createEvent(request)
        }
    }
}
