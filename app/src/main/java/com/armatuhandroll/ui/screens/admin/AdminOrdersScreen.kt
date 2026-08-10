package com.armatuhandroll.ui.screens.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private sealed interface AdminOrdersUiState {
    data object Loading : AdminOrdersUiState
    data object Error : AdminOrdersUiState
    data class Loaded(val orders: List<AdminOrder>) : AdminOrdersUiState
}

@Composable
internal fun AdminOrdersScreen(ordersRepository: AdminOrdersRepository, onBack: () -> Unit) {
    var uiState: AdminOrdersUiState by remember { mutableStateOf(AdminOrdersUiState.Loading) }
    var refreshRequest by remember { mutableIntStateOf(0) }

    LaunchedEffect(ordersRepository, refreshRequest) {
        do {
            ordersRepository.getOrders().fold(
                onSuccess = { uiState = AdminOrdersUiState.Loaded(it.sortedForDisplay()) },
                onFailure = { uiState = AdminOrdersUiState.Error }
            )
            delay(ADMIN_POLL_INTERVAL_MILLIS)
        } while (isActive)
    }

    val orders = (uiState as? AdminOrdersUiState.Loaded)?.orders.orEmpty()
    AppBackground {
        Scaffold(containerColor = Color.Transparent) { innerPadding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Volver", tint = CreamText)
                        }
                        Text("Pedidos", style = MaterialTheme.typography.headlineMedium,
                            color = CreamText, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 7.dp))
                    }
                }
                if (uiState is AdminOrdersUiState.Loaded) {
                    item {
                        AdminSummaryCards(listOf(
                            "Nuevos" to orders.count { it.status == OrderStatus.PENDING_REVIEW }.toString(),
                            "En preparación" to orders.count { it.status == OrderStatus.PREPARING }.toString(),
                            "Listos" to orders.count { it.status == OrderStatus.READY_FOR_PICKUP }.toString()
                        ))
                    }
                }
                when (uiState) {
                    AdminOrdersUiState.Loading -> item { StatusMessage("Cargando pedidos...") }
                    AdminOrdersUiState.Error -> item {
                        IngredientGlassCard {
                            Text("No fue posible cargar los pedidos", color = CreamText)
                            Button(onClick = { refreshRequest++ }, modifier = Modifier.padding(top = 12.dp)) {
                                Text("Reintentar")
                            }
                        }
                    }
                    is AdminOrdersUiState.Loaded -> if (orders.isEmpty()) {
                        item { StatusMessage("No hay pedidos disponibles") }
                    } else {
                        items(orders, key = { it.orderNumber }) { OrderCard(it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusMessage(message: String) = IngredientGlassCard { Text(message, color = CreamText) }

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
    compareBy<AdminOrder> { STATUS_PRIORITY.getValue(it.status) }.thenByDescending { it.dateTime }
)

private val STATUS_PRIORITY = OrderStatus.values().withIndex().associate { (index, status) -> status to index }
