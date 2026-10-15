package com.cundalf.wavelink.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastState
import com.google.android.gms.cast.framework.CastStateListener

/** Ámbar del estado "Reconectando…" (no es error fatal). */
internal val ReconnectColor = Color(0xFFFFC857)

@Composable
internal fun EqualizerBars(animating: Boolean) {
    Row(
        modifier =
            Modifier
                .size(width = 20.dp, height = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        if (animating) {
            val transition = rememberInfiniteTransition(label = "equalizer")
            val barFractions =
                List(EQ_BAR_COUNT) { index ->
                    transition.animateFloat(
                        initialValue = EQ_BAR_MIN_FRACTION,
                        targetValue = EQ_BAR_MAX_FRACTION,
                        animationSpec =
                            infiniteRepeatable(
                                animation =
                                    tween(
                                        durationMillis = EQ_DURATION_MS,
                                        delayMillis = index * EQ_DELAY_STEP_MS,
                                    ),
                                repeatMode = RepeatMode.Reverse,
                            ),
                        label = "equalizer_bar_$index",
                    )
                }
            barFractions.forEach { fraction ->
                EqualizerBar(scaleYProvider = { fraction.value })
            }
        } else {
            repeat(EQ_BAR_COUNT) {
                EqualizerBar(scaleYProvider = { EQ_BAR_IDLE_SCALE })
            }
        }
    }
}

@Composable
private fun EqualizerBar(scaleYProvider: () -> Float) {
    Box(
        modifier =
            Modifier
                .width(4.dp)
                .height(EQ_BAR_MAX_HEIGHT_DP)
                .graphicsLayer {
                    transformOrigin = EQ_BAR_TRANSFORM_ORIGIN
                    scaleY = scaleYProvider()
                }.clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.primary),
    )
}

@Composable
internal fun PulsingDot(
    color: Color,
    pulsing: Boolean,
) {
    if (pulsing) {
        val transition = rememberInfiniteTransition(label = "status_dot")
        val alpha =
            transition.animateFloat(
                initialValue = DOT_MIN_ALPHA,
                targetValue = DOT_MAX_ALPHA,
                animationSpec =
                    infiniteRepeatable(
                        animation = tween(durationMillis = PULSE_DURATION_MS),
                        repeatMode = RepeatMode.Reverse,
                    ),
                label = "status_dot_alpha",
            )
        PulsingDotBox(color = color, alphaProvider = { alpha.value })
    } else {
        PulsingDotBox(color = color, alphaProvider = { DOT_MAX_ALPHA })
    }
}

@Composable
private fun PulsingDotBox(
    color: Color,
    alphaProvider: () -> Float,
) {
    Box(
        modifier =
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .graphicsLayer { alpha = alphaProvider() }
                .background(color),
    )
}

@Composable
internal fun rememberCastActive(castEnabled: Boolean): Boolean {
    var active by remember { mutableStateOf(false) }
    if (castEnabled) {
        val context = LocalContext.current.applicationContext
        DisposableEffect(Unit) {
            val castContext = runCatching { CastContext.getSharedInstance(context) }.getOrNull()
            if (castContext == null) {
                onDispose { }
            } else {
                val listener = CastStateListener { state -> active = state == CastState.CONNECTED }
                active = castContext.castState == CastState.CONNECTED
                castContext.addCastStateListener(listener)
                onDispose { castContext.removeCastStateListener(listener) }
            }
        }
    }
    return active
}

@Composable
internal fun CastButton() {
    AndroidView(
        factory = { context ->
            MediaRouteButton(context).also { button ->
                runCatching { CastButtonFactory.setUpMediaRouteButton(context, button) }
            }
        },
        modifier = Modifier.size(48.dp),
    )
}

private const val EQ_BAR_COUNT = 3
private const val EQ_DURATION_MS = 600
private const val EQ_DELAY_STEP_MS = 120
private const val EQ_BAR_MIN_FRACTION = 0.3f
private const val EQ_BAR_MAX_FRACTION = 1f
private val EQ_BAR_MAX_HEIGHT_DP = 16.dp
private const val EQ_BAR_IDLE_SCALE = 0.375f
private val EQ_BAR_TRANSFORM_ORIGIN = TransformOrigin(0.5f, 1f)
private const val PULSE_DURATION_MS = 900
private const val DOT_MIN_ALPHA = 0.35f
private const val DOT_MAX_ALPHA = 1f
