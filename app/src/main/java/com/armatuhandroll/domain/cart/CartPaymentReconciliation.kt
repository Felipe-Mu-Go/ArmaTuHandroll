package com.armatuhandroll.domain.cart

import com.armatuhandroll.domain.model.CartItem

internal fun remainingCartItemsAfterPayment(
    currentItems: List<CartItem>,
    paidItems: List<CartItem>
): List<CartItem> {
    val paidQuantities = paidItems
        .groupingBy { it.copy(quantity = 1) }
        .fold(0) { total, item -> total + item.quantity }
        .toMutableMap()

    return buildList {
        currentItems.forEach { currentItem ->
            val key = currentItem.copy(quantity = 1)
            val paidQuantity = paidQuantities[key] ?: 0
            val removedQuantity = minOf(currentItem.quantity, paidQuantity)
            val remainingQuantity = currentItem.quantity - removedQuantity
            paidQuantities[key] = paidQuantity - removedQuantity
            if (remainingQuantity > 0) {
                add(currentItem.copy(quantity = remainingQuantity))
            }
        }
    }
}
