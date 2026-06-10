package net.jgpower.gichan_land.network

import net.jgpower.gichan_land.data.alert.AlertStatusUpdateRequest
import net.jgpower.gichan_land.data.alert.WorkerAlert
import net.jgpower.gichan_land.data.auth.LoginRequest
import net.jgpower.gichan_land.data.auth.LoginResponse
import net.jgpower.gichan_land.data.common.ApiResponse
import net.jgpower.gichan_land.data.event.EventCreateRequest
import net.jgpower.gichan_land.data.auth.LogoutRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import net.jgpower.gichan_land.data.alert.AlertStartRequest
import net.jgpower.gichan_land.data.alert.AlertCompleteRequest

import net.jgpower.gichan_land.data.emergency.EmergencyCallRequest
import net.jgpower.gichan_land.data.emergency.EmergencyCallResponse
import net.jgpower.gichan_land.data.emergency.PresenceStatusResponse
import net.jgpower.gichan_land.data.event.EventTypeItem
import net.jgpower.gichan_land.data.textalert.TextAlertListResponse
import net.jgpower.gichan_land.data.area.AreaTypeItem
import net.jgpower.gichan_land.data.walkie.OnlineWorkerDto



interface ApiService {

    @POST("api/app/login")
    suspend fun login(
        @Body request: LoginRequest
    ): Response<LoginResponse>

    @GET("api/app/alerts")
    suspend fun getMyAlerts(
        @Query("workerId") workerId: String
    ): Response<List<WorkerAlert>>

    @GET("api/app/alerts/{alertId}")
    suspend fun getAlertDetail(
        @Path("alertId") alertId: String
    ): Response<WorkerAlert>

    @POST("api/app/alerts/status")
    suspend fun updateAlertStatus(
        @Body request: AlertStatusUpdateRequest
    ): Response<ApiResponse<WorkerAlert>>

    @POST("api/app/events")
    suspend fun createEvent(
        @Body request: EventCreateRequest
    ): Response<ApiResponse<Unit>>

    @POST("api/app/logout")
    suspend fun logout(
        @Body request: LogoutRequest
    ): retrofit2.Response<ApiResponse<Unit>>

    @POST("api/app/alerts/start")
    suspend fun startAlert(
        @Body request: AlertStartRequest
    ): Response<ApiResponse<Unit>>

    @POST("api/app/alerts/complete")
    suspend fun completeAlert(
        @Body request: AlertCompleteRequest
    ): Response<Any>

    @GET("api/app/alerts")
    suspend fun getAlerts(
        @Query("workerId") workerId: String
    ): Response<List<WorkerAlert>>

    @GET("api/app/text-alerts")
    suspend fun getMyTextAlerts(
        @Query("workerId") workerId: String
    ): TextAlertListResponse

    @GET("api/app/presence-status")
    suspend fun getPresenceStatus(): PresenceStatusResponse

    @POST("api/emergency-alerts")
    suspend fun callEmergency(
        @Body request: EmergencyCallRequest
    ): EmergencyCallResponse

    @GET("api/event-types")
    suspend fun getEventTypes(): List<EventTypeItem>

    @GET("api/area-types")
    suspend fun getAreaTypes(): List<AreaTypeItem>

    @GET("api/monitor/online-workers")
    suspend fun getOnlineWorkers(): List<OnlineWorkerDto>

}