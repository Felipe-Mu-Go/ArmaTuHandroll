package com.armatuhandroll.ui.screens.order

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.armatuhandroll.core.util.formatPrice
import com.armatuhandroll.domain.model.OrderHistoryItem
import com.armatuhandroll.ui.AppBackground
import com.armatuhandroll.ui.components.IngredientGlassCard
import com.armatuhandroll.ui.components.ConnectivityBanner
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OrderHistoryScreen(
    isConnected: Boolean,
    orders: List<OrderHistoryItem>,
    onBack: () -> Unit,
    onClearHistory: () -> Unit,
    onOrderClick: (OrderHistoryItem) -> Unit
) {
    var showClearConfirmation by rememberSaveable { mutableStateOf(false) }

    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = Color.White,
            topBar = {
                CenterAlignedTopAppBar(
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White
                    ),
                    title = { Text("Mis pedidos") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Text("←", style = MaterialTheme.typography.headlineSmall)
                        }
                    }
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                ConnectivityBanner(isConnected = isConnected)
                if (orders.isEmpty()) {
                    EmptyOrderHistory(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(orders) { order ->
                            OrderHistoryCard(
                                order = order,
                                onClick = { onOrderClick(order) }
                            )
                        }
                    }
                    Button(
                        onClick = { showClearConfirmation = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text("Borrar historial")
                    }
                }
            }
        }
    }

    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            title = { Text("¿Borrar historial?") },
            text = { Text("Se eliminarán todos los pedidos guardados en este dispositivo.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirmation = false
                        onClearHistory()
                    }
                ) {
                    Text("Borrar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmation = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun EmptyOrderHistory(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Aún no tienes pedidos guardados.",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Los pedidos enviados correctamente aparecerán aquí.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun OrderHistoryCard(order: OrderHistoryItem, onClick: () -> Unit) {
    IngredientGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp)
    ) {
        Text(
            text = order.orderNumber,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = order.status.displayName,
            modifier = Modifier.padding(top = 6.dp, bottom = 12.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFFFC857)
        )
        Text("Fecha: ${formatOrderDate(order.createdAt)}")
        Text("Nombre: ${order.username}", modifier = Modifier.padding(top = 6.dp))
        Text("Cantidad: ${order.quantityTotal}", modifier = Modifier.padding(top = 6.dp))
        Text(
            text = "Total: ${formatPrice(order.totalPaid)}",
            modifier = Modifier.padding(top = 6.dp),
            fontWeight = FontWeight.Bold,
            color = Color(0xFF8BF6A0)
        )
        Text(
            text = "Tiempo estimado: ${order.estimatedTimeMinutes} minutos",
            modifier = Modifier.padding(top = 6.dp)
        )
        Text(
            text = "Productos: ${order.productsSummary}",
            modifier = Modifier.padding(top = 10.dp),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

internal fun formatOrderDate(timestamp: Long): String = SimpleDateFormat(
    "dd/MM/yyyy HH:mm",
    Locale.getDefault()
).format(Date(timestamp))
