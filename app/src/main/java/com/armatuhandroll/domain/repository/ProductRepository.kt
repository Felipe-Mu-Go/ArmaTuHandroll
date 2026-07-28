package com.armatuhandroll.domain.repository

import com.armatuhandroll.domain.model.IngredientCustomization
import com.armatuhandroll.domain.model.Product
import com.armatuhandroll.domain.model.ProductCustomizationConfig

internal interface ProductRepository {
    fun getProducts(): List<Product>

    fun getProductById(productId: Int?): Product?

    fun getProteinOptions(): List<String>

    fun getBaseOptions(): List<String>

    fun getVegetableOptions(): List<String>

    fun getCustomizationConfig(productName: String): ProductCustomizationConfig?

    fun isCustomizable(productName: String): Boolean

    fun hasIncludedRemovableBases(productName: String): Boolean

    fun getFixedIngredients(
        product: Product,
        customization: IngredientCustomization
    ): List<String>
}
