package com.example.data.model

import android.content.Context

/**
 * CPU MNN models — same IDs and HuggingFace paths as local-dream sd-mnn catalog.
 */
class SdModelRepository(private val context: Context) {

    val models: List<SdDreamModel> = listOf(
        SdDreamModel(
            id = "anythingv5cpu",
            name = "Anything V5.0 (MNN CPU)",
            description = "Model anime Anything V5 — format MNN untuk CPU/OpenCL",
            runOnCpu = true,
            defaultPrompt = "masterpiece, best quality, 1girl, solo, cute, white hair,",
            defaultNegativePrompt = "lowres, bad anatomy, bad hands, missing fingers, extra fingers"
        ),
        SdDreamModel(
            id = "qteamixcpu",
            name = "QteaMix (MNN CPU)",
            description = "Model anime QteaMix — format MNN",
            runOnCpu = true,
            defaultPrompt = "chibi, best quality, 1girl, solo, cute, pink hair,",
            defaultNegativePrompt = "lowres, bad anatomy, bad hands, missing fingers"
        ),
        SdDreamModel(
            id = "absoluterealitycpu",
            name = "Absolute Reality (MNN CPU)",
            description = "Model realistis Absolute Reality — format MNN",
            runOnCpu = true,
            defaultPrompt = "masterpiece, best quality, portrait, photorealistic,",
            defaultNegativePrompt = "lowres, bad anatomy, blurry, watermark"
        ),
        SdDreamModel(
            id = "chilloutmixcpu",
            name = "ChilloutMix (MNN CPU)",
            description = "Model ChilloutMix — format MNN",
            runOnCpu = true,
            defaultPrompt = "masterpiece, best quality, 1girl, solo,",
            defaultNegativePrompt = "lowres, bad anatomy, bad hands, missing fingers"
        )
    )

    fun findById(modelId: String): SdDreamModel? = models.find { it.id == modelId }

    fun isDownloaded(modelId: String): Boolean =
        SdDreamModel.isModelDownloaded(context, modelId)
}
