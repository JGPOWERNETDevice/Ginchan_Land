package net.jgpower.gichan_land.data.walkie

data class OnlineWorkerDto(
    val name: String,
    val workerId: String,
    val position: String?,
    val areaGroup: String?,
    val isLoggedIn: Boolean?,
    val isAppActive: Boolean?,
    val isWsConnected: Boolean?,
    val lastLoginAt: String?
)