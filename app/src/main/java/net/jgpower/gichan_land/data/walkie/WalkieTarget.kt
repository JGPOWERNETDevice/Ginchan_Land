package net.jgpower.gichan_land.data.walkie

enum class WalkieTargetType {
    USER,
    GROUP,
    ALL
}

data class WalkieTarget(
    val targetType: WalkieTargetType,
    val targetWorkerId: String? = null,
    val targetWorkerName: String? = null,
    val targetAreaGroup: String? = null
)