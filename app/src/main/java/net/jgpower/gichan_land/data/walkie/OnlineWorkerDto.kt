package net.jgpower.gichan_land.data.walkie

data class OnlineWorkerGroupDto(
    val groupCode: String?,
    val groupName: String?,
    val isActive: Boolean?
)

data class OnlineWorkerDto(
    val name: String?,
    val workerId: String?,
    val position: String?,
    val areaGroup: String?,
    val groups: List<OnlineWorkerGroupDto>?,
    val isLoggedIn: Boolean?,
    val isAppActive: Boolean?,
    val isWsConnected: Boolean?,
    val lastLoginAt: String?
) {
    fun groupItems(): List<OnlineWorkerGroupDto> {
        val apiGroups = groups
            .orEmpty()
            .filter {
                !it.groupCode.isNullOrBlank() || !it.groupName.isNullOrBlank()
            }

        if (apiGroups.isNotEmpty()) {
            return apiGroups
        }

        return areaGroup
            ?.split(",")
            ?.mapNotNull { raw ->
                val name = raw.trim()
                if (name.isBlank()) {
                    null
                } else {
                    OnlineWorkerGroupDto(
                        groupCode = name,
                        groupName = name,
                        isActive = null
                    )
                }
            }
            .orEmpty()
    }

    fun groupNamesText(): String {
        return groupItems()
            .mapNotNull { group ->
                group.groupName?.trim()?.takeIf { it.isNotBlank() }
                    ?: group.groupCode?.trim()?.takeIf { it.isNotBlank() }
            }
            .distinct()
            .joinToString(", ")
    }

    fun primaryGroupName(): String? {
        return groupItems()
            .firstOrNull()
            ?.let { group ->
                group.groupName?.trim()?.takeIf { it.isNotBlank() }
                    ?: group.groupCode?.trim()?.takeIf { it.isNotBlank() }
            }
    }

    fun belongsToGroup(
        groupCode: String?,
        groupName: String?
    ): Boolean {
        val code = groupCode?.trim()
        val name = groupName?.trim()

        return groupItems().any { group ->
            val workerGroupCode = group.groupCode?.trim()
            val workerGroupName = group.groupName?.trim()

            (!code.isNullOrBlank() && workerGroupCode == code) ||
                    (!name.isNullOrBlank() && workerGroupName == name) ||
                    (!code.isNullOrBlank() && workerGroupName == code) ||
                    (!name.isNullOrBlank() && workerGroupCode == name)
        }
    }
}
