package com.armatuhandroll.data.repository

import com.armatuhandroll.domain.history.OrderHistoryManager
import com.armatuhandroll.domain.model.OrderStatusUpdate
import com.armatuhandroll.domain.repository.OrderStatusRepository

internal class LocalOrderStatusRepository : OrderStatusRepository {

    override suspend fun getStatus(
        orderNumber: String
    ): Result<OrderStatusUpdate> {
        val order = OrderHistoryManager.items.firstOrNull {
            it.orderNumber == orderNumber
        }

        return if (order != null) {
            Result.success(OrderStatusUpdate(order.status, order.rejectionReason, order.rejectionDetail))
        } else {
            Result.failure(
                NoSuchElementException(
                    "No se encontró el pedido solicitado"
                )
            )
        }
    }
}
