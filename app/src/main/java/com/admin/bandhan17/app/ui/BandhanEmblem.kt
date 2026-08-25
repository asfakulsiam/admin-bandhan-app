package com.admin.bandhan17.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.admin.bandhan17.app.R
import com.admin.bandhan17.app.ui.theme.BandhanEmeraldPrimary

@Composable
fun BandhanEmblem(
    modifier: Modifier = Modifier,
    size: Dp = 160.dp,
    animate: Boolean = false
) {
    val rotation = if (animate) {
        val infiniteTransition = rememberInfiniteTransition(label = "emblem_anim")
        val animatedRotation by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(20000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "rotation"
        )
        animatedRotation
    } else {
        0f
    }

    Box(
        modifier = modifier
            .size(size)
            .shadow(12.dp, CircleShape, spotColor = BandhanEmeraldPrimary.copy(alpha = 0.35f))
            .clip(CircleShape)
            .testTag("bandhan_emblem"),
        contentAlignment = Alignment.Center
    ) {
        val painter = runCatching { painterResource(id = R.drawable.logo) }.getOrNull()
        if (painter != null) {
            Image(
                painter = painter,
                contentDescription = "Bandhan '17 Admin Logo",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .rotate(if (animate) rotation * 0.1f else 0f)
            )
        } else {
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                val center = this.center
                val radius = this.size.minDimension / 2f
                drawCircle(
                    color = androidx.compose.ui.graphics.Color(0xFF004D40),
                    radius = radius,
                    center = center
                )
                drawCircle(
                    color = androidx.compose.ui.graphics.Color(0xFF00BFA5),
                    radius = radius * 0.92f,
                    center = center,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 8f)
                )
                drawCircle(
                    color = androidx.compose.ui.graphics.Color(0xFF0B1E28),
                    radius = radius * 0.82f,
                    center = center
                )
            }
        }
    }
}


