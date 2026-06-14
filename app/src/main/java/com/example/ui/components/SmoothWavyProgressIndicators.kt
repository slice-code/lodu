package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.example.ui.theme.Motion

@Composable
fun SmoothLinearWavyProgressIndicator(progress: Float, modifier: Modifier = Modifier) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = Motion.Progress,
        label = "linearProgress",
    )
    LinearProgressIndicator(
        progress = { animated },
        modifier = modifier,
    )
}
