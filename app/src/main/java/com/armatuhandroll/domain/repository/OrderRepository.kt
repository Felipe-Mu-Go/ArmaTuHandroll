package com.armatuhandroll.domain.repository

import com.armatuhandroll.domain.model.OrderRequest

internal interface OrderRepository {
    suspend fun sendOrder(order: OrderRequest): Result<Unit>
    suspend fun reportTransfer(orderNumber: String): Result<Unit>
}
