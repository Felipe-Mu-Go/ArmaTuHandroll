package com.armatuhandroll.domain.repository

import com.armatuhandroll.domain.model.OrderStatus

internal interface OrderStatusRepository {

    suspend fun getStatus(
        orderNumber: String
    ): Result<OrderStatus>
}
