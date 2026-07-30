package com.armatuhandroll.data.repository

import com.armatuhandroll.domain.history.OrderHistoryManager
import com.armatuhandroll.domain.model.OrderStatus
import com.armatuhandroll.domain.repository.OrderStatusRepository

internal class LocalOrderStatusRepository : OrderStatusRepository {

    override suspend fun getStatus(
        orderNumber: String
    ): Result<OrderStatus> {
        val order = OrderHistoryManager.items.firstOrNull {
            it.orderNumber == orderNumber
        }

        return if (order != null) {
            Result.success(order.status)
        } else {
            Result.failure(
                NoSuchElementException(
                    "No se encontró el pedido solicitado"
                )
            )
        }
    }
}
