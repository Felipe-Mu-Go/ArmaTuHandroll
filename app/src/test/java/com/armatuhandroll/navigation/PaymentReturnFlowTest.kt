package com.armatuhandroll.navigation

import com.armatuhandroll.domain.model.OrderStatus
import com.armatuhandroll.domain.model.OrderStatusUpdate
import com.armatuhandroll.domain.repository.OrderStatusRepository
import com.armatuhandroll.domain.history.preserveConfirmedPaymentStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

internal class PaymentReturnFlowTest {
    @Test
    fun `confirmed return is verified remotely before checkout is closed`() = runBlocking {
        val repository = FakeStatusRepository(update(paymentStatus = "confirmed"))

        val resolution = PaymentReturnCoordinator(repository).resolve("ORDER-1").getOrThrow()

        assertEquals(listOf("ORDER-1"), repository.requests)
        assertEquals(PaymentReturnOutcome.CONFIRMED, resolution.outcome)
        assertTrue(resolution.clearsCart)
        assertTrue(resolution.clearsCheckoutBackStack)
        assertFalse(resolution.allowsControlledRetry)
    }

    @Test
    fun `confirmed payment keeps administrative order pending review`() = runBlocking {
        val resolution = coordinatorFor("confirmed").resolve("ORDER-1").getOrThrow()

        assertEquals(OrderStatus.PENDING_REVIEW, resolution.update.status)
        assertEquals("confirmed", resolution.update.paymentStatus)
    }

    @Test
    fun `pending return does not clear cart or checkout and can be checked again`() = runBlocking {
        val repository = FakeStatusRepository(update(paymentStatus = "pending"))
        val coordinator = PaymentReturnCoordinator(repository)

        val first = coordinator.resolve("ORDER-1").getOrThrow()
        val second = coordinator.resolve("ORDER-1").getOrThrow()

        assertEquals(PaymentReturnOutcome.PENDING, first.outcome)
        assertFalse(first.clearsCart)
        assertFalse(first.clearsCheckoutBackStack)
        assertEquals(PaymentReturnOutcome.PENDING, second.outcome)
        assertEquals(2, repository.requests.size)
    }

    @Test
    fun `cancelled return permits a controlled new attempt without reporting success`() = runBlocking {
        val resolution = coordinatorFor("cancelled").resolve("ORDER-1").getOrThrow()

        assertEquals(PaymentReturnOutcome.CANCELLED, resolution.outcome)
        assertFalse(resolution.clearsCart)
        assertTrue(resolution.allowsControlledRetry)
    }

    @Test
    fun `failed return permits a controlled new attempt without reporting success`() = runBlocking {
        val resolution = coordinatorFor("failed").resolve("ORDER-1").getOrThrow()

        assertEquals(PaymentReturnOutcome.FAILED, resolution.outcome)
        assertFalse(resolution.clearsCart)
        assertTrue(resolution.allowsControlledRetry)
    }

    @Test
    fun `reversed and nullified returns are treated as failed terminal attempts`() = runBlocking {
        assertEquals(
            PaymentReturnOutcome.FAILED,
            coordinatorFor("reversed").resolve("ORDER-1").getOrThrow().outcome
        )
        assertEquals(
            PaymentReturnOutcome.FAILED,
            coordinatorFor("nullified").resolve("ORDER-1").getOrThrow().outcome
        )
    }

    @Test
    fun `network failure never closes checkout as successful`() = runBlocking {
        val repository = FakeStatusRepository(failure = IllegalStateException("offline"))

        val result = PaymentReturnCoordinator(repository).resolve("ORDER-1")

        assertTrue(result.isFailure)
        assertEquals(listOf("ORDER-1"), repository.requests)
    }

    @Test
    fun `blank recovery order is rejected without a remote lookup`() = runBlocking {
        val repository = FakeStatusRepository(update("confirmed"))

        val result = PaymentReturnCoordinator(repository).resolve(" ")

        assertTrue(result.isFailure)
        assertTrue(repository.requests.isEmpty())
    }

    @Test
    fun `open background and restarted returns all use fresh remote verification`() = runBlocking {
        val repository = FakeStatusRepository(update("confirmed"))
        val coordinator = PaymentReturnCoordinator(repository)

        repeat(3) { coordinator.resolve("ORDER-1").getOrThrow() }

        assertEquals(listOf("ORDER-1", "ORDER-1", "ORDER-1"), repository.requests)
    }

    @Test
    fun `administrative lifecycle remains independent from payment confirmation`() {
        assertEquals("pending_review", OrderStatus.PENDING_REVIEW.storageValue)
        assertEquals("accepted", OrderStatus.ACCEPTED.storageValue)
        assertEquals("preparing", OrderStatus.PREPARING.storageValue)
        assertEquals("ready_for_pickup", OrderStatus.READY_FOR_PICKUP.storageValue)
        assertEquals("delivered", OrderStatus.DELIVERED.storageValue)
    }

    @Test
    fun `confirmed local payment cannot be downgraded by a stale response`() {
        assertEquals("confirmed", preserveConfirmedPaymentStatus("confirmed", "pending"))
        assertEquals("confirmed", preserveConfirmedPaymentStatus("confirmed", "failed"))
        assertEquals("cancelled", preserveConfirmedPaymentStatus("pending", "cancelled"))
    }

    private fun coordinatorFor(paymentStatus: String) =
        PaymentReturnCoordinator(FakeStatusRepository(update(paymentStatus)))

    private fun update(paymentStatus: String) = OrderStatusUpdate(
        status = OrderStatus.PENDING_REVIEW,
        paymentStatus = paymentStatus,
        paymentMethod = "webpay"
    )

    private class FakeStatusRepository(
        private val response: OrderStatusUpdate? = null,
        private val failure: Throwable? = null
    ) : OrderStatusRepository {
        val requests = mutableListOf<String>()

        override suspend fun getStatus(orderNumber: String): Result<OrderStatusUpdate> {
            requests += orderNumber
            return failure?.let { Result.failure(it) }
                ?: Result.success(checkNotNull(response))
        }
    }
}
