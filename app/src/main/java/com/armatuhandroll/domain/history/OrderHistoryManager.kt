package com.armatuhandroll.domain.history

import androidx.compose.runtime.mutableStateListOf
import com.armatuhandroll.domain.model.OrderHistoryItem
import com.armatuhandroll.domain.model.OrderStatus

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
        items.add(0, item)
        persist()
    }

    fun updateStatus(orderNumber: String, status: OrderStatus): Boolean {
        val index = items.indexOfFirst { item -> item.orderNumber == orderNumber }
        if (index == -1) return false

        val currentItem = items[index]
        if (currentItem.status == status) return false

        items[index] = currentItem.copy(status = status)
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
