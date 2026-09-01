package com.armatuhandroll.domain.history

import androidx.compose.runtime.mutableStateListOf
import com.armatuhandroll.domain.model.OrderHistoryItem
import com.armatuhandroll.domain.model.OrderStatus
import com.armatuhandroll.domain.model.OrderStatusUpdate

internal object OrderHistoryManager {
    private var storage: OrderHistoryStorage? = null
    private var initialized = false

    val items = mutableStateListOf<OrderHistoryItem>()

    fun initialize(storage: OrderHistoryStorage) {
        if (initialized) return

        this.storage = storage
        items.clear()
        items.addAll(storage.load())
        initialized = true
    }

    fun add(item: OrderHistoryItem) {
        addOrUpdate(item)
    }

    fun addOrUpdate(item: OrderHistoryItem) {
        val merged = mergeOrderHistoryItems(items, item)
        items.clear()
        items.addAll(merged)
        persist()
    }

    fun updateStatus(orderNumber: String, status: OrderStatus): Boolean {
        return updateStatus(orderNumber, OrderStatusUpdate(status))
    }

    fun updateStatus(orderNumber: String, update: OrderStatusUpdate): Boolean {
        val index = items.indexOfFirst { item -> item.orderNumber == orderNumber }
        if (index == -1) return false

        val currentItem = items[index]
        if (currentItem.status == update.status &&
            currentItem.rejectionReason == update.rejectionReason &&
            currentItem.rejectionDetail == update.rejectionDetail &&
            currentItem.paymentStatus == update.paymentStatus &&
            currentItem.paymentMethod == update.paymentMethod
        ) return false

        items[index] = currentItem.copy(
            status = update.status,
            rejectionReason = update.rejectionReason,
            rejectionDetail = update.rejectionDetail,
            paymentStatus = update.paymentStatus,
            paymentMethod = update.paymentMethod
        )
        persist()
        return true
    }

    fun clear() {
        items.clear()
        storage?.clear()
    }

    private fun persist() {
        storage?.save(items.toList())
    }
}

internal fun mergeOrderHistoryItems(
    items: List<OrderHistoryItem>,
    item: OrderHistoryItem
): List<OrderHistoryItem> {
    val index = items.indexOfFirst { it.hasSameHistoryIdentity(item) }
    if (index == -1) return listOf(item) + items

    val existing = items[index]
    val updated = item.copy(
        historyId = item.historyId.ifBlank { existing.historyId },
        createdAt = existing.createdAt,
        status = existing.status,
        rejectionReason = existing.rejectionReason,
        rejectionDetail = existing.rejectionDetail,
        paymentStatus = if (existing.paymentStatus == "confirmed") "confirmed" else item.paymentStatus,
        paymentMethod = if (existing.paymentStatus == "confirmed") existing.paymentMethod else item.paymentMethod
    )
    return listOf(updated) + items.filterIndexed { itemIndex, _ -> itemIndex != index }
}

private fun OrderHistoryItem.hasSameHistoryIdentity(other: OrderHistoryItem): Boolean {
    if (historyId.isNotBlank() && other.historyId.isNotBlank()) return historyId == other.historyId
    return orderNumber == other.orderNumber &&
        productsSummary == other.productsSummary &&
        quantityTotal == other.quantityTotal &&
        totalPaid == other.totalPaid &&
        estimatedTimeMinutes == other.estimatedTimeMinutes &&
        username.trim() == other.username.trim()
}
