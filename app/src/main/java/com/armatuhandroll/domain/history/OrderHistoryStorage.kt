package com.armatuhandroll.domain.history

import com.armatuhandroll.domain.model.OrderHistoryItem

internal interface OrderHistoryStorage {
    fun load(): List<OrderHistoryItem>

    fun save(items: List<OrderHistoryItem>)

    fun clear()
}
