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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.armatuhandroll.ui.AppBackground
import com.armatuhandroll.ui.components.IngredientGlassCard
import com.armatuhandroll.ui.theme.CreamText
import com.armatuhandroll.ui.theme.DeepBrown
import com.armatuhandroll.ui.theme.SoftBeige
import kotlinx.coroutines.launch

private data class AdminModule(
    val title: String,
    val description: String,
    val icon: ImageVector
)

@Composable
internal fun AdminDashboardScreen(onExit: () -> Unit) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val modules = listOf(
        AdminModule("Pedidos", "Ver y gestionar pedidos", Icons.Default.ReceiptLong),
        AdminModule("Caja", "Pagos y cierre de caja", Icons.Default.PointOfSale),
        AdminModule("Ventas", "Resumen de ventas del día", Icons.Default.Payments),
        AdminModule("Productos", "Conteo por producto", Icons.Default.Inventory2),
        AdminModule("Reportes", "Historial y estadísticas", Icons.Default.Assessment),
        AdminModule("Configuración", "Dispositivo, impresión y acceso", Icons.Default.Settings)
    )
    // Datos locales de ejemplo; serán reemplazados por datos reales en un sprint posterior.
    val summary = listOf(
        "Pedidos nuevos" to "0",
        "En preparación" to "0",
        "Listos" to "0",
        "Ventas de hoy" to "$0"
    )

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
                    IngredientGlassCard(modifier = Modifier.fillMaxWidth()) {
                        Text("Modo administrador", color = CreamText, fontWeight = FontWeight.Bold)
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        summary.chunked(2).forEach { rowItems ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                rowItems.forEach { (label, value) ->
                                    IngredientGlassCard(modifier = Modifier.weight(1f)) {
                                        Text(label, color = CreamText, style = MaterialTheme.typography.bodyMedium)
                                        Text(value, color = CreamText, style = MaterialTheme.typography.headlineSmall,
                                            fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
                items(modules) { module ->
                    Button(
                        onClick = { scope.launch { snackbarHostState.showSnackbar("Disponible en un próximo sprint") } },
                        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 76.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SoftBeige, contentColor = DeepBrown)
                    ) {
                        Icon(module.icon, contentDescription = null, modifier = Modifier.size(30.dp))
                        Column(modifier = Modifier.weight(1f).padding(start = 16.dp),
                            horizontalAlignment = Alignment.Start) {
                            Text(module.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(module.description, style = MaterialTheme.typography.bodyMedium)
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
