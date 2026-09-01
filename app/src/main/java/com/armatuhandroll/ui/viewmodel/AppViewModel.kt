package com.armatuhandroll.ui.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.armatuhandroll.domain.model.IngredientCustomization
import com.armatuhandroll.domain.model.Product
import com.armatuhandroll.ui.state.AppUiState
import java.util.UUID

internal class AppViewModel : ViewModel() {
    private val _uiState = mutableStateOf(AppUiState())

    val uiState: State<AppUiState>
        get() = _uiState

    fun startNewCustomization(product: Product) {
        _uiState.value = _uiState.value.copy(
            pendingCustomization = null,
            pendingProduct = product,
            pendingQuantity = 0,
            pendingEditIndex = null
        )
    }

    fun startEditingCustomization(
        index: Int,
        product: Product,
        customization: IngredientCustomization,
        quantity: Int
    ) {
        _uiState.value = _uiState.value.copy(
            pendingCustomization = customization,
            pendingProduct = product,
            pendingQuantity = quantity,
            pendingEditIndex = index
        )
    }

    fun setPendingCustomization(
        product: Product,
        customization: IngredientCustomization,
        quantity: Int,
        editIndex: Int?
    ) {
        _uiState.value = _uiState.value.copy(
            pendingCustomization = customization,
            pendingProduct = product,
            pendingQuantity = quantity,
            pendingEditIndex = editIndex
        )
    }

    fun clearPendingSelection() {
        _uiState.value = _uiState.value.copy(
            pendingCustomization = null,
            pendingProduct = null,
            pendingQuantity = 0,
            pendingEditIndex = null
        )
    }

    fun prepareOrder(
        total: Int,
        itemCount: Int,
        orderNumber: String,
        productsSummary: String,
        username: String
    ) {
        _uiState.value = _uiState.value.copy(
            pendingOrderTotal = total,
            pendingOrderItemCount = itemCount,
            pendingOrderNumber = orderNumber,
            pendingOrderHistoryId = UUID.randomUUID().toString(),
            pendingOrderProducts = productsSummary,
            pendingOrderUsername = username.trim()
        )
    }

    fun clearOrder() {
        _uiState.value = AppUiState()
    }
}
