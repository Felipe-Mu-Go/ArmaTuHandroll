package com.armatuhandroll.domain.model

data class IngredientCustomization(
    val proteins: List<String>,
    val bases: List<String>,
    val vegetables: List<String>,
    val chargeBaseExtras: Boolean = true
) {
    val proteinExtra: Int
        get() = (proteins.size - 1).coerceAtLeast(0) * 1000

    val baseExtra: Int
        get() = if (chargeBaseExtras) (bases.size - 1).coerceAtLeast(0) * 1000 else 0

    val vegetableExtra: Int
        get() = (vegetables.size - 1).coerceAtLeast(0) * 500

    val totalExtra: Int
        get() = proteinExtra + baseExtra + vegetableExtra
}
