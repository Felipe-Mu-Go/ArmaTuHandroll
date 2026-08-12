package com.armatuhandroll.ui.screens.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.armatuhandroll.core.util.formatPrice
import com.armatuhandroll.domain.model.AdminOrder
import com.armatuhandroll.domain.model.OrderStatus
import com.armatuhandroll.domain.model.PaymentMethod
import com.armatuhandroll.domain.model.RejectionReason
import com.armatuhandroll.domain.repository.AdminOrdersRepository
import com.armatuhandroll.ui.AppBackground
import com.armatuhandroll.ui.components.IngredientGlassCard
import com.armatuhandroll.ui.theme.CreamText
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
internal fun AdminOrderDetailScreen(
    order: AdminOrder,
    ordersRepository: AdminOrdersRepository,
    onOrderUpdated: (AdminOrder) -> Unit,
    onBack: () -> Unit
) {
    var displayedOrder by remember(order.orderNumber) { mutableStateOf(order) }
    var isUpdating by remember { mutableStateOf(false) }
    var isRejecting by remember { mutableStateOf(false) }
    var showRejectionSelector by remember { mutableStateOf(false) }
    var showRejectionConfirmation by remember { mutableStateOf(false) }
    var selectedRejectionReason by remember { mutableStateOf(RejectionReason.OUT_OF_STOCK) }
    var rejectionDetail by remember { mutableStateOf("") }
    var showDeliveryConfirmation by remember { mutableStateOf(false) }
    var showPaymentSelector by remember { mutableStateOf(false) }
    var showPaymentConfirmation by remember { mutableStateOf(false) }
    var selectedPaymentMethod by remember { mutableStateOf(PaymentMethod.CASH) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun updateStatus(newStatus: OrderStatus) {
        if (isUpdating) return
        isUpdating = true
        scope.launch {
            ordersRepository.updateOrderStatus(displayedOrder.orderNumber, newStatus).fold(
                onSuccess = { confirmedStatus ->
                    val updatedOrder = displayedOrder.copy(status = confirmedStatus)
                    displayedOrder = updatedOrder
                    onOrderUpdated(updatedOrder)
                    isUpdating = false
                    snackbarHostState.showSnackbar(confirmedStatus.successMessage())
                },
                onFailure = { error ->
                    isUpdating = false
                    snackbarHostState.showSnackbar(
                        if (error.message.orEmpty().contains("cambió de estado", ignoreCase = true)) {
                            "El pedido cambió de estado. Actualiza la información."
                        } else {
                            error.message ?: "No fue posible actualizar el pedido"
                        }
                    )
                }
            )
        }
    }

    fun registerPayment() {
        if (isUpdating) return
        isUpdating = true
        scope.launch {
            ordersRepository.registerPayment(displayedOrder.orderNumber, selectedPaymentMethod).fold(
                onSuccess = { payment ->
                    val updated = displayedOrder.copy(
                        paymentStatus = payment.paymentStatus,
                        paymentMethod = payment.paymentMethod.storageValue,
                        paidAmount = payment.amount
                    )
                    displayedOrder = updated
                    onOrderUpdated(updated)
                    isUpdating = false
                    snackbarHostState.showSnackbar("Pago registrado correctamente")
                },
                onFailure = { error ->
                    isUpdating = false
                    snackbarHostState.showSnackbar(error.message ?: "No fue posible registrar el pago")
                }
            )
        }
    }

    fun rejectOrder() {
        if (isUpdating) return
        isUpdating = true
        isRejecting = true
        val detail = rejectionDetail.trim()
        scope.launch {
            ordersRepository.rejectOrder(displayedOrder.orderNumber, selectedRejectionReason, detail).fold(
                onSuccess = { status ->
                    val updated = displayedOrder.copy(
                        status = status,
                        rejectionReason = selectedRejectionReason,
                        rejectionDetail = if (selectedRejectionReason == RejectionReason.OTHER) detail else ""
                    )
                    displayedOrder = updated
                    onOrderUpdated(updated)
                    isUpdating = false
                    isRejecting = false
                    snackbarHostState.showSnackbar("Pedido rechazado")
                },
                onFailure = { error ->
                    isUpdating = false
                    isRejecting = false
                    snackbarHostState.showSnackbar(error.message ?: "No fue posible rechazar el pedido")
                }
            )
        }
    }

    if (showRejectionSelector) {
        AlertDialog(
            onDismissRequest = { if (!isUpdating) showRejectionSelector = false },
            title = { Text("¿Por qué rechazas este pedido?") },
            text = {
                Column {
                    RejectionReason.values().forEach { reason ->
                        Row {
                            RadioButton(
                                selected = selectedRejectionReason == reason,
                                onClick = { selectedRejectionReason = reason }
                            )
                            Text(reason.displayName, modifier = Modifier.padding(top = 12.dp))
                        }
                    }
                    if (selectedRejectionReason == RejectionReason.OTHER) {
                        OutlinedTextField(
                            value = rejectionDetail,
                            onValueChange = { if (it.length <= 120) rejectionDetail = it },
                            label = { Text("Explicación breve") },
                            supportingText = { Text("3 a 120 caracteres") }
                        )
                    }
                }
            },
            dismissButton = { TextButton(onClick = { showRejectionSelector = false }) { Text("Volver") } },
            confirmButton = {
                TextButton(
                    onClick = { showRejectionSelector = false; showRejectionConfirmation = true },
                    enabled = selectedRejectionReason != RejectionReason.OTHER || rejectionDetail.trim().length in 3..120
                ) { Text("Continuar") }
            }
        )
    }

    if (showRejectionConfirmation) {
        AlertDialog(
            onDismissRequest = { if (!isUpdating) showRejectionConfirmation = false },
            title = { Text("Rechazar pedido") },
            text = { Text("Pedido: ${displayedOrder.orderNumber}\nMotivo: ${selectedRejectionReason.displayName}\n\nEl cliente será informado de que el pedido no fue aceptado.") },
            dismissButton = { TextButton(onClick = { showRejectionConfirmation = false }, enabled = !isUpdating) { Text("Volver") } },
            confirmButton = { TextButton(onClick = { showRejectionConfirmation = false; rejectOrder() }, enabled = !isUpdating) { Text("Confirmar rechazo") } }
        )
    }

    if (showPaymentSelector) {
        AlertDialog(
            onDismissRequest = { if (!isUpdating) showPaymentSelector = false },
            title = { Text("Registrar pago") },
            text = {
                Column {
                    Text("Total: ${formatPrice(displayedOrder.totalPaid)}")
                    PaymentMethod.values().forEach { method ->
                        Row {
                            RadioButton(selected = selectedPaymentMethod == method, onClick = { selectedPaymentMethod = method })
                            Text(method.displayName, modifier = Modifier.padding(top = 12.dp))
                        }
                    }
                }
            },
            dismissButton = { TextButton(onClick = { showPaymentSelector = false }) { Text("Volver") } },
            confirmButton = { TextButton(onClick = { showPaymentSelector = false; showPaymentConfirmation = true }) { Text("Confirmar pago") } }
        )
    }

    if (showPaymentConfirmation) {
        AlertDialog(
            onDismissRequest = { if (!isUpdating) showPaymentConfirmation = false },
            title = { Text("Confirmar pago") },
            text = { Text("Pedido: ${displayedOrder.orderNumber}\nMétodo: ${selectedPaymentMethod.displayName}\nMonto: ${formatPrice(displayedOrder.totalPaid)}") },
            dismissButton = { TextButton(onClick = { showPaymentConfirmation = false }, enabled = !isUpdating) { Text("Volver") } },
            confirmButton = { TextButton(onClick = { showPaymentConfirmation = false; registerPayment() }, enabled = !isUpdating) { Text("Confirmar pago") } }
        )
    }

    if (showDeliveryConfirmation) {
        AlertDialog(
            onDismissRequest = { if (!isUpdating) showDeliveryConfirmation = false },
            title = { Text("¿Marcar pedido como entregado?") },
            text = { Text("Esta acción finalizará el pedido.") },
            dismissButton = {
                TextButton(onClick = { showDeliveryConfirmation = false }, enabled = !isUpdating) {
                    Text("Volver")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeliveryConfirmation = false
                        updateStatus(OrderStatus.DELIVERED)
                    },
                    enabled = !isUpdating
                ) { Text("Marcar entregado") }
            }
        )
    }

    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        IconButton(onClick = onBack, enabled = !isUpdating) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Volver", tint = CreamText)
                        }
                        Text(
                            displayedOrder.orderNumber,
                            style = MaterialTheme.typography.headlineMedium,
                            color = CreamText,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 7.dp)
                        )
                    }
                }
                item {
                    IngredientGlassCard {
                        DetailField("Cliente", displayedOrder.customerName)
                        DetailField("Fecha y hora", displayedOrder.dateTime.forAdminDisplay())
                        DetailField("Productos", displayedOrder.products)
                        DetailField(
                            "Cantidad",
                            "${displayedOrder.totalQuantity} ${if (displayedOrder.totalQuantity == 1) "producto" else "productos"}"
                        )
                        DetailField("Total", formatPrice(displayedOrder.totalPaid))
                        DetailField("Tiempo estimado", displayedOrder.estimatedTime)
                        DetailField("Estado", displayedOrder.status.displayName)
                        if (displayedOrder.status == OrderStatus.REJECTED) {
                            DetailField("Motivo", displayedOrder.rejectionReason?.displayName.orEmpty())
                            if (displayedOrder.rejectionDetail.isNotBlank()) {
                                DetailField("Detalle", displayedOrder.rejectionDetail)
                            }
                        }
                    }
                }
                item {
                    IngredientGlassCard {
                        Text("Pago", color = CreamText, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        DetailField("Estado", if (displayedOrder.paymentStatus == "confirmed") "Pagado" else "Pendiente de pago")
                        if (displayedOrder.paymentStatus == "confirmed") {
                            DetailField("Método", PaymentMethod.fromStorageValue(displayedOrder.paymentMethod).displayName)
                            DetailField("Monto", formatPrice(displayedOrder.paidAmount))
                        } else if (displayedOrder.status !in setOf(OrderStatus.CANCELLED, OrderStatus.REJECTED)) {
                            Button(onClick = { showPaymentSelector = true }, enabled = !isUpdating, modifier = Modifier.fillMaxWidth()) {
                                Text("Registrar pago")
                            }
                        }
                    }
                }
                if (isUpdating) {
                    item { Text(if (isRejecting) "Rechazando pedido..." else "Procesando solicitud...", color = CreamText, fontWeight = FontWeight.Bold) }
                }
                if (displayedOrder.status == OrderStatus.PENDING_REVIEW) {
                    item {
                        Button(
                            onClick = { updateStatus(OrderStatus.ACCEPTED) },
                            enabled = !isUpdating,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Aceptar pedido") }
                    }
                    item {
                        OutlinedButton(
                            onClick = { showRejectionSelector = true },
                            enabled = !isUpdating,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Rechazar pedido") }
                    }
                }
                when (displayedOrder.status) {
                    OrderStatus.ACCEPTED -> item {
                        Button(
                            onClick = { updateStatus(OrderStatus.PREPARING) },
                            enabled = !isUpdating,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Iniciar preparación") }
                    }
                    OrderStatus.PREPARING -> item {
                        Button(
                            onClick = { updateStatus(OrderStatus.READY_FOR_PICKUP) },
                            enabled = !isUpdating,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Marcar listo para retirar") }
                    }
                    OrderStatus.READY_FOR_PICKUP -> item {
                        Button(
                            onClick = { showDeliveryConfirmation = true },
                            enabled = !isUpdating,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Marcar como entregado") }
                    }
                    OrderStatus.DELIVERED -> item {
                        Text("Pedido entregado", color = CreamText, fontWeight = FontWeight.Bold)
                    }
                    OrderStatus.CANCELLED -> item {
                        Text("Pedido cancelado", color = CreamText, fontWeight = FontWeight.Bold)
                    }
                    OrderStatus.REJECTED -> item {
                        IngredientGlassCard {
                            Text("Pedido rechazado", color = CreamText, fontWeight = FontWeight.Bold)
                            DetailField("Motivo", displayedOrder.rejectionReason?.displayName.orEmpty())
                            if (displayedOrder.rejectionDetail.isNotBlank()) {
                                DetailField("Detalle", displayedOrder.rejectionDetail)
                            }
                        }
                    }
                    else -> Unit
                }
            }
        }
    }
}

private fun OrderStatus.successMessage(): String = when (this) {
    OrderStatus.ACCEPTED -> "Pedido aceptado"
    OrderStatus.PREPARING -> "Preparación iniciada"
    OrderStatus.READY_FOR_PICKUP -> "Pedido listo para retirar"
    OrderStatus.DELIVERED -> "Pedido entregado"
    OrderStatus.CANCELLED -> "Pedido cancelado"
    OrderStatus.REJECTED -> "Pedido rechazado"
    else -> "Pedido actualizado"
}

@Composable
private fun DetailField(label: String, value: String) {
    Text(label, color = CreamText, fontWeight = FontWeight.Bold)
    Text(value, color = CreamText, style = MaterialTheme.typography.bodyLarge)
    Spacer(Modifier.height(16.dp))
}

private fun String.forAdminDisplay(): String {
    val parser = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).apply {
        isLenient = false
    }
    val parsed = runCatching { parser.parse(this@forAdminDisplay) }.getOrNull() ?: return this
    return SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(parsed)
}
