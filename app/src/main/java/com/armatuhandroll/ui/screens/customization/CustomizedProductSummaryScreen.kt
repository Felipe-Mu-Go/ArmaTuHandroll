package com.armatuhandroll.ui.screens.customization

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
import com.armatuhandroll.domain.model.IngredientCustomization
import com.armatuhandroll.domain.model.Product
import com.armatuhandroll.domain.model.ProductCustomizationConfig
import com.armatuhandroll.core.util.formatPrice
import com.armatuhandroll.ui.AppBackground
import com.armatuhandroll.ui.components.PrimaryActionButton
import com.armatuhandroll.ui.components.SecondaryActionButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CustomizedProductSummaryScreen(
    product: Product,
    config: ProductCustomizationConfig,
    customization: IngredientCustomization,
    quantity: Int,
    isEditing: Boolean,
    fixedIngredients: List<String>,
    hasIncludedRemovableBases: Boolean,
    onSaveAndGoToCart: () -> Unit,
    onSaveAndContinueShopping: () -> Unit
) {
    val finalPrice = product.price + customization.totalExtra
    val totalPrice = finalPrice * quantity

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
                    title = { Text(if (isEditing) "Resumen de edición ${product.name}" else "Resumen ${product.name}") }
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Producto solicitado: ${product.name}", style = MaterialTheme.typography.titleLarge)
                if (config.fixedIngredients.isNotEmpty()) {
                    Text("Base fija del plato:")
                    fixedIngredients.forEach { ingredient ->
                        Text("• $ingredient")
                    }
                }
                Text("Proteínas: ${customization.proteins.joinToString().ifEmpty { "Sin selección" }}")
                if (hasIncludedRemovableBases) {
                    Text("Palta: ${if (customization.bases.contains("Palta")) "Con palta" else "Sin palta"}")
                    Text("Queso crema: ${if (customization.bases.contains("Queso crema")) "Con queso crema" else "Sin queso crema"}")
                } else {
                    Text("Bases: ${customization.bases.joinToString().ifEmpty { "Sin selección" }}")
                }
                Text("Vegetales: ${customization.vegetables.joinToString().ifEmpty { "Sin selección" }}")
                Text("Costo extra proteínas: ${formatPrice(customization.proteinExtra)}")
                Text("Costo extra base: ${formatPrice(customization.baseExtra)}")
                Text("Costo extra vegetales: ${formatPrice(customization.vegetableExtra)}")
                Text("Total adicional: ${formatPrice(customization.totalExtra)}")
                Text("Precio base: ${formatPrice(product.price)}")
                Text("Cantidad: $quantity")
                Text("Total final por unidad: ${formatPrice(finalPrice)}", fontWeight = FontWeight.Bold)
                Text("Total por $quantity unidades: ${formatPrice(totalPrice)}", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.weight(1f))
                SecondaryActionButton(
                    text = if (isEditing) "Actualizar y seguir comprando" else "Agregar y seguir comprando",
                    onClick = onSaveAndContinueShopping,
                    modifier = Modifier.fillMaxWidth()
                )
                PrimaryActionButton(
                    text = if (isEditing) "Actualizar e ir al carrito" else "Agregar e ir al carrito",
                    onClick = onSaveAndGoToCart,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
