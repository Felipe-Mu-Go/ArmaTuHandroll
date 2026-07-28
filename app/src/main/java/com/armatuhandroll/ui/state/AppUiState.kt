package com.armatuhandroll.ui.state

import com.armatuhandroll.domain.model.IngredientCustomization
import com.armatuhandroll.domain.model.Product

internal data class AppUiState(
    val pendingCustomization: IngredientCustomization? = null,
    val pendingProduct: Product? = null,
    val pendingQuantity: Int = 0,
    val pendingEditIndex: Int? = null,
    val pendingOrderTotal: Int = 0,
    val pendingOrderItemCount: Int = 0,
    val pendingOrderNumber: String = "",
    val pendingOrderProducts: String = "",
    val pendingOrderUsername: String = ""
)
