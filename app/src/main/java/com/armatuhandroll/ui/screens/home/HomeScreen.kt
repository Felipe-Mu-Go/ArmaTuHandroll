package com.armatuhandroll.ui.screens.home

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
import com.armatuhandroll.domain.model.Product
import com.armatuhandroll.ui.AnimatedBrandTitle
import com.armatuhandroll.ui.AppBackground
import com.armatuhandroll.ui.components.IngredientGlassCard
import com.armatuhandroll.ui.components.ConnectivityBanner
import com.armatuhandroll.ui.components.ProductCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeScreen(
    isConnected: Boolean,
    products: List<Product>,
    cartItemCount: Int,
    onCartClick: () -> Unit,
    onOrderHistoryClick: () -> Unit,
    onProductClick: (Product) -> Unit
) {

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
                    title = {
                        IngredientGlassCard(
                            modifier = Modifier.padding(top = 6.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            AnimatedBrandTitle()
                        }
                    },
                    navigationIcon = {},
                    actions = {
                        TextButton(onClick = onOrderHistoryClick) {
                            Text(
                                text = "Mis pedidos",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        IngredientGlassCard(
                            modifier = Modifier
                                .padding(top = 6.dp, end = 8.dp)
                                .size(52.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                IconButton(onClick = onCartClick) {
                                    Text("🛍️", fontSize = 22.sp)
                                }
                            }
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
                ConnectivityBanner(isConnected = isConnected)
                Text(
                    text = "Productos disponibles",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
                Text(
                    text = "Productos en carrito: ${cartItemCount}",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = Color.White.copy(alpha = 0.9f)
                )
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(products) { product ->
                        ProductCard(
                            product = product,
                            onAdd = { onProductClick(product) }
                        )
                    }
                }
            }
        }
    }
}
