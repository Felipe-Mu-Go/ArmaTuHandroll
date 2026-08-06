package com.armatuhandroll.domain.model

internal enum class OrderStatus(
    val storageValue: String,
    val displayName: String
) {
    PENDING_REVIEW(
        storageValue = "pending_review",
        displayName = "Pendiente de revisión"
    ),
    ACCEPTED(
        storageValue = "accepted",
        displayName = "Aceptado"
    ),
    PENDING_PAYMENT(
        storageValue = "pending_payment",
        displayName = "Pendiente de pago"
    ),
    PAYMENT_REPORTED(
        storageValue = "payment_reported",
        displayName = "Pago informado"
    ),
    PAYMENT_CONFIRMED(
        storageValue = "payment_confirmed",
        displayName = "Pago confirmado"
    ),
    PREPARING(
        storageValue = "preparing",
        displayName = "En preparación"
    ),
    READY_FOR_PICKUP(
        storageValue = "ready_for_pickup",
        displayName = "Listo para retirar"
    ),
    DELIVERED(
        storageValue = "delivered",
        displayName = "Entregado"
    ),
    CANCELLED(
        storageValue = "cancelled",
        displayName = "Cancelado"
    );

    companion object {
        fun fromStorageValue(value: String): OrderStatus {
            return values().firstOrNull {
                it.storageValue == value
            } ?: fromLegacyValue(value)
        }

        private fun fromLegacyValue(value: String): OrderStatus {
            return values().firstOrNull {
                it.displayName.equals(value, ignoreCase = true)
            } ?: PENDING_REVIEW
        }
    }
}
