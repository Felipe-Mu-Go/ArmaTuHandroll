package com.armatuhandroll

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
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

private object CartManager {
    val items = mutableStateListOf<Product>()

    fun add(product: Product) {
        items.add(product)
    }

    fun groupedItems(): Map<Product, Int> = items.groupingBy { it }.eachCount()

    fun total(): Int = items.sumOf { it.price }
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
                    ProductCard(product = product, onAdd = { CartManager.add(product) })
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
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${product.name} x$quantity")
                        Text(formatPrice(product.price * quantity))
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
