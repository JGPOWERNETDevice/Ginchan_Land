package net.jgpower.gichan_land.data.auth

data class LoginResponse(
    val success: Boolean,
    val worker: WorkerUser?,
    val token: String?,
    val message: String?
)