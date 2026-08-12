package com.armatuhandroll.data.local

import android.content.Context
import com.armatuhandroll.domain.history.OrderHistoryStorage
import com.armatuhandroll.domain.model.OrderHistoryItem
import com.armatuhandroll.domain.model.OrderStatus
import org.json.JSONArray
import org.json.JSONObject

internal class SharedPreferencesOrderHistoryStorage(
    context: Context
) : OrderHistoryStorage {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    override fun load(): List<OrderHistoryItem> {
        val savedHistory = preferences.getString(KEY_ORDER_HISTORY, null)
            ?.takeIf { it.isNotBlank() }
            ?: return emptyList()

        return runCatching {
            val jsonItems = JSONArray(savedHistory)
            buildList {
                for (index in 0 until jsonItems.length()) {
                    runCatching { jsonItems.getJSONObject(index).toOrderHistoryItem() }
                        .getOrNull()
                        ?.let(::add)
                }
            }
        }.getOrDefault(emptyList())
    }

    override fun save(items: List<OrderHistoryItem>) {
        runCatching {
            val jsonItems = JSONArray()
            items.forEach { jsonItems.put(it.toJson()) }
            preferences.edit().putString(KEY_ORDER_HISTORY, jsonItems.toString()).apply()
        }
    }

    override fun clear() {
        runCatching { preferences.edit().remove(KEY_ORDER_HISTORY).apply() }
    }

    private fun OrderHistoryItem.toJson(): JSONObject = JSONObject().apply {
        put(ORDER_NUMBER, orderNumber)
        put(PRODUCTS_SUMMARY, productsSummary)
        put(QUANTITY_TOTAL, quantityTotal)
        put(TOTAL_PAID, totalPaid)
        put(ESTIMATED_TIME_MINUTES, estimatedTimeMinutes)
        put(USERNAME, username)
        put(CREATED_AT, createdAt)
        put(STATUS, status.storageValue)
        put(REJECTION_REASON, rejectionReason?.storageValue.orEmpty())
        put(REJECTION_DETAIL, rejectionDetail)
    }

    private fun JSONObject.toOrderHistoryItem(): OrderHistoryItem {
        val storedStatus = optString(STATUS, OrderStatus.PENDING_REVIEW.storageValue)
        return OrderHistoryItem(
            orderNumber = getString(ORDER_NUMBER),
            productsSummary = getString(PRODUCTS_SUMMARY),
            quantityTotal = getInt(QUANTITY_TOTAL),
            totalPaid = getInt(TOTAL_PAID),
            estimatedTimeMinutes = getInt(ESTIMATED_TIME_MINUTES),
            username = getString(USERNAME),
            createdAt = getLong(CREATED_AT),
            status = OrderStatus.fromStorageValue(storedStatus),
            rejectionReason = com.armatuhandroll.domain.model.RejectionReason.fromStorageValue(
                optString(REJECTION_REASON)
            ),
            rejectionDetail = optString(REJECTION_DETAIL)
        )
    }

    private companion object {
        const val PREFERENCES_NAME = "order_history_preferences"
        const val KEY_ORDER_HISTORY = "order_history"
        const val ORDER_NUMBER = "orderNumber"
        const val PRODUCTS_SUMMARY = "productsSummary"
        const val QUANTITY_TOTAL = "quantityTotal"
        const val TOTAL_PAID = "totalPaid"
        const val ESTIMATED_TIME_MINUTES = "estimatedTimeMinutes"
        const val USERNAME = "username"
        const val CREATED_AT = "createdAt"
        const val STATUS = "status"
        const val REJECTION_REASON = "rejectionReason"
        const val REJECTION_DETAIL = "rejectionDetail"
    }
}
