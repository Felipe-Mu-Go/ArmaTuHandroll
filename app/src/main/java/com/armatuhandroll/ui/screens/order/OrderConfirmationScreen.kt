package com.armatuhandroll.ui.screens.order

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.armatuhandroll.formatPrice
import com.armatuhandroll.ui.AppBackground
import com.armatuhandroll.ui.components.IngredientGlassCard
import com.armatuhandroll.ui.components.PrimaryActionButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OrderConfirmationScreen(
    totalPaid: Int,
    totalProducts: Int,
    orderNumber: String,
    productsSummary: String,
    username: String,
    sendOrder: suspend (String, String, Int, Int, String, String) -> Result<Unit>,
    onBackToMenu: () -> Unit
) {
    val estimatedTimeMinutes = totalProducts * 5
    val context = LocalContext.current

    LaunchedEffect(orderNumber, totalProducts, totalPaid, productsSummary, username) {
        if (orderNumber.isNotBlank()) {
            Log.d("OrderSheets", "Iniciando envío de pedido: orderNumber=$orderNumber, totalProducts=$totalProducts, totalPaid=$totalPaid")
            val sendResult = sendOrder(
                orderNumber,
                productsSummary,
                totalProducts,
                totalPaid,
                "$estimatedTimeMinutes minutos",
                username.trim()
            )

            if (sendResult.isSuccess) {
                Log.i("OrderSheets", "Pedido enviado con éxito a Google Sheets: orderNumber=$orderNumber")
                Toast.makeText(context, "Pedido enviado a Google Sheets ✅", Toast.LENGTH_SHORT).show()
            } else {
                val error = sendResult.exceptionOrNull()
                Log.e("OrderSheets", "Error enviando pedido a Google Sheets: orderNumber=$orderNumber", error)
                Toast.makeText(context, "Error al enviar pedido ❌", Toast.LENGTH_SHORT).show()
            }
        } else {
            Log.w("OrderSheets", "Se omitió envío a Google Sheets porque orderNumber está vacío")
        }
    }

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
                    title = { Text("Pedido confirmado") }
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
                        text = "Tu pedido fue recibido correctamente.",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Total pagado: ${formatPrice(totalPaid)}",
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
                PrimaryActionButton(
                    text = "Volver al menú",
                    onClick = onBackToMenu,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
