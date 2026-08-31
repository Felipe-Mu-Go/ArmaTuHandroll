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
        val index = items.indexOfFirst { it.orderNumber == item.orderNumber }
        if (index == -1) {
            items.add(0, item)
        } else {
            val existing = items[index]
            items.removeAll { it.orderNumber == item.orderNumber }
            items.add(
                0,
                item.copy(
                    createdAt = existing.createdAt,
                    status = existing.status,
                    rejectionReason = existing.rejectionReason,
                    rejectionDetail = existing.rejectionDetail,
                    paymentStatus = if (existing.paymentStatus == "confirmed") "confirmed" else item.paymentStatus,
                    paymentMethod = if (existing.paymentStatus == "confirmed") {
                        existing.paymentMethod
                    } else {
                        item.paymentMethod
                    }
                )
            )
        }
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
