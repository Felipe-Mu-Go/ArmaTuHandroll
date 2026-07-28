package com.armatuhandroll.data.local

import com.armatuhandroll.domain.model.IngredientCustomization
import com.armatuhandroll.domain.model.Product
import com.armatuhandroll.domain.model.ProductCustomizationConfig

internal object ProductCatalog {
    val products = listOf(
        Product(
            id = 1,
            name = "Handroll",
            price = 3500,
            description = "Incluye hasta 1 proteína, 1 base y 1 vegetal sin costo extra. " +
                "Proteína o base extra +$1.000. Vegetal extra +$500."
        ),
        Product(
            id = 2,
            name = "SushiBurger",
            price = 5500,
            description = "Incluye arroz, nori, palta y queso crema. " +
                "Puedes quitar palta o queso crema sin costo, y elegir proteínas y vegetales."
        ),
        Product(
            id = 3,
            name = "SushiPleto",
            price = 5000,
            description = "Incluye palta y queso crema en la base. " +
                "Puedes quitar una o ambas sin costo y personalizar proteínas y vegetales."
        ),
        Product(
            id = 4,
            name = "Gohan",
            price = 6500,
            description = "Incluye arroz en la base fija. " +
                "La palta y el queso crema vienen incluidos y son opcionales. " +
                "Personaliza proteínas y vegetales con el mismo cálculo de extras."
        )
    )

    val proteinOptions = listOf("Camarón", "Carne", "Kanikama", "Palmito", "Champiñón", "Pollo")
    val baseOptions = listOf("Palta", "Queso crema")
    val vegetableOptions = listOf("Cebollín", "Ciboulette", "Choclo")
    private val productsWithIncludedRemovableBases = setOf("SushiBurger", "SushiPleto", "Gohan")

    val customizableProductsConfig = mapOf(
        "Handroll" to ProductCustomizationConfig(),
        "SushiBurger" to ProductCustomizationConfig(),
        "SushiPleto" to ProductCustomizationConfig(),
        "Gohan" to ProductCustomizationConfig(fixedIngredients = listOf("Arroz"))
    )

    fun hasIncludedRemovableBases(productName: String): Boolean =
        productName in productsWithIncludedRemovableBases

    fun fixedIngredientsFor(
        product: Product,
        customization: IngredientCustomization
    ): List<String> {
        return customizableProductsConfig[product.name]?.fixedIngredients.orEmpty()
    }
}
