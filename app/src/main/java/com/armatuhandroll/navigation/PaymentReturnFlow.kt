package com.armatuhandroll.navigation

import com.armatuhandroll.domain.model.OrderStatusUpdate
import com.armatuhandroll.domain.repository.OrderStatusRepository

internal data class PaymentReturnRequest(val orderNumber: String = "")

internal fun correlatePaymentReturnOrder(
    requestedOrderNumber: String,
    recoveryOrderNumber: String
): Result<String> {
    val expected = recoveryOrderNumber.trim()
    if (expected.isEmpty()) {
        return Result.failure(IllegalStateException("No hay un pago Webpay pendiente en este dispositivo"))
    }
    val requested = requestedOrderNumber.trim()
    if (requested.isNotEmpty() && requested != expected) {
        return Result.failure(IllegalArgumentException("El retorno no corresponde al pago pendiente"))
    }
    return Result.success(expected)
}

internal fun validateWebpayStartOrder(
    requestedOrderNumber: String,
    pendingWebpayOrderNumber: String
): Result<Unit> {
    val requested = requestedOrderNumber.trim()
    val pending = pendingWebpayOrderNumber.trim()
    if (requested.isEmpty()) {
        return Result.failure(IllegalArgumentException("No se pudo identificar el pedido"))
    }
    if (pending.isNotEmpty() && pending != requested) {
        return Result.failure(
            IllegalStateException("Ya existe otro pago Webpay pendiente. Finalízalo antes de iniciar uno nuevo.")
        )
    }
    return Result.success(Unit)
}

internal fun isPendingWebpayOrder(
    orderNumber: String,
    pendingWebpayOrderNumber: String
): Boolean = orderNumber.trim().isNotEmpty() &&
    orderNumber.trim() == pendingWebpayOrderNumber.trim()

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
        return orderStatusRepository.getStatus(orderNumber).mapCatching { update ->
            if (
                update.paymentStatus.trim().equals("confirmed", ignoreCase = true) &&
                !update.paymentMethod.trim().equals("webpay", ignoreCase = true)
            ) {
                throw IllegalStateException("El pedido no está confirmado mediante Webpay")
            }
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
