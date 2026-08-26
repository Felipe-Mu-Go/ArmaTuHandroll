package com.armatuhandroll.ui.screens.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.armatuhandroll.core.util.formatPrice
import com.armatuhandroll.domain.model.AdminPayment
import com.armatuhandroll.domain.model.PaymentMethod
import com.armatuhandroll.domain.repository.AdminOrdersRepository
import com.armatuhandroll.ui.AppBackground
import com.armatuhandroll.ui.components.IngredientGlassCard
import com.armatuhandroll.ui.theme.CreamText

@Composable
internal fun AdminCashScreen(ordersRepository: AdminOrdersRepository, onBack: () -> Unit) {
    var payments by remember { mutableStateOf<List<AdminPayment>>(emptyList()) }
    LaunchedEffect(ordersRepository) { ordersRepository.getPayments().onSuccess { payments = it } }
    val todayPayments = payments.filter { it.paymentStatus == "confirmed" && it.isToday }
    val cash = todayPayments.filter { it.paymentMethod == PaymentMethod.CASH }.sumOf { it.amount }
    val transfers = todayPayments.filter { it.paymentMethod == PaymentMethod.TRANSFER }.sumOf { it.amount }
    val webpay = todayPayments.filter { it.paymentMethod == PaymentMethod.WEBPAY }.sumOf { it.amount }

    AppBackground {
        LazyColumn(
            modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Volver", tint = CreamText) }
                Text("Caja", style = MaterialTheme.typography.headlineMedium, color = CreamText, fontWeight = FontWeight.Bold)
                Text("Resumen de hoy", color = CreamText)
            }
            item {
                IngredientGlassCard {
                    Text("Total vendido: ${formatPrice(cash + transfers + webpay)}", color = CreamText)
                    Text("Efectivo: ${formatPrice(cash)}", color = CreamText)
                    Text("Transferencias: ${formatPrice(transfers)}", color = CreamText)
                    Text("Webpay: ${formatPrice(webpay)}", color = CreamText)
                    Text("Pagos: ${todayPayments.size}", color = CreamText)
                }
            }
            items(todayPayments) { payment ->
                IngredientGlassCard {
                    Text(payment.orderNumber, color = CreamText, fontWeight = FontWeight.Bold)
                    Text(payment.dateTime.substringAfter(" ").take(5), color = CreamText)
                    Text(payment.paymentMethod.displayName, color = CreamText)
                    Text(formatPrice(payment.amount), color = CreamText)
                }
            }
        }
    }
}
