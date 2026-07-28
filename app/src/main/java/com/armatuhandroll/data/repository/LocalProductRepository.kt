package com.armatuhandroll.data.repository

import com.armatuhandroll.data.local.ProductCatalog
import com.armatuhandroll.domain.model.IngredientCustomization
import com.armatuhandroll.domain.model.Product
import com.armatuhandroll.domain.model.ProductCustomizationConfig
import com.armatuhandroll.domain.repository.ProductRepository

internal class LocalProductRepository : ProductRepository {
    override fun getProducts(): List<Product> = ProductCatalog.products

    override fun getProductById(productId: Int?): Product? =
        ProductCatalog.products.firstOrNull { it.id == productId }

    override fun getProteinOptions(): List<String> = ProductCatalog.proteinOptions

    override fun getBaseOptions(): List<String> = ProductCatalog.baseOptions

    override fun getVegetableOptions(): List<String> = ProductCatalog.vegetableOptions

    override fun getCustomizationConfig(productName: String): ProductCustomizationConfig? =
        ProductCatalog.customizableProductsConfig[productName]

    override fun isCustomizable(productName: String): Boolean =
        ProductCatalog.customizableProductsConfig.containsKey(productName)

    override fun hasIncludedRemovableBases(productName: String): Boolean =
        ProductCatalog.hasIncludedRemovableBases(productName)

    override fun getFixedIngredients(
        product: Product,
        customization: IngredientCustomization
    ): List<String> = ProductCatalog.fixedIngredientsFor(product, customization)
}
