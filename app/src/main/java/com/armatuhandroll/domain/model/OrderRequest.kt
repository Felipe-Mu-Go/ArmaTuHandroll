package com.armatuhandroll.domain.model

internal data class OrderRequest(
    val orderNumber: String,
    val products: String,
    val quantityTotal: Int,
    val totalPaid: Int,
    val estimatedTime: String,
    val username: String,
    val fcmToken: String?
)
