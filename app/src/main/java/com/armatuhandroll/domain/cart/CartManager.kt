package com.armatuhandroll.domain.cart

import androidx.compose.runtime.mutableStateListOf
import com.armatuhandroll.core.util.formatPrice
import com.armatuhandroll.data.local.ProductCatalog
import com.armatuhandroll.domain.model.CartItem
import com.armatuhandroll.domain.model.IngredientCustomization
import com.armatuhandroll.domain.model.Product

internal object CartManager {
    val items = mutableStateListOf<CartItem>()

    fun addProduct(product: Product, quantity: Int = 1) {
        items.add(CartItem(productId = product.id, name = product.name, unitPrice = product.price, quantity = quantity))
    }

    private fun customizedCartItem(
        product: Product,
        customization: IngredientCustomization,
        quantity: Int,
        fixedIngredients: List<String> = emptyList()
    ): CartItem {
        val finalPrice = product.price + customization.totalExtra
        val fixedIngredientsLine = if (fixedIngredients.isNotEmpty()) {
            listOf("Base fija: ${fixedIngredients.joinToString()}")
        } else {
            emptyList()
        }
        val baseDetailLines = if (ProductCatalog.hasIncludedRemovableBases(product.name)) {
            listOf(
                "Palta: ${if (customization.bases.contains("Palta")) "Con palta" else "Sin palta"}",
                "Queso crema: ${if (customization.bases.contains("Queso crema")) "Con queso crema" else "Sin queso crema"}"
            )
        } else {
            listOf("Bases: ${customization.bases.joinToString().ifEmpty { "Sin selección" }}")
        }
        val detailLines = fixedIngredientsLine + listOf(
            "Proteínas: ${customization.proteins.joinToString().ifEmpty { "Sin selección" }}"
        ) + baseDetailLines + listOf(
            "Vegetales: ${customization.vegetables.joinToString().ifEmpty { "Sin selección" }}",
            "Extra proteínas: ${formatPrice(customization.proteinExtra)}",
            "Extra bases: ${formatPrice(customization.baseExtra)}",
            "Extra vegetales: ${formatPrice(customization.vegetableExtra)}",
            "Total adicional: ${formatPrice(customization.totalExtra)}"
        )
        return CartItem(
            productId = product.id,
            name = product.name,
            unitPrice = finalPrice,
            quantity = quantity,
            customization = customization,
            fixedIngredients = fixedIngredients,
            details = detailLines
        )
    }

    fun addCustomizedProduct(
        product: Product,
        customization: IngredientCustomization,
        quantity: Int,
        fixedIngredients: List<String> = emptyList()
    ) {
        items.add(customizedCartItem(product, customization, quantity, fixedIngredients))
    }

    fun updateCustomizedProduct(
        index: Int,
        product: Product,
        customization: IngredientCustomization,
        quantity: Int,
        fixedIngredients: List<String> = emptyList()
    ) {
        if (index in items.indices) {
            items[index] = customizedCartItem(product, customization, quantity, fixedIngredients)
        }
    }

    fun total(): Int = items.sumOf { it.unitPrice * it.quantity }

    fun removeItem(index: Int) {
        if (index in items.indices) {
            items.removeAt(index)
        }
    }

    fun clear() {
        items.clear()
    }
}
