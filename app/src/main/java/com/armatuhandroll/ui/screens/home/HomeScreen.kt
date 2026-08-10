package com.armatuhandroll.ui.screens.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun HomeScreen(
    isConnected: Boolean,
    products: List<Product>,
    cartItemCount: Int,
    onCartClick: () -> Unit,
    onOrderHistoryClick: () -> Unit,
    onAdminAccessRequest: () -> Unit,
    onProductClick: (Product) -> Unit
) {

    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = Color.White,
            topBar = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IngredientGlassCard(
                        modifier = Modifier
                            .weight(1f)
                            .combinedClickable(
                                onClick = {},
                                onLongClick = onAdminAccessRequest
                            ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        AnimatedBrandTitle(
                            textSize = 18.sp,
                            logoSize = 24.dp
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    TextButton(
                        onClick = onOrderHistoryClick,
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Text(
                            text = "Mis pedidos",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Box(modifier = Modifier.size(48.dp)) {
                        IngredientGlassCard(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            IconButton(onClick = onCartClick) {
                                Text("🛍️", fontSize = 21.sp)
                            }
                        }
                        Badge(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 4.dp, end = 4.dp)
                        ) {
                            Text(text = cartItemCount.toString())
                        }
                    }
                }
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
