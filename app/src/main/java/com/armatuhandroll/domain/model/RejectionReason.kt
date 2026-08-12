package com.armatuhandroll.domain.model

internal enum class RejectionReason(val storageValue: String, val displayName: String) {
    OUT_OF_STOCK("out_of_stock", "Producto sin stock"),
    STORE_CLOSED("store_closed", "Local cerrado"),
    HIGH_DEMAND("high_demand", "Alta demanda / tiempo de espera excesivo"),
    TECHNICAL_ISSUE("technical_issue", "Problema técnico"),
    INVALID_ORDER("invalid_order", "Problema con el pedido"),
    OTHER("other", "Otro motivo");

    companion object {
        fun fromStorageValue(value: String): RejectionReason? =
            values().firstOrNull { it.storageValue == value.trim() }
    }
}
