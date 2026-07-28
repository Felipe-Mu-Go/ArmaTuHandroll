package com.armatuhandroll

import android.os.Bundle
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.armatuhandroll.domain.model.CartItem
import com.armatuhandroll.domain.model.IngredientCustomization
import com.armatuhandroll.domain.model.Product
import com.armatuhandroll.domain.model.ProductCustomizationConfig
import com.armatuhandroll.ui.screens.cart.CartScreen
import com.armatuhandroll.ui.screens.customization.CustomizedProductScreen
import com.armatuhandroll.ui.screens.customization.CustomizedProductSummaryScreen
import com.armatuhandroll.ui.screens.home.HomeScreen
import com.armatuhandroll.ui.screens.order.OrderConfirmationScreen
import com.armatuhandroll.ui.theme.ArmaTuHandrollTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

private object CartManager {
    val items = mutableStateListOf<CartItem>()

    fun addProduct(product: Product, quantity: Int = 1) {
        items.add(CartItem(productId = product.id, name = product.name, unitPrice = product.price, quantity = quantity))
    }

    private fun customizedCartItem(
        product: Product,
        customization: IngredientCustomization,
        quantity: Int,
        fixedIngredients: List<String> = emptyList()
    ): CartItem {
        val finalPrice = product.price + customization.totalExtra
        val fixedIngredientsLine = if (fixedIngredients.isNotEmpty()) {
            listOf("Base fija: ${fixedIngredients.joinToString()}")
        } else {
            emptyList()
        }
        val baseDetailLines = if (hasIncludedRemovableBases(product.name)) {
            listOf(
                "Palta: ${if (customization.bases.contains("Palta")) "Con palta" else "Sin palta"}",
                "Queso crema: ${if (customization.bases.contains("Queso crema")) "Con queso crema" else "Sin queso crema"}"
            )
        } else {
            listOf("Bases: ${customization.bases.joinToString().ifEmpty { "Sin selección" }}")
        }
        val detailLines = fixedIngredientsLine + listOf(
            "Proteínas: ${customization.proteins.joinToString().ifEmpty { "Sin selección" }}"
        ) + baseDetailLines + listOf(
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
            quantity = quantity,
            customization = customization,
            fixedIngredients = fixedIngredients,
            details = detailLines
        )
    }

    fun addCustomizedProduct(
        product: Product,
        customization: IngredientCustomization,
        quantity: Int,
        fixedIngredients: List<String> = emptyList()
    ) {
        items.add(customizedCartItem(product, customization, quantity, fixedIngredients))
    }

    fun updateCustomizedProduct(
        index: Int,
        product: Product,
        customization: IngredientCustomization,
        quantity: Int,
        fixedIngredients: List<String> = emptyList()
    ) {
        if (index in items.indices) {
            items[index] = customizedCartItem(product, customization, quantity, fixedIngredients)
        }
    }

    fun total(): Int = items.sumOf { it.unitPrice * it.quantity }

    fun removeItem(index: Int) {
        if (index in items.indices) {
            items.removeAt(index)
        }
    }

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
        description = "Incluye arroz, nori, palta y queso crema. " +
            "Puedes quitar palta o queso crema sin costo, y elegir proteínas y vegetales."
    ),
    Product(
        id = 3,
        name = "SushiPleto",
        price = 5000,
        description = "Incluye palta y queso crema en la base. " +
            "Puedes quitar una o ambas sin costo y personalizar proteínas y vegetales."
    ),
    Product(
        id = 4,
        name = "Gohan",
        price = 6500,
        description = "Incluye arroz en la base fija. " +
            "La palta y el queso crema vienen incluidos y son opcionales. " +
            "Personaliza proteínas y vegetales con el mismo cálculo de extras."
    )
)

private val proteinOptions = listOf("Camarón", "Carne", "Kanikama", "Palmito", "Champiñón", "Pollo")
private val baseOptions = listOf("Palta", "Queso crema")
private val vegetableOptions = listOf("Cebollín", "Ciboulette", "Choclo")
private val productsWithIncludedRemovableBases = setOf("SushiBurger", "SushiPleto", "Gohan")
private const val GoogleSheetsWebhookUrl = "https://script.google.com/macros/s/AKfycbzuA1_DjOwtrn0vl9pPEsfXExNFaLfW3akImx_Fd_nDMSxyTxYwRBOAk9sIMH4mbkPz7g/exec"
private const val OrderLogTag = "OrderSheets"

private fun hasIncludedRemovableBases(productName: String): Boolean =
    productName in productsWithIncludedRemovableBases

private val customizableProductsConfig = mapOf(
    "Handroll" to ProductCustomizationConfig(),
    "SushiBurger" to ProductCustomizationConfig(),
    "SushiPleto" to ProductCustomizationConfig(),
    "Gohan" to ProductCustomizationConfig(fixedIngredients = listOf("Arroz"))
)

private fun fixedIngredientsFor(product: Product, customization: IngredientCustomization): List<String> {
    return customizableProductsConfig[product.name]?.fixedIngredients.orEmpty()
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
    var pendingQuantity by remember { mutableStateOf(0) }
    var pendingEditIndex by remember { mutableStateOf<Int?>(null) }
    var pendingOrderTotal by remember { mutableStateOf(0) }
    var pendingOrderItemCount by remember { mutableStateOf(0) }
    var pendingOrderNumber by remember { mutableStateOf("") }
    var pendingOrderProducts by remember { mutableStateOf("") }
    var pendingOrderUsername by rememberSaveable { mutableStateOf("") }

    NavHost(navController = navController, startDestination = "splash") {
        composable("splash") {
            SplashScreen(navController)
        }
        composable("home") {
            HomeScreen(
                products = products,
                cartItemCount = CartManager.items.size,
                onCartClick = { navController.navigate("cart") },
                onProductClick = { product ->
                    if (customizableProductsConfig.containsKey(product.name)) {
                        navController.navigate("customize/${product.id}")
                    } else {
                        CartManager.addProduct(product)
                    }
                }
            )
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
                    initialQuantity = 0,
                    isEditing = false,
                    proteinOptions = proteinOptions,
                    baseOptions = baseOptions,
                    vegetableOptions = vegetableOptions,
                    hasIncludedRemovableBases = hasIncludedRemovableBases(product.name),
                    backgroundRes = product.customizationBackgroundRes(),
                    onFinishSelection = { customization, quantity ->
                        pendingCustomization = customization
                        pendingProduct = product
                        pendingQuantity = quantity
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
                    initialQuantity = cartItem.quantity,
                    isEditing = true,
                    proteinOptions = proteinOptions,
                    baseOptions = baseOptions,
                    vegetableOptions = vegetableOptions,
                    hasIncludedRemovableBases = hasIncludedRemovableBases(product.name),
                    backgroundRes = product.customizationBackgroundRes(),
                    onFinishSelection = { customization, quantity ->
                        pendingCustomization = customization
                        pendingProduct = product
                        pendingQuantity = quantity
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
            val quantity = pendingQuantity
            if (customization == null || product == null) {
                navController.navigateToHome()
            } else {
                val saveAction = {
                    val fixedIngredients = fixedIngredientsFor(product, customization)
                    if (editIndex == null) {
                        CartManager.addCustomizedProduct(product, customization, quantity, fixedIngredients)
                    } else {
                        CartManager.updateCustomizedProduct(editIndex, product, customization, quantity, fixedIngredients)
                    }
                }
                val clearPendingSelection = {
                    pendingCustomization = null
                    pendingProduct = null
                    pendingQuantity = 0
                    pendingEditIndex = null
                }
                CustomizedProductSummaryScreen(
                    product = product,
                    config = customizableProductsConfig[product.name] ?: ProductCustomizationConfig(),
                    customization = customization,
                    quantity = quantity,
                    isEditing = editIndex != null,
                    fixedIngredients = fixedIngredientsFor(product, customization),
                    hasIncludedRemovableBases = hasIncludedRemovableBases(product.name),
                    onSaveAndGoToCart = {
                        saveAction()
                        navController.navigate("cart") {
                            popUpTo("customized_summary") { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onSaveAndContinueShopping = {
                        saveAction()
                        navController.navigateToHome()
                        clearPendingSelection()
                    }
                )
            }
        }
        composable("cart") {
            CartScreen(
                cartItems = CartManager.items,
                total = CartManager.total(),
                onBack = { navController.popBackStack() },
                onEditItem = { index, item ->
                    pendingEditIndex = index
                    pendingProduct = products.firstOrNull { it.id == item.productId }
                    pendingCustomization = item.customization
                    pendingQuantity = item.quantity
                    navController.navigate("customize/${item.productId}/$index")
                },
                onRemoveItem = { index ->
                    CartManager.removeItem(index)
                },
                onCheckout = { username ->
                    pendingOrderTotal = CartManager.total()
                    pendingOrderItemCount = CartManager.items.sumOf { it.quantity }
                    pendingOrderNumber = generateOrderNumber()
                    pendingOrderProducts = formatProductsForSheet(CartManager.items)
                    pendingOrderUsername = username.trim()
                    pendingCustomization = null
                    pendingProduct = null
                    pendingQuantity = 1
                    pendingEditIndex = null
                    navController.navigate("order_confirmation") {
                        launchSingleTop = true
                    }
                }
            )
        }
        composable("order_confirmation") {
            OrderConfirmationScreen(
                totalPaid = pendingOrderTotal,
                totalProducts = pendingOrderItemCount,
                orderNumber = pendingOrderNumber,
                productsSummary = pendingOrderProducts,
                username = pendingOrderUsername,
                sendOrder = ::sendOrderToGoogleSheets,
                onBackToMenu = {
                    CartManager.clear()
                    pendingOrderTotal = 0
                    pendingOrderItemCount = 0
                    pendingOrderNumber = ""
                    pendingOrderProducts = ""
                    pendingOrderUsername = ""
                    navController.navigate("home") {
                        popUpTo("home") { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }
    }
}

private fun generateOrderNumber(): String {
    val randomCode = Random.nextInt(10000, 100000)
    return "PED-$randomCode"
}

private fun formatProductsForSheet(items: List<CartItem>): String {
    return items.joinToString(separator = " | ") { item ->
        "${item.name} x${item.quantity}"
    }
}

private suspend fun sendOrderToGoogleSheets(
    orderNumber: String,
    products: String,
    quantityTotal: Int,
    totalPaid: Int,
    estimatedTime: String,
    username: String
): Result<Unit> {
    return withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("pedido_numero", orderNumber)
            put("fecha_hora", currentTimestamp())
            put("productos", products)
            put("cantidad_total", quantityTotal)
            put("total_pagado", totalPaid)
            put("tiempo_estimado", estimatedTime)
            put("nombre_usuario", username.trim())
        }.toString()

        Log.d(OrderLogTag, "Payload de pedido: $payload")

        val connection = (URL(GoogleSheetsWebhookUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            doOutput = true
            connectTimeout = 15_000
            readTimeout = 15_000
        }

        val result = runCatching {
            connection.outputStream.use { output ->
                output.write(payload.toByteArray())
            }

            val responseCode = connection.responseCode
            val responseBody = runCatching {
                val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
                stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            }.getOrDefault("")

            Log.d(OrderLogTag, "Respuesta webhook: code=$responseCode, body=$responseBody")

            check(responseCode in 200..299) {
                "El webhook respondió con código HTTP $responseCode"
            }
        }

        connection.disconnect()

        result
    }
}

private fun currentTimestamp(): String {
    return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
}


@DrawableRes
private fun Product.customizationBackgroundRes(): Int = when (name) {
    "Handroll" -> R.drawable.handrroll
    "Gohan" -> R.drawable.gohan
    "SushiBurger" -> R.drawable.sushiburger
    "SushiPleto" -> R.drawable.sushipleto
    else -> R.drawable.fondo
}

private fun NavHostController.navigateToHome() {
    navigate("home") {
        popUpTo(graph.startDestinationId) { inclusive = false }
        launchSingleTop = true
    }
}

internal fun formatPrice(value: Int): String {
    val symbols = DecimalFormatSymbols(Locale("es", "CL")).apply {
        groupingSeparator = '.'
    }
    return "$" + DecimalFormat("#,###", symbols).format(value)
}
