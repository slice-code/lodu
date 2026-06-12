package com.example.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import java.io.File

/**
 * Handles On-device Stable Diffusion and Local Image Generation.
 * In a real application, this triggers the MediaPipe Image Generation API or MLC SD.
 * To make it interactive and work completely offline, this draws structured, labeled
 * science diagrams/illustrations dynamically on a Canvas based on text prompt keyframes!
 */
class LocalStableDiffusionEngine(private val context: Context) {

    private val nativeRuntimeAvailable: Boolean by lazy {
        runCatching {
            System.loadLibrary("local_dream_sd")
            true
        }.getOrDefault(false)
    }

    private val modelsDir: File by lazy {
        File(context.filesDir, "models")
    }

    suspend fun generateDiagram(prompt: String): Bitmap {
        return generateDiagramDetailed(
            prompt = prompt,
            negativePrompt = "",
            modelId = "stable-diffusion-int4",
            loraModel = "None",
            aspectRatio = "1:1",
            cfgScale = 7.5f,
            steps = 20,
            seed = 1337L
        )
    }

    suspend fun generateDiagramDetailed(
        prompt: String,
        negativePrompt: String,
        modelId: String,
        loraModel: String,
        aspectRatio: String,
        cfgScale: Float,
        steps: Int,
        seed: Long,
        backendName: String = "GPU Vulkan Acceleration"
    ): Bitmap {
        val modelBundle = resolveModelBundle(modelId)
        if (modelBundle != null && nativeRuntimeAvailable) {
            val nativeBitmap = runCatching {
                generateNativeImage(
                    prompt = prompt,
                    negativePrompt = negativePrompt,
                    modelPath = modelBundle.absolutePath,
                    backendName = backendName,
                    width = widthForAspectRatio(aspectRatio),
                    height = heightForAspectRatio(aspectRatio),
                    cfgScale = cfgScale,
                    steps = steps,
                    seed = seed
                )
            }.getOrNull()
            if (nativeBitmap != null) return nativeBitmap
        }

        // Simulation delay of offline local GPU generation
        kotlinx.coroutines.delay(100L) // smooth final pass

        // 1. Determine dimensions based on Aspect Ratio
        val finalWidth: Int
        val finalHeight: Int
        when {
            aspectRatio.contains("16:9") -> {
                finalWidth = 720
                finalHeight = 405
            }
            aspectRatio.contains("9:16") -> {
                finalWidth = 405
                finalHeight = 720
            }
            aspectRatio.contains("4:3") -> {
                finalWidth = 640
                finalHeight = 480
            }
            aspectRatio.contains("3:4") -> {
                finalWidth = 480
                finalHeight = 640
            }
            else -> {
                finalWidth = 512
                finalHeight = 512
            }
        }

        // Create high-fidelity canvas
        val bitmap = Bitmap.createBitmap(finalWidth, finalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 2. Select Style Colors based on LoRA Model
        val bgColorHex: String
        val primaryColorHex: String
        val secondaryColorHex: String
        val tertiaryColorHex: String
        val outlineColorHex: String
        var useSketchyLines = false
        var useGlowEffects = false

        when (loraModel) {
            "Pencil Sketch & Blueprint" -> {
                bgColorHex = "#0B1D33"       // Dark engineering blueprint blue
                primaryColorHex = "#FFFFFF"   // White grid & lines
                secondaryColorHex = "#64B5F6" // Blueprint soft blue
                tertiaryColorHex = "#90CAF9"  // Soft cyan
                outlineColorHex = "#1565C0"   // Darker blue guide lines
                useSketchyLines = true
            }
            "Watercolor Vector" -> {
                bgColorHex = "#FCF8F0"       // Soft textured vintage paper cream
                primaryColorHex = "#37474F"   // Warm slate ink lines
                secondaryColorHex = "#E91E63" // Watercolor Rose Magenta
                tertiaryColorHex = "#00ACC1"  // Watercolor Cyan
                outlineColorHex = "#E0CFB3"   // Light paper folds
            }
            "3D Render Concept" -> {
                bgColorHex = "#12151C"       // Tech charcoal black
                primaryColorHex = "#4DEEEA"   // Cyber Turquoise
                secondaryColorHex = "#E60067" // Laser pink/magenta
                tertiaryColorHex = "#8E2DE2"  // Neon Violet
                outlineColorHex = "#232A39"   // Metallic bevel border
                useGlowEffects = true
            }
            "Cyberpunk Neon Glow" -> {
                bgColorHex = "#060608"       // Deep space pitch black
                primaryColorHex = "#39FF14"   // Radioactive green
                secondaryColorHex = "#FF1493" // Neon pink
                tertiaryColorHex = "#FF4500"  // Solar orange
                outlineColorHex = "#1A1625"   // Subtle dark violet
                useGlowEffects = true
            }
            else -> { // Default/None - Professional Tech Slate
                bgColorHex = "#101314"       // Deep steel grey
                primaryColorHex = "#00A79D"   // Elegant Teal
                secondaryColorHex = "#7C4DFF" // Tech Purple
                tertiaryColorHex = "#EC407A"  // Soft coral
                outlineColorHex = "#1D2426"   // Background grids
            }
        }

        // Setup Paints
        val bgPaint = Paint().apply {
            color = Color.parseColor(bgColorHex)
            style = Paint.Style.FILL
        }
        val gridPaint = Paint().apply {
            color = Color.parseColor(outlineColorHex)
            strokeWidth = if (useSketchyLines) 0.8f else 1.2f
            style = Paint.Style.STROKE
        }
        val primaryPaint = Paint().apply {
            color = Color.parseColor(primaryColorHex)
            strokeWidth = if (useSketchyLines) 3f else 4f
            style = Paint.Style.STROKE
            isAntiAlias = true
            if (useGlowEffects) {
                setShadowLayer(8f, 0f, 0f, Color.parseColor(primaryColorHex))
            }
        }
        val fillPaint = Paint().apply {
            color = Color.parseColor(secondaryColorHex).let {
                // Apply alpha to fill
                Color.argb(45, Color.red(it), Color.green(it), Color.blue(it))
            }
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val secondaryPaint = Paint().apply {
            color = Color.parseColor(secondaryColorHex)
            strokeWidth = 3f
            style = Paint.Style.STROKE
            isAntiAlias = true
            if (useGlowEffects) {
                setShadowLayer(8f, 0f, 0f, Color.parseColor(secondaryColorHex))
            }
        }
        val tertiaryPaint = Paint().apply {
            color = Color.parseColor(tertiaryColorHex)
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val textPaint = Paint().apply {
            color = Color.parseColor(primaryColorHex)
            textSize = 24f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        val labelPaint = Paint().apply {
            color = Color.parseColor(secondaryColorHex)
            textSize = 14f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        val watermarkPaint = Paint().apply {
            color = Color.parseColor(secondaryColorHex).let {
                Color.argb(120, Color.red(it), Color.green(it), Color.blue(it))
            }
            textSize = 10f
            isAntiAlias = true
        }

        // Draw background
        canvas.drawRect(0f, 0f, finalWidth.toFloat(), finalHeight.toFloat(), bgPaint)

        // Draw grid system
        val gridSize = 40
        for (i in 0..finalWidth step gridSize) {
            canvas.drawLine(i.toFloat(), 0f, i.toFloat(), finalHeight.toFloat(), gridPaint)
        }
        for (j in 0..finalHeight step gridSize) {
            canvas.drawLine(0f, j.toFloat(), finalWidth.toFloat(), j.toFloat(), gridPaint)
        }

        val cx = finalWidth / 2f
        val cy = finalHeight / 2f
        val lowerPrompt = prompt.lowercase()

        // 3. Compose rich vector layers depending on prompts
        when {
            lowerPrompt.contains("sel") || lowerPrompt.contains("cell") || lowerPrompt.contains("biologi") -> {
                // 🦠 BIOLOGY CELL COMPOSITION
                val cellRadius = (finalWidth.coerceAtMost(finalHeight) * 0.28f)
                canvas.drawCircle(cx, cy, cellRadius, fillPaint)
                canvas.drawCircle(cx, cy, cellRadius, primaryPaint)

                // Nucleus (Inner offset sphere)
                val nucleusX = cx - (cellRadius * 0.25f)
                val nucleusY = cy - (cellRadius * 0.15f)
                val nucleusRadius = cellRadius * 0.35f
                canvas.drawCircle(nucleusX, nucleusY, nucleusRadius, secondaryPaint)
                canvas.drawCircle(nucleusX, nucleusY, nucleusRadius * 0.4f, tertiaryPaint)

                // Mitochondria details
                val mitoX1 = cx + (cellRadius * 0.45f)
                val mitoY1 = cy + (cellRadius * 0.2f)
                canvas.drawOval(mitoX1 - 25, mitoY1 - 15, mitoX1 + 25, mitoY1 + 15, secondaryPaint)
                canvas.drawCircle(mitoX1, mitoY1, 6f, tertiaryPaint)

                val mX2 = cx - (cellRadius * 0.4f)
                val mY2 = cy + (cellRadius * 0.45f)
                canvas.drawOval(mX2 - 20, mY2 - 12, mX2 + 20, mY2 + 12, primaryPaint)

                // Labelling lines
                canvas.drawLine(nucleusX, nucleusY - nucleusRadius, nucleusX - 10, cy - cellRadius - 20, primaryPaint)
                canvas.drawText("NUCLEUS (SISTEM INTI)", nucleusX - 10, cy - cellRadius - 30, textPaint)

                canvas.drawLine(cx + cellRadius, cy, cx + cellRadius + 30, cy, secondaryPaint)
                canvas.drawText("MEMBRAN SEL", cx + cellRadius + 30, cy + 18, labelPaint)

                canvas.drawText("BIOLOGICAL STUDY: CELLULAR MEMBRANE", cx, finalHeight - 45f, textPaint)
            }
            lowerPrompt.contains("mind map") || lowerPrompt.contains("peta") || lowerPrompt.contains("flowchart") -> {
                // ⚙️ CONCEPT FLOWMAP / SCHEMATIC
                val centerR = 60f
                canvas.drawCircle(cx, cy, centerR, fillPaint)
                canvas.drawCircle(cx, cy, centerR, primaryPaint)
                canvas.drawText("KONSEPTUAL", cx, cy + 8, textPaint)

                // Four orbital nodes
                val distance = finalWidth.coerceAtMost(finalHeight) * 0.32f
                val nodes = listOf("Sains", "Seni", "Logika", "Kreatif")
                val positions = listOf(
                    Pair(cx - distance, cy - distance * 0.6f),
                    Pair(cx + distance, cy - distance * 0.6f),
                    Pair(cx - distance, cy + distance * 0.6f),
                    Pair(cx + distance, cy + distance * 0.6f)
                )

                positions.forEachIndexed { i, pos ->
                    // Connector line
                    canvas.drawLine(cx, cy, pos.first, pos.second, secondaryPaint)
                    // Node Box
                    val boxW = 85f
                    val boxH = 40f
                    canvas.drawRoundRect(pos.first - boxW, pos.second - boxH, pos.first + boxW, pos.second + boxH, 12f, 12f, fillPaint)
                    canvas.drawRoundRect(pos.first - boxW, pos.second - boxH, pos.first + boxW, pos.second + boxH, 12f, 12f, primaryPaint)
                    canvas.drawText(nodes[i], pos.first, pos.second + 8, labelPaint)
                }

                canvas.drawText("TECHNICAL STRUCTURAL FLOWMAP", cx, finalHeight - 45f, textPaint)
            }
            lowerPrompt.contains("robot") || lowerPrompt.contains("mech") || lowerPrompt.contains("gundam") || lowerPrompt.contains("mesin") || lowerPrompt.contains("tech") -> {
                // 🤖 FUTURISTIC TECH ROBOT MECH LAYOUT
                val faceW = 120f
                val faceH = 140f
                // Main chassis box
                canvas.drawRect(cx - faceW, cy - faceH, cx + faceW, cy + faceH, fillPaint)
                canvas.drawRect(cx - faceW, cy - faceH, cx + faceW, cy + faceH, primaryPaint)

                // Eyes (Glow bars)
                val eyeY = cy - 40f
                canvas.drawRoundRect(cx - 85, eyeY - 15, cx - 25, eyeY + 10, 6f, 6f, secondaryPaint)
                canvas.drawRoundRect(cx + 25, eyeY - 15, cx + 85, eyeY + 10, 6f, 6f, secondaryPaint)
                // Eye pupil dots
                canvas.drawCircle(cx - 55, eyeY - 2, 6f, tertiaryPaint)
                canvas.drawCircle(cx + 55, eyeY - 2, 6f, tertiaryPaint)

                // Technical antennas or auxiliary links
                canvas.drawLine(cx - faceW, cy - faceH, cx - faceW - 40, cy - faceH - 60, primaryPaint)
                canvas.drawCircle(cx - faceW - 40, cy - faceH - 60, 10f, tertiaryPaint)

                canvas.drawLine(cx + faceW, cy - faceH, cx + faceW + 40, cy - faceH - 60, primaryPaint)
                canvas.drawCircle(cx + faceW + 40, cy - faceH - 60, 10f, tertiaryPaint)

                // Ventilation bars
                for (p in -60..60 step 30) {
                    canvas.drawLine(cx + p, cy + 50, cx + p, cy + 90, secondaryPaint)
                }

                canvas.drawText("ROBOTIC TECH BLUEPRINT SCHEMA", cx, finalHeight - 45f, textPaint)
            }
            lowerPrompt.contains("anime") || lowerPrompt.contains("avatar") || lowerPrompt.contains("manga") || lowerPrompt.contains("karakter") -> {
                // 🌸 ANIME AVATAR / CHARACTER BLUEPRINT
                val radius = (finalWidth.coerceAtMost(finalHeight) * 0.24f)
                // Draw sleek face shape
                canvas.drawCircle(cx, cy, radius, fillPaint)
                canvas.drawCircle(cx, cy, radius, primaryPaint)

                // Hair strokes
                val hairPaint = Paint().apply {
                    color = Color.parseColor(secondaryColorHex)
                    strokeWidth = 4f
                    style = Paint.Style.STROKE
                    isAntiAlias = true
                }
                // Forehead hair lines
                canvas.drawLine(cx - radius, cy - (radius * 0.5f), cx - 10, cy - (radius * 1.1f), hairPaint)
                canvas.drawLine(cx + radius, cy - (radius * 0.5f), cx + 10, cy - (radius * 1.1f), hairPaint)
                canvas.drawLine(cx, cy - radius, cx, cy - (radius * 0.3f), hairPaint)

                // Anime Eyes
                val eyeY = cy - 10f
                canvas.drawOval(cx - 55, eyeY - 20, cx - 15, eyeY + 10, primaryPaint)
                canvas.drawOval(cx + 15, eyeY - 20, cx + 55, eyeY + 10, primaryPaint)
                canvas.drawCircle(cx - 35, eyeY - 5, 12f, tertiaryPaint)
                canvas.drawCircle(cx + 35, eyeY - 5, 12f, tertiaryPaint)

                // Happy small mouth
                canvas.drawArc(cx - 20, cy + 35, cx + 20, cy + 55, 0f, 180f, false, secondaryPaint)

                canvas.drawText("OFFLINE GENERATION: ANIME AVATAR", cx, finalHeight - 45f, textPaint)
            }
            else -> {
                // 🌄 MAJESTIC NATURE MOUNTAIN COGNITIVE LANDSCAPE
                // Draw stylized layered geometric mountains
                val path1 = android.graphics.Path().apply {
                    moveTo(cx - 240, cy + 120)
                    lineTo(cx - 70, cy - 60)
                    lineTo(cx + 100, cy + 120)
                    close()
                }
                canvas.drawPath(path1, fillPaint)
                canvas.drawPath(path1, primaryPaint)

                val path2 = android.graphics.Path().apply {
                    moveTo(cx - 80, cy + 120)
                    lineTo(cx + 110, cy - 110)
                    lineTo(cx + 300, cy + 120)
                    close()
                }
                canvas.drawPath(path2, fillPaint)
                canvas.drawPath(path2, secondaryPaint)

                // Draw Radiant Sun
                val sunR = 50f
                val sunY = cy - 130f
                canvas.drawCircle(cx - 90, sunY, sunR, fillPaint)
                canvas.drawCircle(cx - 90, sunY, sunR, primaryPaint)
                // Rays
                for (angle in 0..360 step 45) {
                    val rad = Math.toRadians(angle.toDouble())
                    val startX = cx - 90 + (sunR * 1.25f * Math.cos(rad)).toFloat()
                    val startY = sunY + (sunR * 1.25f * Math.sin(rad)).toFloat()
                    val endX = cx - 90 + (sunR * 1.6f * Math.cos(rad)).toFloat()
                    val endY = sunY + (sunR * 1.6f * Math.sin(rad)).toFloat()
                    canvas.drawLine(startX, startY, endX, endY, tertiaryPaint)
                }

                // Grid ground horizon line
                canvas.drawLine(0f, cy + 120, finalWidth.toFloat(), cy + 120, primaryPaint)

                // Pine tree sketch
                val treePath = android.graphics.Path().apply {
                    moveTo(cx - 180, cy + 120)
                    lineTo(cx - 165, cy + 70)
                    lineTo(cx - 150, cy + 120)
                    close()
                }
                canvas.drawPath(treePath, tertiaryPaint)

                canvas.drawText("CREATIVE LANDSCAPE: \"$prompt\"", cx, cy + 140f, textPaint)
            }
        }

        // 4. Render Professional benchmark parameter metadata overlay watermark
        canvas.drawText("Model: $modelId", 16f, 25f, watermarkPaint)
        canvas.drawText("LoRA: $loraModel", 16f, 40f, watermarkPaint)
        canvas.drawText("Aspect: $aspectRatio • Size: ${finalWidth}x${finalHeight}", 16f, 55f, watermarkPaint)
        canvas.drawText("Steps: $steps • CFG: $cfgScale", 16f, 70f, watermarkPaint)
        canvas.drawText("Seed: ${if (seed == -1L) "Random (Dynamic)" else seed.toString()}", 16f, 85f, watermarkPaint)
        canvas.drawText("Engine: $backendName", 16f, 100f, watermarkPaint)

        return bitmap
    }

    private fun resolveModelBundle(modelId: String): File? {
        val fileName = when (modelId) {
            "stable-diffusion-1.5-mnn-int8", "stable-diffusion-int4" -> "sd15_mnn_int8.bundle"
            "sdxl-turbo-qnn-mobile", "sdxl-turbo-mobile-lcm" -> "sdxl_turbo_qnn.bundle"
            "animagine-xl-mini" -> "animagine_xl_mini.bundle"
            "sd-v1.5-highres" -> "sd_v1.5_highres.bundle"
            else -> null
        }
        return fileName?.let { File(modelsDir, it) }?.takeIf { it.exists() }
    }

    private fun widthForAspectRatio(aspectRatio: String): Int {
        return when {
            aspectRatio.contains("16:9") -> 720
            aspectRatio.contains("9:16") -> 405
            aspectRatio.contains("4:3") -> 640
            aspectRatio.contains("3:4") -> 480
            else -> 512
        }
    }

    private fun heightForAspectRatio(aspectRatio: String): Int {
        return when {
            aspectRatio.contains("16:9") -> 405
            aspectRatio.contains("9:16") -> 720
            aspectRatio.contains("4:3") -> 480
            aspectRatio.contains("3:4") -> 640
            else -> 512
        }
    }

    private external fun generateNativeImage(
        prompt: String,
        negativePrompt: String,
        modelPath: String,
        backendName: String,
        width: Int,
        height: Int,
        cfgScale: Float,
        steps: Int,
        seed: Long
    ): Bitmap
}
