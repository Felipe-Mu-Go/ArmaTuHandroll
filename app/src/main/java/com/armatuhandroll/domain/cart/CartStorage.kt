package com.armatuhandroll.domain.cart

import com.armatuhandroll.domain.model.CartItem

internal interface CartStorage {
    fun loadItems(): List<CartItem>

    fun saveItems(items: List<CartItem>)

    fun clear()
}
