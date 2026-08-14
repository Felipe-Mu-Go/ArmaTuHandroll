package com.armatuhandroll.ui.screens.order

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.armatuhandroll.core.util.formatPrice
import com.armatuhandroll.ui.AppBackground
import com.armatuhandroll.ui.components.IngredientGlassCard
import com.armatuhandroll.ui.components.ConnectivityBanner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OrderConfirmationScreen(
    isConnected: Boolean,
    isConnectionAvailable: () -> Boolean,
    totalPaid: Int,
    totalProducts: Int,
    orderNumber: String,
    productsSummary: String,
    username: String,
    onContinueToPayment: () -> Unit
) {
    val estimatedTimeMinutes = totalProducts * 5

    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = Color.White,
            topBar = {
                CenterAlignedTopAppBar(
                    colors = androidx.compose.material3.TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = Color.White
                    ),
                    title = { Text("Confirmar pedido") }
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ConnectivityBanner(isConnected = isConnected)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(24.dp))
                    IngredientGlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp)
                    ) {
                    Text(
                        text = "Revisa los datos antes de confirmar tu pedido.",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Total a pagar: ${formatPrice(totalPaid)}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF8BF6A0)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Número de pedido: $orderNumber",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Nombre: $username",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Tiempo estimado: $estimatedTimeMinutes minutos",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                    Spacer(modifier = Modifier.weight(1f))
                    Button(
                    onClick = {
                        if (isConnected && isConnectionAvailable()) onContinueToPayment()
                    },
                    enabled = username.isNotBlank() && isConnected,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFE9D8B4),
                        contentColor = Color.Black
                    )
                    ) {
                            Text(
                                text = "Continuar al pago",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                    }
                }
            }
        }
    }
}
