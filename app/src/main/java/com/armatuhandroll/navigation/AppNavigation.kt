package com.armatuhandroll.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.armatuhandroll.SplashScreen
import com.armatuhandroll.data.repository.GoogleSheetsOrderRepository
import com.armatuhandroll.data.repository.LocalProductRepository
import com.armatuhandroll.domain.cart.CartManager
import com.armatuhandroll.domain.history.OrderHistoryManager
import com.armatuhandroll.domain.model.OrderHistoryItem
import com.armatuhandroll.domain.model.OrderRequest
import com.armatuhandroll.domain.model.ProductCustomizationConfig
import com.armatuhandroll.domain.order.formatProductsForSheet
import com.armatuhandroll.domain.order.generateOrderNumber
import com.armatuhandroll.domain.repository.OrderRepository
import com.armatuhandroll.domain.repository.ProductRepository
import com.armatuhandroll.ui.screens.cart.CartScreen
import com.armatuhandroll.ui.screens.customization.CustomizedProductScreen
import com.armatuhandroll.ui.screens.customization.CustomizedProductSummaryScreen
import com.armatuhandroll.ui.screens.home.HomeScreen
import com.armatuhandroll.ui.screens.order.OrderConfirmationScreen
import com.armatuhandroll.ui.screens.order.OrderHistoryScreen
import com.armatuhandroll.ui.screens.order.OrderSentScreen
import com.armatuhandroll.ui.util.customizationBackgroundRes
import com.armatuhandroll.ui.viewmodel.AppViewModel

@Composable
internal fun AppNavigation(
    productRepository: ProductRepository = LocalProductRepository(),
    orderRepository: OrderRepository = GoogleSheetsOrderRepository()
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
                products = productRepository.getProducts(),
                cartItemCount = CartManager.items.size,
                onCartClick = { navController.navigate(AppRoutes.CART) },
                onOrderHistoryClick = {
                    navController.navigate(AppRoutes.ORDER_HISTORY) {
                        launchSingleTop = true
                    }
                },
                onProductClick = { product ->
                    if (productRepository.isCustomizable(product.name)) {
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
            val product = productRepository.getProductById(productId)
            val customizationConfig = product?.let { productRepository.getCustomizationConfig(it.name) }
            if (product == null || customizationConfig == null) {
                navController.popBackStack()
            } else {
                CustomizedProductScreen(
                    product = product,
                    config = customizationConfig,
                    initialCustomization = null,
                    initialQuantity = 0,
                    isEditing = false,
                    proteinOptions = productRepository.getProteinOptions(),
                    baseOptions = productRepository.getBaseOptions(),
                    vegetableOptions = productRepository.getVegetableOptions(),
                    hasIncludedRemovableBases = productRepository.hasIncludedRemovableBases(product.name),
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
            val product = productRepository.getProductById(productId)
            val cartItem = editIndex?.let { idx -> CartManager.items.getOrNull(idx) }
            val customizationConfig = product?.let { productRepository.getCustomizationConfig(it.name) }
            if (product == null || cartItem?.customization == null || customizationConfig == null) {
                navController.popBackStack()
            } else {
                CustomizedProductScreen(
                    product = product,
                    config = customizationConfig,
                    initialCustomization = cartItem.customization,
                    initialQuantity = cartItem.quantity,
                    isEditing = true,
                    proteinOptions = productRepository.getProteinOptions(),
                    baseOptions = productRepository.getBaseOptions(),
                    vegetableOptions = productRepository.getVegetableOptions(),
                    hasIncludedRemovableBases = productRepository.hasIncludedRemovableBases(product.name),
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
                    val fixedIngredients = productRepository.getFixedIngredients(product, customization)
                    if (editIndex == null) {
                        CartManager.addCustomizedProduct(product, customization, quantity, fixedIngredients)
                    } else {
                        CartManager.updateCustomizedProduct(editIndex, product, customization, quantity, fixedIngredients)
                    }
                }
                CustomizedProductSummaryScreen(
                    product = product,
                    config = productRepository.getCustomizationConfig(product.name) ?: ProductCustomizationConfig(),
                    customization = customization,
                    quantity = quantity,
                    isEditing = editIndex != null,
                    fixedIngredients = productRepository.getFixedIngredients(product, customization),
                    hasIncludedRemovableBases = productRepository.hasIncludedRemovableBases(product.name),
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
                    val product = productRepository.getProductById(item.productId)
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
                sendOrder = { orderNumber, products, quantityTotal, totalPaid, estimatedTime, username ->
                    orderRepository.sendOrder(
                        OrderRequest(
                            orderNumber = orderNumber,
                            products = products,
                            quantityTotal = quantityTotal,
                            totalPaid = totalPaid,
                            estimatedTime = estimatedTime,
                            username = username
                        )
                    )
                },
                onOrderSent = {
                    OrderHistoryManager.add(
                        OrderHistoryItem(
                            orderNumber = uiState.pendingOrderNumber,
                            productsSummary = uiState.pendingOrderProducts,
                            quantityTotal = uiState.pendingOrderItemCount,
                            totalPaid = uiState.pendingOrderTotal,
                            estimatedTimeMinutes = uiState.pendingOrderItemCount * 5,
                            username = uiState.pendingOrderUsername,
                            createdAt = System.currentTimeMillis(),
                            status = "Pendiente de revisión"
                        )
                    )
                    CartManager.clear()

                    navController.navigate(AppRoutes.ORDER_SENT) {
                        popUpTo(AppRoutes.ORDER_CONFIRMATION) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(AppRoutes.ORDER_SENT) {
            OrderSentScreen(
                totalPaid = uiState.pendingOrderTotal,
                orderNumber = uiState.pendingOrderNumber,
                username = uiState.pendingOrderUsername,
                estimatedTimeMinutes = uiState.pendingOrderItemCount * 5,
                onBackToMenu = {
                    appViewModel.clearOrder()

                    navController.navigate(AppRoutes.HOME) {
                        popUpTo(AppRoutes.HOME) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(AppRoutes.ORDER_HISTORY) {
            OrderHistoryScreen(
                orders = OrderHistoryManager.items,
                onBack = { navController.popBackStack() },
                onClearHistory = { OrderHistoryManager.clear() }
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
