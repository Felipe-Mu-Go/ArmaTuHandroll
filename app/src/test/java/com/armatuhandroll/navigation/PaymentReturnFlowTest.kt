package com.armatuhandroll.navigation

import com.armatuhandroll.domain.model.OrderStatus
import com.armatuhandroll.domain.model.OrderStatusUpdate
import com.armatuhandroll.domain.repository.OrderStatusRepository
import com.armatuhandroll.domain.repository.WebpayCreationRejectedException
import com.armatuhandroll.domain.history.preserveConfirmedPaymentStatus
import com.armatuhandroll.domain.cart.remainingCartItemsAfterPayment
import com.armatuhandroll.domain.model.CartItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

internal class PaymentReturnFlowTest {
    @Test
    fun `a second Webpay order is rejected while another order is pending`() {
        assertTrue(validateWebpayStartOrder("ORDER-1", "").isSuccess)
        assertTrue(validateWebpayStartOrder("ORDER-1", "ORDER-1").isSuccess)
        assertTrue(validateWebpayStartOrder("ORDER-2", "ORDER-1").isFailure)
    }

    @Test
    fun `transfer completion clears Webpay recovery only for the same order`() {
        assertTrue(isPendingWebpayOrder("ORDER-1", "ORDER-1"))
        assertFalse(isPendingWebpayOrder("ORDER-2", "ORDER-1"))
    }

    @Test
    fun `confirmed transfer cannot be consumed as a Webpay return`() = runBlocking {
        val repository = FakeStatusRepository(
            update(paymentStatus = "confirmed").copy(paymentMethod = "transfer")
        )

        val result = PaymentReturnCoordinator(repository).resolve("ORDER-1")

        assertTrue(result.isFailure)
    }

    @Test
    fun `confirmed transfer without a Webpay attempt releases provisional recovery`() = runBlocking {
        val repository = FakeStatusRepository(
            update(paymentStatus = "confirmed").copy(
                paymentMethod = "transfer",
                webpayAttemptPresent = false
            )
        )

        val resolution = PaymentReturnCoordinator(repository).resolve("ORDER-1").getOrThrow()

        assertEquals(PaymentReturnOutcome.FAILED, resolution.outcome)
        assertTrue(resolution.allowsControlledRetry)
    }

    @Test
    fun `return order must match the locally pending Webpay order`() {
        assertEquals(
            "ORDER-1",
            correlatePaymentReturnOrder("ORDER-1", "ORDER-1").getOrThrow()
        )
        assertEquals(
            "ORDER-1",
            correlatePaymentReturnOrder("", "ORDER-1").getOrThrow()
        )
        assertTrue(correlatePaymentReturnOrder("ORDER-2", "ORDER-1").isFailure)
        assertTrue(correlatePaymentReturnOrder("ORDER-1", "").isFailure)
    }

    @Test
    fun `confirmed payment removes only the paid cart snapshot`() {
        val paidItem = CartItem(productId = 1, name = "Handroll", unitPrice = 3_000, quantity = 2)
        val addedLater = CartItem(productId = 2, name = "Bebida", unitPrice = 1_500, quantity = 1)

        val remaining = remainingCartItemsAfterPayment(
            currentItems = listOf(paidItem.copy(quantity = 3), addedLater),
            paidItems = listOf(paidItem)
        )

        assertEquals(listOf(paidItem.copy(quantity = 1), addedLater), remaining)
    }

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
    fun `missing remote Webpay attempt releases provisional recovery`() = runBlocking {
        val repository = FakeStatusRepository(
            update(paymentStatus = "pending").copy(webpayAttemptPresent = false)
        )

        val resolution = PaymentReturnCoordinator(repository).resolve("ORDER-1").getOrThrow()

        assertEquals(PaymentReturnOutcome.FAILED, resolution.outcome)
        assertTrue(resolution.allowsControlledRetry)
    }

    @Test
    fun `existing remote Webpay attempt remains pending`() = runBlocking {
        val repository = FakeStatusRepository(
            update(paymentStatus = "pending").copy(webpayAttemptPresent = true)
        )

        val resolution = PaymentReturnCoordinator(repository).resolve("ORDER-1").getOrThrow()

        assertEquals(PaymentReturnOutcome.PENDING, resolution.outcome)
        assertFalse(resolution.allowsControlledRetry)
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
    fun `definitive creation rejection clears provisional recovery but transport failure keeps it`() {
        assertTrue(shouldClearProvisionalWebpayRecovery(WebpayCreationRejectedException("rejected")))
        assertFalse(shouldClearProvisionalWebpayRecovery(java.net.SocketTimeoutException("timeout")))
        assertFalse(shouldClearProvisionalWebpayRecovery(java.io.IOException("offline")))
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
