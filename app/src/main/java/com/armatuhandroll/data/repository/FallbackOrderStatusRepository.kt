package com.armatuhandroll.data.repository

import com.armatuhandroll.domain.model.OrderStatus
import com.armatuhandroll.domain.repository.OrderStatusRepository

internal class FallbackOrderStatusRepository(
    private val remoteRepository: OrderStatusRepository,
    private val localRepository: OrderStatusRepository
) : OrderStatusRepository {

    override suspend fun getStatus(
        orderNumber: String
    ): Result<OrderStatus> {
        val remoteResult = try {
            remoteRepository.getStatus(orderNumber)
        } catch (exception: Exception) {
            Result.failure(exception)
        }

        if (remoteResult.isSuccess) {
            return remoteResult
        }

        return try {
            localRepository.getStatus(orderNumber)
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }
}
