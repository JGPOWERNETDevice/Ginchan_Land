package net.jgpower.gichan_land.data.common

data class ApiResponse<T>(
    val success: Boolean,
    val data: T?,
    val message: String?
)