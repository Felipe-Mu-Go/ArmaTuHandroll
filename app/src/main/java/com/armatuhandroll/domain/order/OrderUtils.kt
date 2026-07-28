package com.armatuhandroll.domain.order

import com.armatuhandroll.domain.model.CartItem
import kotlin.random.Random

internal fun generateOrderNumber(): String {
    val randomCode = Random.nextInt(10000, 100000)
    return "PED-$randomCode"
}

internal fun formatProductsForSheet(items: List<CartItem>): String {
    return items.joinToString(separator = " | ") { item ->
        "${item.name} x${item.quantity}"
    }
}
