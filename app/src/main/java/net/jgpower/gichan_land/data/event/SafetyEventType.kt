package net.jgpower.gichan_land.data.event

enum class SafetyEventType {
    DANGEROUS_BEHAVIOR,
    DENSITY_EXCEEDED,
    RESTRICTED_AREA_ACCESS
}

fun SafetyEventType.toDisplayName(): String {
    return when (this) {
        SafetyEventType.DANGEROUS_BEHAVIOR -> "위험행동"
        SafetyEventType.DENSITY_EXCEEDED -> "밀집도 초과"
        SafetyEventType.RESTRICTED_AREA_ACCESS -> "위험 구역 접근"
    }
}