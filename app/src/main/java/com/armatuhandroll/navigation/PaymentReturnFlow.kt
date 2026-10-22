package com.armatuhandroll.navigation

import com.armatuhandroll.domain.model.OrderStatusUpdate
import com.armatuhandroll.domain.repository.OrderStatusRepository

internal data class PaymentReturnRequest(val orderNumber: String = "")

internal enum class PaymentReturnOutcome {
    CONFIRMED,
    PENDING,
    CANCELLED,
    FAILED
}

internal data class PaymentReturnResolution(
    val update: OrderStatusUpdate,
    val outcome: PaymentReturnOutcome
) {
    val clearsCart: Boolean get() = outcome == PaymentReturnOutcome.CONFIRMED
    val clearsCheckoutBackStack: Boolean get() = outcome == PaymentReturnOutcome.CONFIRMED
    val allowsControlledRetry: Boolean
        get() = outcome == PaymentReturnOutcome.CANCELLED || outcome == PaymentReturnOutcome.FAILED
}

internal class PaymentReturnCoordinator(
    private val orderStatusRepository: OrderStatusRepository
) {
    suspend fun resolve(orderNumber: String): Result<PaymentReturnResolution> {
        if (orderNumber.isBlank()) {
            return Result.failure(IllegalArgumentException("No se pudo identificar el pedido"))
        }
        return orderStatusRepository.getStatus(orderNumber).map { update ->
            PaymentReturnResolution(
                update = update,
                outcome = when (update.paymentStatus.trim().lowercase()) {
                    "confirmed" -> PaymentReturnOutcome.CONFIRMED
                    "cancelled" -> PaymentReturnOutcome.CANCELLED
                    "failed", "reversed", "nullified" -> PaymentReturnOutcome.FAILED
                    else -> PaymentReturnOutcome.PENDING
                }
            )
        }
    }
}
