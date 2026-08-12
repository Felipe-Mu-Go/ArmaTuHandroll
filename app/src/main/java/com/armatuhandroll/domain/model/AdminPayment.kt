package com.armatuhandroll.domain.model

internal data class AdminPayment(
    val paymentId: String,
    val orderNumber: String,
    val dateTime: String,
    val paymentMethod: PaymentMethod,
    val amount: Int,
    val paymentStatus: String,
    val isToday: Boolean
)
