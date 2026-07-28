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
import com.armatuhandroll.formatPrice
import com.armatuhandroll.ui.AppBackground
import com.armatuhandroll.ui.components.IngredientCategory
import com.armatuhandroll.ui.components.IngredientGlassCard
import com.armatuhandroll.ui.components.PrimaryActionButton
import com.armatuhandroll.ui.components.SecondaryActionButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CustomizedProductScreen(
    product: Product,
    config: ProductCustomizationConfig,
    initialCustomization: IngredientCustomization?,
    initialQuantity: Int,
    isEditing: Boolean,
    proteinOptions: List<String>,
    baseOptions: List<String>,
    vegetableOptions: List<String>,
    hasIncludedRemovableBases: Boolean,
    backgroundRes: Int,
    onFinishSelection: (IngredientCustomization, Int) -> Unit,
    onBack: () -> Unit
) {
    val selectedProteins = remember(initialCustomization) { mutableStateListOf<String>().apply { addAll(initialCustomization?.proteins.orEmpty()) } }
    val selectedBases = remember(initialCustomization, product.name) {
        mutableStateListOf<String>().apply {
            val initialBases = initialCustomization?.bases
                ?: if (hasIncludedRemovableBases) baseOptions else emptyList()
            addAll(initialBases)
        }
    }
    val selectedVegetables = remember(initialCustomization) { mutableStateListOf<String>().apply { addAll(initialCustomization?.vegetables.orEmpty()) } }
    var quantity by remember(initialQuantity, isEditing) {
        mutableStateOf(if (isEditing) initialQuantity.coerceAtLeast(1) else initialQuantity.coerceAtLeast(0))
    }

    fun toggleSelection(bucket: MutableList<String>, ingredient: String) {
        if (bucket.contains(ingredient)) bucket.remove(ingredient) else bucket.add(ingredient)
    }

    val customization = IngredientCustomization(
        proteins = selectedProteins.toList(),
        bases = selectedBases.toList(),
        vegetables = selectedVegetables.toList(),
        chargeBaseExtras = !hasIncludedRemovableBases
    )
    val hasValidIngredients = when (product.name) {
        "Handroll", "SushiBurger", "SushiPleto", "Gohan" -> {
            selectedProteins.isNotEmpty() || selectedBases.isNotEmpty() || selectedVegetables.isNotEmpty()
        }
        else -> {
            selectedProteins.isNotEmpty() && selectedBases.isNotEmpty() && selectedVegetables.isNotEmpty()
        }
    }
    val finalPrice = product.price + customization.totalExtra

    AppBackground(backgroundRes = backgroundRes) {
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
                    title = { Text(if (isEditing) "Edita tu ${product.name}" else "Personaliza tu ${product.name}") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Text("⬅️")
                        }
                    }
                )
            }
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    IngredientGlassCard(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp)) {
                        Text(
                            "Precio base: ${formatPrice(product.price)}",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                if (config.fixedIngredients.isNotEmpty()) {
                    item {
                        IngredientGlassCard {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    "Base fija incluida",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                                config.fixedIngredients.forEach { ingredient ->
                                    Text("• $ingredient", color = Color.White)
                                }
                                Text("Estos ingredientes no generan costo adicional.", color = Color.White.copy(alpha = 0.92f))
                            }
                        }
                    }
                }
                item {
                    IngredientGlassCard(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                "Cantidad",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                SecondaryActionButton(
                                    text = "−",
                                    onClick = { if (quantity > 0) quantity-- },
                                    modifier = Modifier.defaultMinSize(minWidth = 54.dp)
                                )
                                Text(
                                    text = quantity.toString(),
                                    modifier = Modifier.padding(horizontal = 18.dp),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                                SecondaryActionButton(
                                    text = "+",
                                    onClick = { quantity++ },
                                    modifier = Modifier.defaultMinSize(minWidth = 54.dp)
                                )
                            }
                        }
                    }
                }
                item {
                    IngredientCategory(
                        title = "Proteínas",
                        subtitle = "1 sin costo, extras +$1.000",
                        options = proteinOptions,
                        selected = selectedProteins,
                        onToggle = { toggleSelection(selectedProteins, it) }
                    )
                }
                item {
                    val baseTitle = if (hasIncludedRemovableBases) "Base incluida" else "Bases"
                    val baseSubtitle = if (hasIncludedRemovableBases) {
                        "Desmarca para quitar. No modifica el precio."
                    } else {
                        "1 sin costo, segunda +$1.000"
                    }
                    IngredientCategory(
                        title = baseTitle,
                        subtitle = baseSubtitle,
                        options = baseOptions,
                        selected = selectedBases,
                        onToggle = { toggleSelection(selectedBases, it) }
                    )
                }
                item {
                    IngredientCategory(
                        title = "Vegetales",
                        subtitle = "1 sin costo, extras +$500",
                        options = vegetableOptions,
                        selected = selectedVegetables,
                        onToggle = { toggleSelection(selectedVegetables, it) }
                    )
                }
                item {
                    IngredientGlassCard(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "Resumen de extras",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Text("Extra proteínas: ${formatPrice(customization.proteinExtra)}", color = Color.White)
                            Text("Extra base: ${formatPrice(customization.baseExtra)}", color = Color.White)
                            Text("Extra vegetales: ${formatPrice(customization.vegetableExtra)}", color = Color.White)
                            Text("Total adicional: ${formatPrice(customization.totalExtra)}", color = Color.White)
                            Text(
                                "Total final ${product.name} x$quantity: ${formatPrice(finalPrice * quantity)}",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                item {
                    PrimaryActionButton(
                        text = if (isEditing) "Guardar cambios" else "Finalizar selección",
                        onClick = { onFinishSelection(customization, quantity) },
                        enabled = quantity > 0 && hasValidIngredients,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
