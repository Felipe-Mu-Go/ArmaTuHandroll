package com.armatuhandroll.domain.repository

import com.armatuhandroll.domain.model.AdminOrder
import com.armatuhandroll.domain.model.AdminPayment
import com.armatuhandroll.domain.model.OrderStatus
import com.armatuhandroll.domain.model.PaymentMethod

internal interface AdminOrdersRepository {
    suspend fun getOrders(): Result<List<AdminOrder>>
    suspend fun getPayments(): Result<List<AdminPayment>>
    suspend fun registerPayment(orderNumber: String, paymentMethod: PaymentMethod): Result<AdminPayment>
    suspend fun updateOrderStatus(orderNumber: String, newStatus: OrderStatus): Result<OrderStatus>
}
