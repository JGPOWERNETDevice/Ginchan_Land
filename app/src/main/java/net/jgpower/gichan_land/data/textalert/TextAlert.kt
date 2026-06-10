package net.jgpower.gichan_land.data.textalert

data class TextAlert(
    val textAlertId: String,
    val receiverId: String,
    val receiveType: String,
    val message: String,
    val createdAt: String
)

data class TextAlertListResponse(
    val success: Boolean,
    val data: List<TextAlert>,
    val message: String
)