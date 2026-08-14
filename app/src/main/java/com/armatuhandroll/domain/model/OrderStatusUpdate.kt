package com.armatuhandroll.domain.model

internal data class OrderStatusUpdate(
    val status: OrderStatus,
    val rejectionReason: RejectionReason? = null,
    val rejectionDetail: String = "",
    val paymentStatus: String = "pending",
    val paymentMethod: String = ""
)
