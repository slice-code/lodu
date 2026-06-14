package com.example.data.model

data class SdGenerationSettings(
    val steps: Float = SdMobileDefaults.DEFAULT_STEPS.toFloat(),
    val cfgScale: Float = SdMobileDefaults.DEFAULT_CFG,
    val customWidth: Float = SdMobileDefaults.DEFAULT_WIDTH.toFloat(),
    val customHeight: Float = SdMobileDefaults.DEFAULT_HEIGHT.toFloat(),
    val aspectRatio: String = SdMobileDefaults.DEFAULT_ASPECT_RATIO,
    val performanceProfile: SdMobileDefaults.PerformanceProfile = SdMobileDefaults.PerformanceProfile.BALANCED
)
