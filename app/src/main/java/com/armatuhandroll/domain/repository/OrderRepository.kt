package com.armatuhandroll.domain.repository

import com.armatuhandroll.domain.model.OrderRequest
import com.armatuhandroll.domain.model.WebpayTransaction

internal class WebpayCreationRejectedException(message: String) : IllegalStateException(message)

internal interface OrderRepository {
    suspend fun sendOrder(order: OrderRequest): Result<Unit>
    suspend fun reportTransfer(orderNumber: String): Result<Unit>
    suspend fun createWebpayTransaction(orderNumber: String): Result<WebpayTransaction>
}
