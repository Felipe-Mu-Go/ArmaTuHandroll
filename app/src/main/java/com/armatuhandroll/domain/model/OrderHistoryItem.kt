package com.armatuhandroll.domain.model

internal data class OrderHistoryItem(
    val orderNumber: String,
    val productsSummary: String,
    val quantityTotal: Int,
    val totalPaid: Int,
    val estimatedTimeMinutes: Int,
    val username: String,
    val createdAt: Long,
    val status: OrderStatus,
    val rejectionReason: RejectionReason? = null,
    val rejectionDetail: String = ""
)
