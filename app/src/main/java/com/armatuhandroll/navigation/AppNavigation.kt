package com.armatuhandroll.navigation

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
import com.armatuhandroll.SplashScreen
import com.armatuhandroll.data.local.ProductCatalog
import com.armatuhandroll.domain.cart.CartManager
import com.armatuhandroll.domain.model.IngredientCustomization
import com.armatuhandroll.domain.model.Product
import com.armatuhandroll.domain.model.ProductCustomizationConfig
import com.armatuhandroll.domain.order.formatProductsForSheet
import com.armatuhandroll.domain.order.generateOrderNumber
import com.armatuhandroll.ui.screens.cart.CartScreen
import com.armatuhandroll.ui.screens.customization.CustomizedProductScreen
import com.armatuhandroll.ui.screens.customization.CustomizedProductSummaryScreen
import com.armatuhandroll.ui.screens.home.HomeScreen
import com.armatuhandroll.ui.screens.order.OrderConfirmationScreen
import com.armatuhandroll.ui.util.customizationBackgroundRes

@Composable
internal fun AppNavigation(
    sendOrder: suspend (
        orderNumber: String,
        products: String,
        quantityTotal: Int,
        totalPaid: Int,
        estimatedTime: String,
        username: String
    ) -> Result<Unit>
) {
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

    NavHost(navController = navController, startDestination = AppRoutes.SPLASH) {
        composable(AppRoutes.SPLASH) {
            SplashScreen(navController)
        }
        composable(AppRoutes.HOME) {
            HomeScreen(
                products = ProductCatalog.products,
                cartItemCount = CartManager.items.size,
                onCartClick = { navController.navigate(AppRoutes.CART) },
                onProductClick = { product ->
                    if (ProductCatalog.customizableProductsConfig.containsKey(product.name)) {
                        navController.navigate(AppRoutes.customize(product.id))
                    } else {
                        CartManager.addProduct(product)
                    }
                }
            )
        }
        composable(AppRoutes.CUSTOMIZE) { backStackEntry ->
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
                        navController.navigate(AppRoutes.CUSTOMIZED_SUMMARY)
                    },
                    onBack = { navController.popBackStack() }
                )
            }
        }
        composable(AppRoutes.CUSTOMIZE_EDIT) { backStackEntry ->
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
                        navController.navigate(AppRoutes.CUSTOMIZED_SUMMARY)
                    },
                    onBack = { navController.popBackStack() }
                )
            }
        }
        composable(AppRoutes.CUSTOMIZED_SUMMARY) {
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
                        navController.navigate(AppRoutes.CART) {
                            popUpTo(AppRoutes.CUSTOMIZED_SUMMARY) { inclusive = true }
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
        composable(AppRoutes.CART) {
            CartScreen(
                cartItems = CartManager.items,
                total = CartManager.total(),
                onBack = { navController.popBackStack() },
                onEditItem = { index, item ->
                    pendingEditIndex = index
                    pendingProduct = ProductCatalog.products.firstOrNull { it.id == item.productId }
                    pendingCustomization = item.customization
                    pendingQuantity = item.quantity
                    navController.navigate(AppRoutes.customizeEdit(item.productId, index))
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
                    navController.navigate(AppRoutes.ORDER_CONFIRMATION) {
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(AppRoutes.ORDER_CONFIRMATION) {
            OrderConfirmationScreen(
                totalPaid = pendingOrderTotal,
                totalProducts = pendingOrderItemCount,
                orderNumber = pendingOrderNumber,
                productsSummary = pendingOrderProducts,
                username = pendingOrderUsername,
                sendOrder = sendOrder,
                onBackToMenu = {
                    CartManager.clear()
                    pendingOrderTotal = 0
                    pendingOrderItemCount = 0
                    pendingOrderNumber = ""
                    pendingOrderProducts = ""
                    pendingOrderUsername = ""
                    navController.navigate(AppRoutes.HOME) {
                        popUpTo(AppRoutes.HOME) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }
    }
}


private fun NavHostController.navigateToHome() {
    navigate(AppRoutes.HOME) {
        popUpTo(graph.startDestinationId) {
            inclusive = false
        }
        launchSingleTop = true
    }
}
