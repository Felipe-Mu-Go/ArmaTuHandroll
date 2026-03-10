package com.armatuhandroll

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.armatuhandroll.ui.theme.ArmaTuHandrollTheme
import kotlinx.coroutines.delay
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

data class Product(
    val id: Int,
    val name: String,
    val price: Int,
    val description: String
)

data class HandrollCustomization(
    val proteins: List<String>,
    val bases: List<String>,
    val vegetables: List<String>
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

data class CartItem(
    val name: String,
    val unitPrice: Int,
    val details: List<String> = emptyList()
)

private object CartManager {
    val items = mutableStateListOf<CartItem>()

    fun addProduct(product: Product) {
        items.add(CartItem(name = product.name, unitPrice = product.price))
    }

    fun addHandroll(product: Product, customization: HandrollCustomization) {
        val finalPrice = product.price + customization.totalExtra
        val detailLines = listOf(
            "Proteínas: ${customization.proteins.joinToString().ifEmpty { "Sin selección" }}",
            "Bases: ${customization.bases.joinToString().ifEmpty { "Sin selección" }}",
            "Vegetales: ${customization.vegetables.joinToString().ifEmpty { "Sin selección" }}",
            "Extra proteínas: ${formatPrice(customization.proteinExtra)}",
            "Extra bases: ${formatPrice(customization.baseExtra)}",
            "Extra vegetales: ${formatPrice(customization.vegetableExtra)}",
            "Total adicional: ${formatPrice(customization.totalExtra)}"
        )
        items.add(CartItem(name = product.name, unitPrice = finalPrice, details = detailLines))
    }

    fun groupedItems(): Map<CartItem, Int> = items.groupingBy { it }.eachCount()

    fun total(): Int = items.sumOf { it.unitPrice }
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
        name = "Sushiburger",
        price = 5500,
        description = "Incluye arroz y nori. Elige tu proteína favorita, " +
            "una base cremosa y vegetales frescos."
    ),
    Product(
        id = 3,
        name = "Sushipleto",
        price = 5000,
        description = "Base de arroz y nori relleno con una proteína, " +
            "una base cremosa y un vegetal fresco."
    ),
    Product(
        id = 4,
        name = "Sushipleto Vegetariano",
        price = 4800,
        description = "Producto vegetariano con vegetales frescos."
    )
)

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
    var pendingHandrollConfig by remember { mutableStateOf<HandrollCustomization?>(null) }
    val handroll = products.first { it.name == "Handroll" }

    NavHost(navController = navController, startDestination = "splash") {
        composable("splash") {
            SplashScreen(onSplashFinished = {
                navController.navigate("home") {
                    popUpTo("splash") { inclusive = true }
                }
            })
        }
        composable("home") {
            HomeScreen(navController)
        }
        composable("handroll") {
            HandrollScreen(
                product = handroll,
                onFinishSelection = { customization ->
                    pendingHandrollConfig = customization
                    navController.navigate("handroll_summary")
                },
                onBack = { navController.popBackStack() }
            )
        }
        composable("handroll_summary") {
            val customization = pendingHandrollConfig
            if (customization == null) {
                navController.popBackStack()
            } else {
                HandrollSummaryScreen(
                    product = handroll,
                    customization = customization,
                    onSendOrder = {
                        CartManager.addHandroll(handroll, customization)
                        pendingHandrollConfig = null
                        navController.navigate("cart") {
                            popUpTo("home")
                        }
                    },
                    onContinueShopping = {
                        pendingHandrollConfig = null
                        navController.navigate("home") {
                            popUpTo("home") { inclusive = true }
                        }
                    }
                )
            }
        }
        composable("cart") {
            CartScreen(navController)
        }
    }
}

@Composable
private fun SplashScreen(onSplashFinished: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(2000)
        onSplashFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surface
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "🍣 🌊 ✨ 🥢", fontSize = 28.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Arma Tu Handroll",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(navController: NavHostController) {
    val itemsInCart = remember { CartManager.items }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Arma Tu Handroll") },
                navigationIcon = { Text("🍣", modifier = Modifier.padding(start = 12.dp), fontSize = 24.sp) },
                actions = {
                    IconButton(onClick = { navController.navigate("cart") }) {
                        Icon(Icons.Default.ShoppingCart, contentDescription = "Carrito")
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
                modifier = Modifier.padding(16.dp)
            )
            Text(
                text = "Productos en carrito: ${itemsInCart.size}",
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.secondary
            )
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(products) { product ->
                    ProductCard(
                        product = product,
                        onAdd = {
                            if (product.name == "Handroll") {
                                navController.navigate("handroll")
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
private fun HandrollScreen(
    product: Product,
    onFinishSelection: (HandrollCustomization) -> Unit,
    onBack: () -> Unit
) {
    val selectedProteins = remember { mutableStateListOf<String>() }
    val selectedBases = remember { mutableStateListOf<String>() }
    val selectedVegetables = remember { mutableStateListOf<String>() }

    fun toggleSelection(bucket: MutableList<String>, ingredient: String) {
        if (bucket.contains(ingredient)) bucket.remove(ingredient) else bucket.add(ingredient)
    }

    val customization = HandrollCustomization(
        proteins = selectedProteins.toList(),
        bases = selectedBases.toList(),
        vegetables = selectedVegetables.toList()
    )
    val finalPrice = product.price + customization.totalExtra

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Personaliza tu Handroll") },
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
            item {
                IngredientCategory(
                    title = "Proteína",
                    subtitle = "1 sin costo, extras +$1.000",
                    options = listOf("Camarón", "Carne", "Kanikama", "Palmito", "Champiñón"),
                    selected = selectedProteins,
                    onToggle = { toggleSelection(selectedProteins, it) }
                )
            }
            item {
                IngredientCategory(
                    title = "Base",
                    subtitle = "1 sin costo, segunda +$1.000",
                    options = listOf("Palta", "Queso crema"),
                    selected = selectedBases,
                    onToggle = { toggleSelection(selectedBases, it) }
                )
            }
            item {
                IngredientCategory(
                    title = "Vegetales",
                    subtitle = "1 sin costo, extras +$500",
                    options = listOf("Cebollín", "Ciboulette", "Choclo"),
                    selected = selectedVegetables,
                    onToggle = { toggleSelection(selectedVegetables, it) }
                )
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Resumen de extras", style = MaterialTheme.typography.titleMedium)
                        Text("Extra proteínas: ${formatPrice(customization.proteinExtra)}")
                        Text("Extra base: ${formatPrice(customization.baseExtra)}")
                        Text("Extra vegetales: ${formatPrice(customization.vegetableExtra)}")
                        Text("Total adicional: ${formatPrice(customization.totalExtra)}")
                        Text(
                            "Total final Handroll: ${formatPrice(finalPrice)}",
                            style = MaterialTheme.typography.titleMedium,
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
                    Text("Finalizar selección")
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
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
            options.forEach { option ->
                val isSelected = selected.contains(option)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggle(option) }
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(option)
                    Text(if (isSelected) "✓" else "○")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HandrollSummaryScreen(
    product: Product,
    customization: HandrollCustomization,
    onSendOrder: () -> Unit,
    onContinueShopping: () -> Unit
) {
    val finalPrice = product.price + customization.totalExtra

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(title = { Text("Resumen Handroll") })
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Producto: ${product.name}", style = MaterialTheme.typography.titleLarge)
            Text("Proteínas: ${customization.proteins.joinToString().ifEmpty { "Sin selección" }}")
            Text("Bases: ${customization.bases.joinToString().ifEmpty { "Sin selección" }}")
            Text("Vegetales: ${customization.vegetables.joinToString().ifEmpty { "Sin selección" }}")
            Text("Costo extra proteínas: ${formatPrice(customization.proteinExtra)}")
            Text("Costo extra base: ${formatPrice(customization.baseExtra)}")
            Text("Costo extra vegetales: ${formatPrice(customization.vegetableExtra)}")
            Text("Total adicional: ${formatPrice(customization.totalExtra)}")
            Text("Total final del producto: ${formatPrice(finalPrice)}", fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.weight(1f))
            Button(onClick = onSendOrder, modifier = Modifier.fillMaxWidth()) {
                Text("Enviar pedido")
            }
            Button(onClick = onContinueShopping, modifier = Modifier.fillMaxWidth()) {
                Text("Continuar comprando")
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
private fun CartScreen(navController: NavHostController) {
    val grouped = CartManager.groupedItems().toList()
    val total = CartManager.total()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Carrito de compra") },
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
            if (grouped.isEmpty()) {
                Text("Tu carrito está vacío.")
            } else {
                grouped.forEach { (product, quantity) ->
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${product.name} x$quantity")
                            Text(formatPrice(product.unitPrice * quantity))
                        }
                        product.details.forEach { detail ->
                            Text("• $detail", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text("Subtotal: ${formatPrice(total)}", style = MaterialTheme.typography.titleMedium)
            Text("Total general: ${formatPrice(total)}", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.weight(1f))
            Button(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                Text("Pagar")
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
