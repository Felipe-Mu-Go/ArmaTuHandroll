package com.armatuhandroll.domain.repository

import com.armatuhandroll.domain.model.OrderStatusUpdate

internal interface OrderStatusRepository {

    suspend fun getStatus(
        orderNumber: String
    ): Result<OrderStatusUpdate>
}
