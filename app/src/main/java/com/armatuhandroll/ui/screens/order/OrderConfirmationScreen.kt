package com.armatuhandroll.ui.screens.order

import android.util.Log
import android.widget.Toast
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
import androidx.compose.ui.platform.LocalContext
import com.armatuhandroll.core.util.formatPrice
import com.armatuhandroll.ui.AppBackground
import com.armatuhandroll.ui.components.IngredientGlassCard
import com.armatuhandroll.ui.components.ConnectivityBanner
import kotlinx.coroutines.launch

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
    sendOrder: suspend (String, String, Int, Int, String, String) -> Result<Unit>,
    onOrderSent: () -> Unit
) {
    val estimatedTimeMinutes = totalProducts * 5
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isSending by rememberSaveable {
        mutableStateOf(false)
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
                        if (isSending) {
                            return@Button
                        }
                        val hasConnection = isConnected && isConnectionAvailable()
                        if (!hasConnection) {
                            Toast.makeText(
                                context,
                                "Sin conexión a Internet. Revisa tu Wi-Fi o datos móviles e inténtalo nuevamente.",
                                Toast.LENGTH_SHORT
                            ).show()
                            return@Button
                        }

                        isSending = true
                        coroutineScope.launch {
                            try {
                                Log.d("OrderSheets", "Iniciando envío de pedido: orderNumber=$orderNumber, totalProducts=$totalProducts, totalPaid=$totalPaid")
                                val sendResult = try {
                                    sendOrder(
                                        orderNumber,
                                        productsSummary,
                                        totalProducts,
                                        totalPaid,
                                        "$estimatedTimeMinutes minutos",
                                        username.trim()
                                    )
                                } catch (exception: Exception) {
                                    Result.failure(exception)
                                }

                                if (sendResult.isSuccess) {
                                    Log.i("OrderSheets", "Pedido enviado con éxito a Google Sheets: orderNumber=$orderNumber")
                                    Toast.makeText(context, "Pedido enviado a Google Sheets ✅", Toast.LENGTH_SHORT).show()
                                    onOrderSent()
                                } else {
                                    val error = sendResult.exceptionOrNull()
                                    Log.e("OrderSheets", "Error enviando pedido a Google Sheets: orderNumber=$orderNumber", error)
                                    Toast.makeText(context, "Error al enviar pedido ❌", Toast.LENGTH_SHORT).show()
                                }
                            } finally {
                                isSending = false
                            }
                        }
                    },
                    enabled = username.isNotBlank() && !isSending,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFE9D8B4),
                        contentColor = Color.Black
                    )
                    ) {
                        if (isSending) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                            Text("Enviando pedido...")
                        }
                        } else {
                            Text(
                                text = "Confirmar pedido",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}
