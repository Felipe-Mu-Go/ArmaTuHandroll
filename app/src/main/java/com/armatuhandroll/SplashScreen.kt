package com.armatuhandroll

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlinx.coroutines.delay

private const val SplashRoute = "splash"
private const val MainMenuRoute = "home"
private const val SplashDurationMs = 2_500L

@Composable
fun SplashScreen(navController: NavController) {
    var startAnimation by remember { mutableStateOf(false) }

    val imageAlpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0.55f,
        animationSpec = tween(durationMillis = 1_100, easing = FastOutSlowInEasing),
        label = "splashImageAlpha"
    )

    val imageScale by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 1.08f,
        animationSpec = tween(durationMillis = 1_400, easing = FastOutSlowInEasing),
        label = "splashImageScale"
    )

    LaunchedEffect(Unit) {
        startAnimation = true
        delay(SplashDurationMs)
        navController.navigate(MainMenuRoute) {
            popUpTo(SplashRoute) { inclusive = true }
            launchSingleTop = true
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.fondo),
            contentDescription = "Pantalla de inicio",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .alpha(imageAlpha)
                .scale(imageScale)
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.28f))
        )

        AnimatedVisibility(
            visible = startAnimation,
            enter = fadeIn(animationSpec = tween(durationMillis = 1_000)) +
                slideInVertically(
                    animationSpec = tween(durationMillis = 1_000, easing = FastOutSlowInEasing),
                    initialOffsetY = { it / 4 }
                ),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Arma Tu Handroll",
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Personaliza tu pedido",
                    fontSize = 16.sp,
                    color = Color.White.copy(alpha = 0.92f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
