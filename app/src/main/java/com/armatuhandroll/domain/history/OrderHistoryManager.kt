package com.armatuhandroll.domain.history

import androidx.compose.runtime.mutableStateListOf
import com.armatuhandroll.domain.model.OrderHistoryItem

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

    fun clear() {
        items.clear()
        storage?.clear()
    }

    private fun persist() {
        storage?.save(items.toList())
    }
}
