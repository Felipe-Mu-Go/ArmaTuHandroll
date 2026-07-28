package com.armatuhandroll.ui.screens.cart

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
import com.armatuhandroll.domain.model.CartItem
import com.armatuhandroll.core.util.formatPrice
import com.armatuhandroll.ui.AppBackground
import com.armatuhandroll.ui.components.IngredientGlassCard
import com.armatuhandroll.ui.components.PrimaryActionButton
import com.armatuhandroll.ui.components.SecondaryActionButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CartScreen(
    cartItems: List<CartItem>,
    total: Int,
    onBack: () -> Unit,
    onEditItem: (Int, CartItem) -> Unit,
    onRemoveItem: (Int) -> Unit,
    onCheckout: (String) -> Unit
) {
    var showCheckoutDialog by rememberSaveable { mutableStateOf(false) }
    var username by rememberSaveable { mutableStateOf("") }

    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = Color.White,
            topBar = {
                CenterAlignedTopAppBar(
                    colors = androidx.compose.material3.TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White
                    ),
                    title = { Text("Carrito de compra 🛍️", style = MaterialTheme.typography.titleLarge) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Text("⬅️")
                        }
                    }
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (cartItems.isEmpty()) {
                    IngredientGlassCard {
                        Text("Tu carrito está vacío.", style = MaterialTheme.typography.titleMedium)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        itemsIndexed(cartItems) { index, item ->
                            IngredientGlassCard(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp)) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(item.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                                        Text("${item.quantity} x ${formatPrice(item.unitPrice)}", fontWeight = FontWeight.Bold, color = Color(0xFF8BF6A0))
                                    }
                                    Text("Subtotal ítem: ${formatPrice(item.unitPrice * item.quantity)}", fontWeight = FontWeight.Bold)
                                    item.details.forEach { detail ->
                                        Text("• $detail", style = MaterialTheme.typography.bodySmall)
                                    }
                                    if (item.customization != null) {
                                        SecondaryActionButton(
                                            text = "Editar",
                                            onClick = { onEditItem(index, item) },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                    SecondaryActionButton(
                                        text = "Eliminar",
                                        onClick = { onRemoveItem(index) },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                IngredientGlassCard(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp)) {
                    Text("Subtotal: ${formatPrice(total)}", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Total general: ${formatPrice(total)}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                PrimaryActionButton(
                    text = "Finalizar compra",
                    onClick = {
                        username = ""
                        showCheckoutDialog = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = cartItems.isNotEmpty()
                )
            }
        }

        if (showCheckoutDialog) {
            AlertDialog(
                onDismissRequest = { showCheckoutDialog = false },
                title = { Text("Finalizar compra") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Ingrese su nombre para retiro")
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = { Text("Nombre para retiro") },
                            singleLine = true
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val trimmedUsername = username.trim()
                            if (trimmedUsername.isNotEmpty()) {
                                onCheckout(trimmedUsername)
                                showCheckoutDialog = false
                                username = ""
                            }
                        },
                        enabled = username.trim().isNotEmpty()
                    ) {
                        Text("Enviar")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCheckoutDialog = false }) {
                        Text("Cancelar")
                    }
                }
            )
        }
    }
}
