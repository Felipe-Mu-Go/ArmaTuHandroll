package com.armatuhandroll.ui.screens.admin

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import kotlinx.coroutines.launch

@Composable
internal fun AdminDashboardScreen(
    ordersRepository: AdminOrdersRepository,
    onOrdersClick: () -> Unit,
    onCashClick: () -> Unit,
    onExit: () -> Unit
) {
    var orders by remember { mutableStateOf<List<AdminOrder>>(emptyList()) }
    var confirmedSalesToday by remember { mutableStateOf(0) }

    LaunchedEffect(ordersRepository) {
        do {
            ordersRepository.getOrders().onSuccess { orders = it }
            ordersRepository.getPayments().onSuccess { payments ->
                confirmedSalesToday = payments.filter { it.paymentStatus == "confirmed" && it.isToday }.sumOf { it.amount }
            }
            delay(ADMIN_POLL_INTERVAL_MILLIS)
        } while (isActive)
    }

    val summary = listOf(
        "Pedidos nuevos" to orders.count { it.status == OrderStatus.PENDING_REVIEW }.toString(),
        "En preparación" to orders.count { it.status == OrderStatus.PREPARING }.toString(),
        "Listos" to orders.count { it.status == OrderStatus.READY_FOR_PICKUP }.toString(),
        "Ventas de hoy" to formatPrice(confirmedSalesToday)
    )
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val comingSoon: () -> Unit = {
        scope.launch {
            snackbarHostState.showSnackbar("Disponible en un próximo sprint")
        }
        Unit
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
                    Text("Panel administrador", style = MaterialTheme.typography.headlineMedium,
                        color = CreamText, fontWeight = FontWeight.Bold)
                    Text("Arma Tu Handroll", style = MaterialTheme.typography.titleMedium, color = CreamText)
                    Spacer(Modifier.height(8.dp))
                }
                item { AdminSummaryCards(summary) }
                item { DashboardCard("Pedidos", "Ver y gestionar pedidos", Icons.Default.ReceiptLong, onOrdersClick) }
                item { DashboardCard("Caja", "Pagos confirmados del día", Icons.Default.PointOfSale, onCashClick) }
                item { DashboardCard("Ventas", "Resumen de ventas del día", Icons.Default.TrendingUp, comingSoon) }
                item { DashboardCard("Productos", "Conteo por producto", Icons.Default.Inventory2, comingSoon) }
                item { DashboardCard("Reportes", "Historial y estadísticas", Icons.Default.Assessment, comingSoon) }
                item { DashboardCard("Configuración", "Dispositivo, impresión y acceso", Icons.Default.Settings, comingSoon) }
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
internal fun AdminSummaryCards(summary: List<Pair<String, String>>) {
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
private fun DashboardCard(title: String, description: String, icon: ImageVector, onClick: () -> Unit) {
    IngredientGlassCard(
        modifier = Modifier.clickable(onClick = onClick),
        contentPadding = PaddingValues(20.dp)
    ) {
        Icon(icon, contentDescription = null, tint = CreamText)
        Spacer(Modifier.height(10.dp))
        Text(title, color = CreamText, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(description, color = CreamText, style = MaterialTheme.typography.bodyLarge)
    }
}

internal const val ADMIN_POLL_INTERVAL_MILLIS = 10_000L
