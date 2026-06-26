package net.jgpower.gichan_land.repository

import net.jgpower.gichan_land.data.group.AppGroupPatchRequest
import net.jgpower.gichan_land.data.group.AppGroupPatchResponse
import net.jgpower.gichan_land.data.group.MyGroupsResponse
import net.jgpower.gichan_land.network.ApiService
import org.json.JSONObject

class GroupRepository(
    private val apiService: ApiService
) {
    suspend fun getMyGroups(workerId: String): MyGroupsResponse {
        val response = apiService.getMyGroups(workerId)

        if (response.isSuccessful) {
            return response.body() ?: MyGroupsResponse(
                success = false,
                message = "그룹 목록 응답이 비어 있습니다."
            )
        }

        return MyGroupsResponse(
            success = false,
            message = extractErrorMessage(response.errorBody()?.string())
                ?: "그룹 목록 조회 실패 (${response.code()})"
        )
    }

    suspend fun addGroup(workerId: String, groupCode: String): AppGroupPatchResponse {
        return patchGroups(
            AppGroupPatchRequest(
                workerId = workerId,
                addGroupCodes = listOf(groupCode),
                removeGroupCodes = emptyList()
            )
        )
    }

    suspend fun removeGroup(workerId: String, groupCode: String): AppGroupPatchResponse {
        return patchGroups(
            AppGroupPatchRequest(
                workerId = workerId,
                addGroupCodes = emptyList(),
                removeGroupCodes = listOf(groupCode)
            )
        )
    }

    private suspend fun patchGroups(request: AppGroupPatchRequest): AppGroupPatchResponse {
        val response = apiService.patchMyGroups(request)

        if (response.isSuccessful) {
            return response.body() ?: AppGroupPatchResponse(
                success = false,
                message = "그룹 수정 응답이 비어 있습니다."
            )
        }

        return AppGroupPatchResponse(
            success = false,
            message = extractErrorMessage(response.errorBody()?.string())
                ?: "그룹 수정 실패 (${response.code()})"
        )
    }

    private fun extractErrorMessage(errorBody: String?): String? {
        if (errorBody.isNullOrBlank()) return null

        return runCatching {
            val json = JSONObject(errorBody)
            json.optString("message").takeIf { it.isNotBlank() }
        }.getOrNull()
    }
}
