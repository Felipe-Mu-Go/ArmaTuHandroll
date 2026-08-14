package com.armatuhandroll.ui.screens.checkout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.armatuhandroll.core.util.formatPrice
import com.armatuhandroll.domain.model.BankTransferConfig
import com.armatuhandroll.ui.AppBackground
import com.armatuhandroll.ui.components.IngredientGlassCard
import kotlinx.coroutines.launch

private enum class ClientPaymentMethod { TRANSFER }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CheckoutScreen(
    total: Int,
    orderNumber: String,
    isConnected: Boolean,
    submitTransfer: suspend () -> Result<Unit>,
    onCompleted: () -> Unit,
    onBack: () -> Unit
) {
    var selectedMethod by rememberSaveable { mutableStateOf<ClientPaymentMethod?>(null) }
    var isSubmitting by rememberSaveable { mutableStateOf(false) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = Color.White,
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("Finalizar compra") },
                    navigationIcon = { IconButton(onClick = onBack, enabled = !isSubmitting) { Text("←") } },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White
                    )
                )
            }
        ) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding).padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("Total del pedido", style = MaterialTheme.typography.titleMedium)
                Text(formatPrice(total), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color(0xFF8BF6A0))
                Text("¿Cómo quieres pagar?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                IngredientGlassCard(
                    modifier = Modifier.fillMaxWidth().clickable { selectedMethod = ClientPaymentMethod.TRANSFER }
                ) {
                    Text("Transferencia bancaria", fontWeight = FontWeight.Bold)
                    Text("Realiza una transferencia a nuestros datos bancarios y confirma cuando hayas terminado.")
                    RadioButton(selected = selectedMethod == ClientPaymentMethod.TRANSFER, onClick = { selectedMethod = ClientPaymentMethod.TRANSFER })
                }
                IngredientGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text("Webpay / Transbank", fontWeight = FontWeight.Bold)
                    Text("Débito, crédito y prepago")
                    Text("Próximamente", color = Color(0xFFFFC857), fontWeight = FontWeight.Bold)
                }
                if (selectedMethod == ClientPaymentMethod.TRANSFER) {
                    IngredientGlassCard(modifier = Modifier.fillMaxWidth()) {
                        Text("Datos para transferir", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        BankField("Banco", BankTransferConfig.BANK)
                        BankField("Tipo de cuenta", BankTransferConfig.ACCOUNT_TYPE)
                        BankField("Número de cuenta", BankTransferConfig.ACCOUNT_NUMBER)
                        BankField("Titular", BankTransferConfig.HOLDER_NAME)
                        BankField("RUT", BankTransferConfig.HOLDER_ID)
                        BankField("Correo", BankTransferConfig.EMAIL)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { clipboard.setText(AnnotatedString(BankTransferConfig.ACCOUNT_NUMBER)) }) { Text("Copiar cuenta") }
                            OutlinedButton(onClick = { clipboard.setText(AnnotatedString(BankTransferConfig.HOLDER_ID)) }) { Text("Copiar RUT") }
                        }
                    }
                    errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Button(
                        onClick = {
                            if (isSubmitting) return@Button
                            isSubmitting = true
                            errorMessage = null
                            scope.launch {
                                submitTransfer().fold(
                                    onSuccess = { onCompleted() },
                                    onFailure = { errorMessage = it.message ?: "No fue posible completar el checkout" }
                                )
                                isSubmitting = false
                            }
                        },
                        enabled = isConnected && !isSubmitting,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (isSubmitting) "Procesando..." else "Ya realicé la transferencia") }
                }
                Text("Pedido: $orderNumber", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun BankField(label: String, value: String) {
    Text("$label: $value", modifier = Modifier.padding(top = 6.dp))
}
