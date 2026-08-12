package com.armatuhandroll.domain.model

internal data class AdminOrder(
    val orderNumber: String,
    val dateTime: String,
    val products: String,
    val totalQuantity: Int,
    val totalPaid: Int,
    val estimatedTime: String,
    val customerName: String,
    val status: OrderStatus,
    val paymentStatus: String,
    val paymentMethod: String,
    val paidAmount: Int
)
