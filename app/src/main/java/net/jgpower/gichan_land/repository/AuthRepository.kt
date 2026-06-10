package net.jgpower.gichan_land.repository

import net.jgpower.gichan_land.data.auth.LoginRequest
import net.jgpower.gichan_land.data.auth.LogoutRequest
import net.jgpower.gichan_land.network.ApiService

class AuthRepository(
    private val apiService: ApiService
) {
    suspend fun login(workerId: String, password: String) =
        apiService.login(
            LoginRequest(
                workerId = workerId,
                password = password
            )
        )

    suspend fun logout(workerId: String) =
        apiService.logout(
            LogoutRequest(
                workerId = workerId
            )
        )
}