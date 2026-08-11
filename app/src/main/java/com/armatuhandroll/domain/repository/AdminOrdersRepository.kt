package com.armatuhandroll.domain.repository

import com.armatuhandroll.domain.model.AdminOrder
import com.armatuhandroll.domain.model.OrderStatus

internal interface AdminOrdersRepository {
    suspend fun getOrders(): Result<List<AdminOrder>>
    suspend fun updateOrderStatus(orderNumber: String, newStatus: OrderStatus): Result<OrderStatus>
}
