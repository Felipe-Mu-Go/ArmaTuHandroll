package com.armatuhandroll.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.armatuhandroll.SplashScreen
import com.armatuhandroll.data.local.ProductCatalog
import com.armatuhandroll.domain.cart.CartManager
import com.armatuhandroll.domain.model.ProductCustomizationConfig
import com.armatuhandroll.domain.order.formatProductsForSheet
import com.armatuhandroll.domain.order.generateOrderNumber
import com.armatuhandroll.ui.screens.cart.CartScreen
import com.armatuhandroll.ui.screens.customization.CustomizedProductScreen
import com.armatuhandroll.ui.screens.customization.CustomizedProductSummaryScreen
import com.armatuhandroll.ui.screens.home.HomeScreen
import com.armatuhandroll.ui.screens.order.OrderConfirmationScreen
import com.armatuhandroll.ui.util.customizationBackgroundRes
import com.armatuhandroll.ui.viewmodel.AppViewModel

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
    val appViewModel: AppViewModel = viewModel()
    val uiState by appViewModel.uiState

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
                        appViewModel.startNewCustomization(product)
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
                        appViewModel.setPendingCustomization(product, customization, quantity, null)
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
                        appViewModel.setPendingCustomization(product, customization, quantity, editIndex)
                        navController.navigate(AppRoutes.CUSTOMIZED_SUMMARY)
                    },
                    onBack = { navController.popBackStack() }
                )
            }
        }
        composable(AppRoutes.CUSTOMIZED_SUMMARY) {
            val customization = uiState.pendingCustomization
            val product = uiState.pendingProduct
            val editIndex = uiState.pendingEditIndex
            val quantity = uiState.pendingQuantity
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
                        appViewModel.clearPendingSelection()
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
                    val product = ProductCatalog.products.firstOrNull { it.id == item.productId }
                    val customization = item.customization
                    if (product != null && customization != null) {
                        appViewModel.startEditingCustomization(index, product, customization, item.quantity)
                        navController.navigate(AppRoutes.customizeEdit(item.productId, index))
                    }
                },
                onRemoveItem = { index ->
                    CartManager.removeItem(index)
                },
                onCheckout = { username ->
                    val total = CartManager.total()
                    val itemCount = CartManager.items.sumOf { it.quantity }
                    val orderNumber = generateOrderNumber()
                    val productsSummary = formatProductsForSheet(CartManager.items)
                    appViewModel.prepareOrder(total, itemCount, orderNumber, productsSummary, username)
                    navController.navigate(AppRoutes.ORDER_CONFIRMATION) {
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(AppRoutes.ORDER_CONFIRMATION) {
            OrderConfirmationScreen(
                totalPaid = uiState.pendingOrderTotal,
                totalProducts = uiState.pendingOrderItemCount,
                orderNumber = uiState.pendingOrderNumber,
                productsSummary = uiState.pendingOrderProducts,
                username = uiState.pendingOrderUsername,
                sendOrder = sendOrder,
                onBackToMenu = {
                    CartManager.clear()
                    appViewModel.clearOrder()
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
