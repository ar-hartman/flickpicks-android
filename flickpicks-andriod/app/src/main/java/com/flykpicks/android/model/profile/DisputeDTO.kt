package com.flykpicks.android.model.profile

import com.flykpicks.android.model.enums.DisputeReason
import com.flykpicks.android.model.enums.DisputeStatus
import com.flykpicks.android.model.enums.ResolutionType
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class DisputeDTO(
    val id: String,
    val orderId: String,
    val reason: String,
    val description: String,
    val status: String,
    val desiredResolution: String,
    val createdAt: String,
    val resolvedAt: String?,
) {
    val disputeStatus: DisputeStatus get() = DisputeStatus.fromRaw(status) ?: DisputeStatus.OPEN
    val disputeReason: DisputeReason get() = DisputeReason.fromRaw(reason) ?: DisputeReason.OTHER
    val resolutionType: ResolutionType
        get() = ResolutionType.fromRaw(desiredResolution) ?: ResolutionType.FULL_REFUND
}

@JsonClass(generateAdapter = true)
data class DisputeDetailDTO(
    val id: String,
    val orderId: String,
    val reason: String,
    val description: String,
    val status: String,
    val desiredResolution: String,
    val createdAt: String,
    val resolvedAt: String?,
    /** Absent when the dispute has no photos. */
    val photoUrls: List<String> = emptyList(),
    val messages: List<DisputeMessageDTO>,
    val orderSummary: DisputeOrderSummary,
) {
    val disputeStatus: DisputeStatus get() = DisputeStatus.fromRaw(status) ?: DisputeStatus.OPEN
    val disputeReason: DisputeReason get() = DisputeReason.fromRaw(reason) ?: DisputeReason.OTHER
    val resolutionType: ResolutionType
        get() = ResolutionType.fromRaw(desiredResolution) ?: ResolutionType.FULL_REFUND
}

@JsonClass(generateAdapter = true)
data class DisputeMessageDTO(
    val id: String,
    val senderName: String,
    val senderRole: String,
    val content: String,
    val sentAt: String,
)

@JsonClass(generateAdapter = true)
data class DisputeOrderSummary(
    val orderId: String,
    val itemTitle: String,
    val itemThumbnailUrl: String?,
    val price: Double,
    val orderDate: String,
)

@JsonClass(generateAdapter = true)
data class FileDisputeRequest(
    val orderId: String,
    val reason: String,
    val description: String,
    val desiredResolution: String,
)

@JsonClass(generateAdapter = true)
data class SendDisputeMessageRequest(val content: String)
