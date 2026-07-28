package com.armatuhandroll

import android.os.Bundle
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.armatuhandroll.data.local.ProductCatalog
import com.armatuhandroll.domain.cart.CartManager
import com.armatuhandroll.domain.order.formatProductsForSheet
import com.armatuhandroll.domain.order.generateOrderNumber
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val GoogleSheetsWebhookUrl = "https://script.google.com/macros/s/AKfycbzuA1_DjOwtrn0vl9pPEsfXExNFaLfW3akImx_Fd_nDMSxyTxYwRBOAk9sIMH4mbkPz7g/exec"
private const val OrderLogTag = "OrderSheets"

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
                products = ProductCatalog.products,
                cartItemCount = CartManager.items.size,
                onCartClick = { navController.navigate("cart") },
                onProductClick = { product ->
                    if (ProductCatalog.customizableProductsConfig.containsKey(product.name)) {
                        navController.navigate("customize/${product.id}")
                    } else {
                        CartManager.addProduct(product)
                    }
                }
            )
        }
        composable("customize/{productId}") { backStackEntry ->
            val productId = backStackEntry.arguments?.getString("productId")?.toIntOrNull()
            val product = ProductCatalog.products.firstOrNull { it.id == productId }
            val customizationConfig = product?.let { ProductCatalog.customizableProductsConfig[it.name] }
            if (product == null || customizationConfig == null) {
                navController.popBackStack()
            } else {
                CustomizedProductScreen(
                    product = product,
                    config = customizationConfig,
                    initialCustomization = null,
                    initialQuantity = 0,
                    isEditing = false,
                    proteinOptions = ProductCatalog.proteinOptions,
                    baseOptions = ProductCatalog.baseOptions,
                    vegetableOptions = ProductCatalog.vegetableOptions,
                    hasIncludedRemovableBases = ProductCatalog.hasIncludedRemovableBases(product.name),
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
            val product = ProductCatalog.products.firstOrNull { it.id == productId }
            val cartItem = editIndex?.let { idx -> CartManager.items.getOrNull(idx) }
            val customizationConfig = product?.let { ProductCatalog.customizableProductsConfig[it.name] }
            if (product == null || cartItem?.customization == null || customizationConfig == null) {
                navController.popBackStack()
            } else {
                CustomizedProductScreen(
                    product = product,
                    config = customizationConfig,
                    initialCustomization = cartItem.customization,
                    initialQuantity = cartItem.quantity,
                    isEditing = true,
                    proteinOptions = ProductCatalog.proteinOptions,
                    baseOptions = ProductCatalog.baseOptions,
                    vegetableOptions = ProductCatalog.vegetableOptions,
                    hasIncludedRemovableBases = ProductCatalog.hasIncludedRemovableBases(product.name),
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
                    val fixedIngredients = ProductCatalog.fixedIngredientsFor(product, customization)
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
                    config = ProductCatalog.customizableProductsConfig[product.name] ?: ProductCustomizationConfig(),
                    customization = customization,
                    quantity = quantity,
                    isEditing = editIndex != null,
                    fixedIngredients = ProductCatalog.fixedIngredientsFor(product, customization),
                    hasIncludedRemovableBases = ProductCatalog.hasIncludedRemovableBases(product.name),
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
                    pendingProduct = ProductCatalog.products.firstOrNull { it.id == item.productId }
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
