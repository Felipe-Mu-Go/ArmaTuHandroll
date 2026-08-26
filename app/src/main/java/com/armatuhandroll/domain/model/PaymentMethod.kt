package com.armatuhandroll.domain.model

internal enum class PaymentMethod(val storageValue: String, val displayName: String) {
    CASH("cash", "Efectivo"),
    TRANSFER("transfer", "Transferencia"),
    WEBPAY("webpay", "Webpay");

    companion object {
        fun fromStorageValue(value: String): PaymentMethod =
            values().firstOrNull { it.storageValue == value }
                ?: throw IllegalArgumentException("Método de pago desconocido: $value")
    }
}
