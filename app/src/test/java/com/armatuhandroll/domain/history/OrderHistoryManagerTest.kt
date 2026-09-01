package com.armatuhandroll.domain.history

import com.armatuhandroll.domain.model.OrderHistoryItem
import com.armatuhandroll.domain.model.OrderStatus
import org.junit.Assert.assertEquals
import org.junit.Test

internal class OrderHistoryManagerTest {
    @Test
    fun `same stable history id is upserted`() {
        val existing = item(historyId = "history-1", createdAt = 100)
        val retry = item(historyId = "history-1", createdAt = 200, paymentStatus = "pending")

        val result = mergeOrderHistoryItems(listOf(existing), retry)

        assertEquals(1, result.size)
        assertEquals(100L, result.single().createdAt)
    }

    @Test
    fun `same short order number with different stable ids remains distinct`() {
        val first = item(historyId = "history-1", createdAt = 100)
        val second = item(historyId = "history-2", createdAt = 200)

        val result = mergeOrderHistoryItems(listOf(first), second)

        assertEquals(2, result.size)
        assertEquals(listOf("history-2", "history-1"), result.map { it.historyId })
    }

    @Test
    fun `Webpay retry does not create a duplicate`() {
        val existing = item(historyId = "history-1", paymentStatus = "pending")
        val retry = item(historyId = "history-1", paymentStatus = "pending")

        assertEquals(1, mergeOrderHistoryItems(listOf(existing), retry).size)
    }

    @Test
    fun `legacy history without stable id still matches deterministic immutable fields`() {
        val legacy = item(historyId = "", createdAt = 100)
        val retry = item(historyId = "history-migrated", createdAt = 200)

        val result = mergeOrderHistoryItems(listOf(legacy), retry)

        assertEquals(1, result.size)
        assertEquals("history-migrated", result.single().historyId)
        assertEquals(100L, result.single().createdAt)
    }

    @Test
    fun `confirmed payment is preserved during upsert`() {
        val existing = item(
            historyId = "history-1",
            paymentStatus = "confirmed",
            paymentMethod = "webpay"
        )
        val retry = item(historyId = "history-1", paymentStatus = "pending", paymentMethod = "webpay")

        val result = mergeOrderHistoryItems(listOf(existing), retry).single()

        assertEquals("confirmed", result.paymentStatus)
        assertEquals("webpay", result.paymentMethod)
    }

    private fun item(
        historyId: String,
        createdAt: Long = 100,
        paymentStatus: String = "pending",
        paymentMethod: String = "webpay"
    ) = OrderHistoryItem(
        historyId = historyId,
        orderNumber = "1234",
        productsSummary = "2 handrolls",
        quantityTotal = 2,
        totalPaid = 10000,
        estimatedTimeMinutes = 10,
        username = "Cliente",
        createdAt = createdAt,
        status = OrderStatus.PENDING_REVIEW,
        paymentStatus = paymentStatus,
        paymentMethod = paymentMethod
    )
}
