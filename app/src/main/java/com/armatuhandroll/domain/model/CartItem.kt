package com.armatuhandroll.domain.model

data class CartItem(
    val productId: Int,
    val name: String,
    val unitPrice: Int,
    val quantity: Int = 1,
    val customization: IngredientCustomization? = null,
    val fixedIngredients: List<String> = emptyList(),
    val details: List<String> = emptyList()
)
