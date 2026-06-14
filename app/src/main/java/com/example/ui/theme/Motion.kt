package com.example.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween

object Motion {
    const val DurationShort = 200
    const val DurationMedium = 300
    const val DurationLong = 400

    val EmphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccelerate: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    val Fade: FiniteAnimationSpec<Float> = tween(DurationLong, easing = EmphasizedDecelerate)
    val FadeOut: FiniteAnimationSpec<Float> = tween(DurationShort, easing = EmphasizedAccelerate)
    val Progress: FiniteAnimationSpec<Float> = tween(DurationMedium, easing = LinearEasing)
}
