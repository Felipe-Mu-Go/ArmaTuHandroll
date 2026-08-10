package com.armatuhandroll.ui.screens.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.armatuhandroll.core.util.formatPrice
import com.armatuhandroll.domain.model.AdminOrder
import com.armatuhandroll.domain.model.OrderStatus
import com.armatuhandroll.domain.repository.AdminOrdersRepository
import com.armatuhandroll.ui.AppBackground
import com.armatuhandroll.ui.components.IngredientGlassCard
import com.armatuhandroll.ui.theme.CreamText
import com.armatuhandroll.ui.theme.DeepBrown
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private sealed interface AdminOrdersUiState {
    data object Loading : AdminOrdersUiState
    data object Error : AdminOrdersUiState
    data class Loaded(val orders: List<AdminOrder>) : AdminOrdersUiState
}

@Composable
internal fun AdminDashboardScreen(
    ordersRepository: AdminOrdersRepository,
    onExit: () -> Unit
) {
    var uiState: AdminOrdersUiState by remember { mutableStateOf(AdminOrdersUiState.Loading) }
    var refreshRequest by remember { mutableIntStateOf(0) }

    LaunchedEffect(ordersRepository, refreshRequest) {
        do {
            ordersRepository.getOrders().fold(
                onSuccess = { uiState = AdminOrdersUiState.Loaded(it.sortedForDisplay()) },
                onFailure = { uiState = AdminOrdersUiState.Error }
            )
            delay(POLL_INTERVAL_MILLIS)
        } while (isActive)
    }

    val orders = (uiState as? AdminOrdersUiState.Loaded)?.orders.orEmpty()
    val today = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }
    val summary = listOf(
        "Pedidos nuevos" to orders.count { it.status == OrderStatus.PENDING_REVIEW }.toString(),
        "En preparación" to orders.count { it.status == OrderStatus.PREPARING }.toString(),
        "Listos" to orders.count { it.status == OrderStatus.READY_FOR_PICKUP }.toString(),
        "Ventas de hoy" to formatPrice(
            orders.filter { it.dateTime.startsWith(today) && it.status != OrderStatus.CANCELLED }
                .sumOf { it.totalPaid }
        )
    )

    AppBackground {
        Scaffold(containerColor = Color.Transparent) { innerPadding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Text(
                        "Panel administrador",
                        style = MaterialTheme.typography.headlineMedium,
                        color = CreamText,
                        fontWeight = FontWeight.Bold
                    )
                    Text("Arma Tu Handroll", style = MaterialTheme.typography.titleMedium, color = CreamText)
                    Spacer(Modifier.height(8.dp))
                }
                if (uiState is AdminOrdersUiState.Loaded) {
                    item { SummaryCards(summary) }
                }
                when (uiState) {
                    AdminOrdersUiState.Loading -> item { StatusMessage("Cargando pedidos...") }
                    AdminOrdersUiState.Error -> item {
                        IngredientGlassCard {
                            Text("No fue posible cargar los pedidos", color = CreamText)
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = { refreshRequest++ }) { Text("Reintentar") }
                        }
                    }
                    is AdminOrdersUiState.Loaded -> {
                        if (orders.isEmpty()) {
                            item { StatusMessage("No hay pedidos disponibles") }
                        } else {
                            items(orders, key = { it.orderNumber }) { order -> OrderCard(order) }
                        }
                    }
                }
                item {
                    Button(
                        onClick = onExit,
                        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DeepBrown, contentColor = CreamText)
                    ) { Text("Salir del panel", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
private fun SummaryCards(summary: List<Pair<String, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        summary.chunked(2).forEach { entries ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                entries.forEach { (label, value) ->
                    IngredientGlassCard(modifier = Modifier.weight(1f)) {
                        Text(label, color = CreamText)
                        Text(value, color = CreamText, style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusMessage(message: String) {
    IngredientGlassCard { Text(message, color = CreamText) }
}

@Composable
private fun OrderCard(order: AdminOrder) {
    IngredientGlassCard {
        Text(order.orderNumber, color = CreamText, style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold)
        Text("Cliente: ${order.customerName}", color = CreamText)
        Text("Fecha: ${order.dateTime}", color = CreamText)
        Text("Productos: ${order.products}", color = CreamText)
        Text("Cantidad: ${order.totalQuantity}", color = CreamText)
        Text("Total: ${formatPrice(order.totalPaid)}", color = CreamText)
        Text("Tiempo estimado: ${order.estimatedTime}", color = CreamText)
        Text("Estado: ${order.status.displayName}", color = CreamText, fontWeight = FontWeight.Bold)
    }
}

private fun List<AdminOrder>.sortedForDisplay(): List<AdminOrder> = sortedWith(
    compareBy<AdminOrder> { STATUS_PRIORITY.getValue(it.status) }
        .thenByDescending { it.dateTime }
)

private val STATUS_PRIORITY = OrderStatus.values().withIndex().associate { (index, status) -> status to index }
private const val POLL_INTERVAL_MILLIS = 10_000L
