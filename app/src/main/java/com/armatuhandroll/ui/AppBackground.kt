package com.armatuhandroll.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.annotation.DrawableRes
import androidx.compose.ui.Alignment
import com.armatuhandroll.R

@Composable
fun AppBackground(
    @DrawableRes backgroundRes: Int = R.drawable.fondo,
    modifier: Modifier = Modifier,
    overlayAlpha: Float = 0.35f,
    content: @Composable BoxScope.() -> Unit
) {
    Box(modifier = modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = backgroundRes),
            contentDescription = "Fondo de la aplicación",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = overlayAlpha))
        )

        content()
    }
}

@Composable
fun AnimatedBrandTitle(
    modifier: Modifier = Modifier,
    text: String = "Arma Tu Handroll",
    textSize: TextUnit = MaterialTheme.typography.headlineSmall.fontSize
) {
    val transition = rememberInfiniteTransition(label = "brandTitleTransition")
    val offsetY = transition.animateFloat(
        initialValue = 0f,
        targetValue = -5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "brandTitleOffset"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .graphicsLayer { translationY = offsetY.value }
            .shadow(elevation = 6.dp, ambientColor = Color.Black, spotColor = Color.Black)
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_launcher_background),
            contentDescription = "Logo de Arma Tu Handroll",
            modifier = Modifier.size(30.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.headlineSmall,
            fontSize = textSize,
            color = Color.White,
            fontWeight = FontWeight.ExtraBold
        )
    }
}
