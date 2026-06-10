package net.jgpower.gichan_land.data.auth

data class WorkerUser(
    val workerId: String,
    val name: String,
    val position: String?,
    val areaGroup: String?,
    val active: Boolean
)