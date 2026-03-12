package com.armatuhandroll

import android.os.Bundle
import androidx.annotation.DrawableRes
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.armatuhandroll.ui.AnimatedBrandTitle
import com.armatuhandroll.ui.AppBackground
import com.armatuhandroll.ui.theme.ArmaTuHandrollTheme
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

data class Product(
    val id: Int,
    val name: String,
    val price: Int,
    val description: String
)

data class IngredientCustomization(
    val proteins: List<String>,
    val bases: List<String>,
    val vegetables: List<String>,
    val includeRice: Boolean = true
) {
    val proteinExtra: Int
        get() = (proteins.size - 1).coerceAtLeast(0) * 1000

    val baseExtra: Int
        get() = (bases.size - 1).coerceAtLeast(0) * 1000

    val vegetableExtra: Int
        get() = (vegetables.size - 1).coerceAtLeast(0) * 500

    val totalExtra: Int
        get() = proteinExtra + baseExtra + vegetableExtra
}

data class ProductCustomizationConfig(
    val fixedIngredients: List<String> = emptyList()
)

data class CartItem(
    val productId: Int,
    val name: String,
    val unitPrice: Int,
    val customization: IngredientCustomization? = null,
    val fixedIngredients: List<String> = emptyList(),
    val details: List<String> = emptyList()
)

private object CartManager {
    val items = mutableStateListOf<CartItem>()

    fun addProduct(product: Product) {
        items.add(CartItem(productId = product.id, name = product.name, unitPrice = product.price))
    }

    private fun customizedCartItem(
        product: Product,
        customization: IngredientCustomization,
        fixedIngredients: List<String> = emptyList()
    ): CartItem {
        val finalPrice = product.price + customization.totalExtra
        val fixedIngredientsLine = if (fixedIngredients.isNotEmpty()) {
            listOf("Base fija: ${fixedIngredients.joinToString()}")
        } else {
            emptyList()
        }
        val riceLine = if (product.name == "Gohan") {
            listOf("Arroz: ${if (customization.includeRice) "Con arroz" else "Sin arroz"}")
        } else {
            emptyList()
        }
        val detailLines = fixedIngredientsLine + riceLine + listOf(
            "Proteínas: ${customization.proteins.joinToString().ifEmpty { "Sin selección" }}",
            "Bases: ${customization.bases.joinToString().ifEmpty { "Sin selección" }}",
            "Vegetales: ${customization.vegetables.joinToString().ifEmpty { "Sin selección" }}",
            "Extra proteínas: ${formatPrice(customization.proteinExtra)}",
            "Extra bases: ${formatPrice(customization.baseExtra)}",
            "Extra vegetales: ${formatPrice(customization.vegetableExtra)}",
            "Total adicional: ${formatPrice(customization.totalExtra)}"
        )
        return CartItem(
            productId = product.id,
            name = product.name,
            unitPrice = finalPrice,
            customization = customization,
            fixedIngredients = fixedIngredients,
            details = detailLines
        )
    }

    fun addCustomizedProduct(
        product: Product,
        customization: IngredientCustomization,
        fixedIngredients: List<String> = emptyList()
    ) {
        items.add(customizedCartItem(product, customization, fixedIngredients))
    }

    fun updateCustomizedProduct(
        index: Int,
        product: Product,
        customization: IngredientCustomization,
        fixedIngredients: List<String> = emptyList()
    ) {
        if (index in items.indices) {
            items[index] = customizedCartItem(product, customization, fixedIngredients)
        }
    }

    fun groupedItems(): Map<CartItem, Int> = items.groupingBy { it }.eachCount()

    fun total(): Int = items.sumOf { it.unitPrice }

    fun clear() {
        items.clear()
    }
}

private val products = listOf(
    Product(
        id = 1,
        name = "Handroll",
        price = 3500,
        description = "Incluye hasta 1 proteína, 1 base y 1 vegetal sin costo extra. " +
            "Proteína o base extra +$1.000. Vegetal extra +$500."
    ),
    Product(
        id = 2,
        name = "SushiBurger",
        price = 5500,
        description = "Incluye arroz y nori. Elige tu proteína favorita, " +
            "una base cremosa y vegetales frescos."
    ),
    Product(
        id = 3,
        name = "SushiPleto",
        price = 5000,
        description = "Funciona igual que Handroll: elige proteínas, bases y vegetales " +
            "con el mismo cálculo de extras."
    ),
    Product(
        id = 4,
        name = "Gohan",
        price = 6500,
        description = "Incluye cebollín y permite elegir con o sin arroz. " +
            "Personaliza proteínas, bases y vegetales con el mismo cálculo de extras."
    )
)

private val proteinOptions = listOf("Camarón", "Carne", "Kanikama", "Palmito", "Champiñón", "Pollo")
private val baseOptions = listOf("Palta", "Queso crema")
private val vegetableOptions = listOf("Cebollín", "Ciboulette", "Choclo")
private val customizableProductsConfig = mapOf(
    "Handroll" to ProductCustomizationConfig(),
    "SushiBurger" to ProductCustomizationConfig(),
    "SushiPleto" to ProductCustomizationConfig(),
    "Gohan" to ProductCustomizationConfig(fixedIngredients = listOf("Cebollín"))
)

private fun fixedIngredientsFor(product: Product, customization: IngredientCustomization): List<String> {
    val defaultIngredients = customizableProductsConfig[product.name]?.fixedIngredients.orEmpty()
    return if (product.name == "Gohan" && customization.includeRice) {
        listOf("Arroz") + defaultIngredients
    } else {
        defaultIngredients
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ArmaTuHandrollTheme {
                AppNavigation()
            }
        }
    }
}

@Composable
private fun AppNavigation() {
    val navController = rememberNavController()
    var pendingCustomization by remember { mutableStateOf<IngredientCustomization?>(null) }
    var pendingProduct by remember { mutableStateOf<Product?>(null) }
    var pendingEditIndex by remember { mutableStateOf<Int?>(null) }

    NavHost(navController = navController, startDestination = "splash") {
        composable("splash") {
            SplashScreen(navController)
        }
        composable("home") {
            HomeScreen(navController)
        }
        composable("customize/{productId}") { backStackEntry ->
            val productId = backStackEntry.arguments?.getString("productId")?.toIntOrNull()
            val product = products.firstOrNull { it.id == productId }
            val customizationConfig = product?.let { customizableProductsConfig[it.name] }
            if (product == null || customizationConfig == null) {
                navController.popBackStack()
            } else {
                CustomizedProductScreen(
                    product = product,
                    config = customizationConfig,
                    initialCustomization = null,
                    isEditing = false,
                    onFinishSelection = { customization ->
                        pendingCustomization = customization
                        pendingProduct = product
                        pendingEditIndex = null
                        navController.navigate("customized_summary")
                    },
                    onBack = { navController.popBackStack() }
                )
            }
        }
        composable("customize/{productId}/{editIndex}") { backStackEntry ->
            val productId = backStackEntry.arguments?.getString("productId")?.toIntOrNull()
            val editIndex = backStackEntry.arguments?.getString("editIndex")?.toIntOrNull()
            val product = products.firstOrNull { it.id == productId }
            val cartItem = editIndex?.let { idx -> CartManager.items.getOrNull(idx) }
            val customizationConfig = product?.let { customizableProductsConfig[it.name] }
            if (product == null || cartItem?.customization == null || customizationConfig == null) {
                navController.popBackStack()
            } else {
                CustomizedProductScreen(
                    product = product,
                    config = customizationConfig,
                    initialCustomization = cartItem.customization,
                    isEditing = true,
                    onFinishSelection = { customization ->
                        pendingCustomization = customization
                        pendingProduct = product
                        pendingEditIndex = editIndex
                        navController.navigate("customized_summary")
                    },
                    onBack = { navController.popBackStack() }
                )
            }
        }
        composable("customized_summary") {
            val customization = pendingCustomization
            val product = pendingProduct
            val editIndex = pendingEditIndex
            if (customization == null || product == null) {
                navController.navigateToHome()
            } else {
                val saveAction = {
                    val fixedIngredients = fixedIngredientsFor(product, customization)
                    if (editIndex == null) {
                        CartManager.addCustomizedProduct(product, customization, fixedIngredients)
                    } else {
                        CartManager.updateCustomizedProduct(editIndex, product, customization, fixedIngredients)
                    }
                    pendingCustomization = null
                    pendingProduct = null
                    pendingEditIndex = null
                }
                CustomizedProductSummaryScreen(
                    product = product,
                    config = customizableProductsConfig[product.name] ?: ProductCustomizationConfig(),
                    customization = customization,
                    isEditing = editIndex != null,
                    onSaveAndGoToCart = {
                        saveAction()
                        navController.navigate("cart") {
                            popUpTo("home")
                        }
                    },
                    onSaveAndContinueShopping = {
                        saveAction()
                        navController.navigateToHome()
                    }
                )
            }
        }
        composable("cart") {
            CartScreen(
                navController = navController,
                onEditItem = { index, item ->
                    pendingEditIndex = index
                    pendingProduct = products.firstOrNull { it.id == item.productId }
                    pendingCustomization = item.customization
                    navController.navigate("customize/${item.productId}/$index")
                },
                onCheckout = {
                    pendingCustomization = null
                    pendingProduct = null
                    pendingEditIndex = null
                    CartManager.clear()
                    navController.navigate("home") {
                        popUpTo("home") { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }
    }
}


@DrawableRes
private fun Product.customizationBackgroundRes(): Int = when (name) {
    "Handroll" -> R.drawable.handrroll
    "Gohan" -> R.drawable.gohan
    "SushiBurger" -> R.drawable.sushiburger
    else -> R.drawable.fondo
}

private fun NavHostController.navigateToHome() {
    navigate("home") {
        popUpTo(graph.startDestinationId) { inclusive = false }
        launchSingleTop = true
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(navController: NavHostController) {
    val itemsInCart = remember { CartManager.items }

    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = Color.White,
            topBar = {
                CenterAlignedTopAppBar(
                    colors = androidx.compose.material3.TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White,
                        actionIconContentColor = Color.White
                    ),
                    title = { AnimatedBrandTitle() },
                    navigationIcon = { Text("🍣", modifier = Modifier.padding(start = 12.dp), fontSize = 24.sp) },
                    actions = {
                        IconButton(onClick = { navController.navigate("cart") }) {
                            Text("🛍️", fontSize = 22.sp)
                        }
                    }
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
            Text(
                text = "Productos disponibles",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                modifier = Modifier.padding(16.dp)
            )
            Text(
                text = "Productos en carrito: ${itemsInCart.size}",
                modifier = Modifier.padding(horizontal = 16.dp),
                color = Color.White.copy(alpha = 0.9f)
            )
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(products) { product ->
                    ProductCard(
                        product = product,
                        onAdd = {
                            if (customizableProductsConfig.containsKey(product.name)) {
                                navController.navigate("customize/${product.id}")
                            } else {
                                CartManager.addProduct(product)
                            }
                        }
                    )
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomizedProductScreen(
    product: Product,
    config: ProductCustomizationConfig,
    initialCustomization: IngredientCustomization?,
    isEditing: Boolean,
    onFinishSelection: (IngredientCustomization) -> Unit,
    onBack: () -> Unit
) {
    val selectedProteins = remember(initialCustomization) { mutableStateListOf<String>().apply { addAll(initialCustomization?.proteins.orEmpty()) } }
    val selectedBases = remember(initialCustomization) { mutableStateListOf<String>().apply { addAll(initialCustomization?.bases.orEmpty()) } }
    val selectedVegetables = remember(initialCustomization) { mutableStateListOf<String>().apply { addAll(initialCustomization?.vegetables.orEmpty()) } }
    var includeRice by remember(initialCustomization, product.name) { mutableStateOf(initialCustomization?.includeRice ?: true) }

    fun toggleSelection(bucket: MutableList<String>, ingredient: String) {
        if (bucket.contains(ingredient)) bucket.remove(ingredient) else bucket.add(ingredient)
    }

    val customization = IngredientCustomization(
        proteins = selectedProteins.toList(),
        bases = selectedBases.toList(),
        vegetables = selectedVegetables.toList(),
        includeRice = includeRice
    )
    val finalPrice = product.price + customization.totalExtra

    AppBackground(backgroundRes = product.customizationBackgroundRes()) {
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
                    Text("Precio base: ${formatPrice(product.price)}", style = MaterialTheme.typography.titleMedium)
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
                if (product.name == "Gohan") {
                    item {
                        IngredientGlassCard {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Con arroz",
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Checkbox(
                                    checked = includeRice,
                                    onCheckedChange = { includeRice = it }
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
                    IngredientCategory(
                        title = "Bases",
                        subtitle = "1 sin costo, segunda +$1.000",
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
                    IngredientGlassCard {
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
                                "Total final ${product.name}: ${formatPrice(finalPrice)}",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                item {
                    Button(
                        onClick = { onFinishSelection(customization) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (isEditing) "Guardar cambios" else "Finalizar selección")
                    }
                }
            }
        }
    }
}

@Composable
private fun IngredientCategory(
    title: String,
    subtitle: String,
    options: List<String>,
    selected: List<String>,
    onToggle: (String) -> Unit
) {
    IngredientGlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.92f))
            options.forEach { option ->
                val isSelected = selected.contains(option)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggle(option) }
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(option, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (isSelected) "✓" else "○", color = if (isSelected) Color(0xFF8BF6A0) else Color.White)
                }
            }
        }
    }
}

@Composable
private fun IngredientGlassCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.25f),
                shape = RoundedCornerShape(16.dp)
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Black.copy(alpha = 0.55f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomizedProductSummaryScreen(
    product: Product,
    config: ProductCustomizationConfig,
    customization: IngredientCustomization,
    isEditing: Boolean,
    onSaveAndGoToCart: () -> Unit,
    onSaveAndContinueShopping: () -> Unit
) {
    val finalPrice = product.price + customization.totalExtra

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
                if (product.name == "Gohan") {
                    Text("Arroz: ${if (customization.includeRice) "Con arroz" else "Sin arroz"}")
                }
                if (config.fixedIngredients.isNotEmpty()) {
                    Text("Base fija del plato:")
                    fixedIngredientsFor(product, customization).forEach { ingredient ->
                        Text("• $ingredient")
                    }
                }
                Text("Proteínas: ${customization.proteins.joinToString().ifEmpty { "Sin selección" }}")
                Text("Bases: ${customization.bases.joinToString().ifEmpty { "Sin selección" }}")
                Text("Vegetales: ${customization.vegetables.joinToString().ifEmpty { "Sin selección" }}")
                Text("Costo extra proteínas: ${formatPrice(customization.proteinExtra)}")
                Text("Costo extra base: ${formatPrice(customization.baseExtra)}")
                Text("Costo extra vegetales: ${formatPrice(customization.vegetableExtra)}")
                Text("Total adicional: ${formatPrice(customization.totalExtra)}")
                Text("Precio base: ${formatPrice(product.price)}")
                Text("Total final del producto: ${formatPrice(finalPrice)}", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.weight(1f))
                Button(onClick = onSaveAndGoToCart, modifier = Modifier.fillMaxWidth()) {
                    Text(if (isEditing) "Actualizar y volver al carrito" else "Agregar al carrito")
                }
                Button(onClick = onSaveAndContinueShopping, modifier = Modifier.fillMaxWidth()) {
                    Text(if (isEditing) "Actualizar y continuar comprando" else "Agregar y continuar comprando")
                }
            }
        }
    }
}

@Composable
private fun ProductCard(product: Product, onAdd: () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(product.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Precio: ${formatPrice(product.price)}", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text(product.description, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
                Text("Elegir ingredientes")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CartScreen(
    navController: NavHostController,
    onEditItem: (Int, CartItem) -> Unit,
    onCheckout: () -> Unit
) {
    val cartItems = remember { CartManager.items }
    val total = CartManager.total()

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
                    title = { Text("Carrito de compra 🛍️") },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
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
                    Text("Tu carrito está vacío.")
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        itemsIndexed(cartItems) { index, item ->
                            IngredientGlassCard {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(item.name, fontWeight = FontWeight.SemiBold)
                                        Text(formatPrice(item.unitPrice), fontWeight = FontWeight.Bold)
                                    }
                                    item.details.forEach { detail ->
                                        Text("• $detail", style = MaterialTheme.typography.bodySmall)
                                    }
                                    if (item.customization != null) {
                                        Button(
                                            onClick = { onEditItem(index, item) },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("Editar")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text("Subtotal: ${formatPrice(total)}", style = MaterialTheme.typography.titleMedium)
                Text("Total general: ${formatPrice(total)}", style = MaterialTheme.typography.titleLarge)
                Button(onClick = onCheckout, modifier = Modifier.fillMaxWidth()) {
                    Text("Finalizar compra")
                }
            }
        }
    }
}

private fun formatPrice(value: Int): String {
    val symbols = DecimalFormatSymbols(Locale("es", "CL")).apply {
        groupingSeparator = '.'
    }
    return "$" + DecimalFormat("#,###", symbols).format(value)
}
