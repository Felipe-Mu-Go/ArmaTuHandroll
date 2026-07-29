package com.armatuhandroll.ui.screens.order

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.armatuhandroll.core.util.formatPrice
import com.armatuhandroll.domain.model.OrderStatus
import com.armatuhandroll.ui.AppBackground
import com.armatuhandroll.ui.components.IngredientGlassCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OrderSentScreen(
    totalPaid: Int,
    orderNumber: String,
    username: String,
    estimatedTimeMinutes: Int,
    onBackToMenu: () -> Unit
) {
    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = Color.White,
            topBar = {
                CenterAlignedTopAppBar(
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = Color.White
                    ),
                    title = { Text("Pedido enviado") }
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(24.dp))
                IngredientGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp)
                ) {
                    Text(
                        text = "Tu pedido fue enviado correctamente.",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Total: ${formatPrice(totalPaid)}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF8BF6A0)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Número de pedido: $orderNumber", style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Nombre: $username", style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        "Tiempo estimado: $estimatedTimeMinutes minutos",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text("Estado del pedido", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = OrderStatus.PENDING_REVIEW.displayName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFC857)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "El local revisará tu pedido antes de comenzar la preparación.",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Button(
                    onClick = onBackToMenu,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFE9D8B4),
                        contentColor = Color.Black
                    )
                ) {
                    Text(
                        text = "Volver al menú",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
