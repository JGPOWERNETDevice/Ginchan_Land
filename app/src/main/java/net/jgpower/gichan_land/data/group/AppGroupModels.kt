package net.jgpower.gichan_land.data.group

data class AppGroupItem(
    val groupCode: String,
    val groupName: String,
    val isActive: Boolean = true
)

data class MyGroupsData(
    val workerId: String,
    val groups: List<AppGroupItem> = emptyList()
)

data class MyGroupsResponse(
    val success: Boolean,
    val data: MyGroupsData? = null,
    val message: String? = null
)

data class AppGroupPatchRequest(
    val workerId: String,
    val addGroupCodes: List<String> = emptyList(),
    val removeGroupCodes: List<String> = emptyList()
)

data class AppGroupPatchResult(
    val workerId: String,
    val addedGroupCodes: List<String> = emptyList(),
    val removedGroupCodes: List<String> = emptyList(),
    val invalidGroupCodes: List<String> = emptyList(),
    val groups: List<AppGroupItem> = emptyList()
)

data class AppGroupPatchResponse(
    val success: Boolean,
    val data: AppGroupPatchResult? = null,
    val message: String? = null
)
