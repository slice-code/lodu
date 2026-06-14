package com.example.data.model

import android.app.ActivityManager
import android.content.Context
import android.os.Build

/**
 * Default generation parameters tuned for on-device SD 1.5 (MNN) on Android phones.
 * Aligned with local-dream mobile profiles: moderate resolution, fewer steps, capped max size.
 */
object SdMobileDefaults {

    const val DEFAULT_STEPS = 20
    const val MIN_STEPS = 4
    const val MAX_STEPS = 30

    const val DEFAULT_CFG = 7.0f
    const val MIN_CFG = 4.0f
    const val MAX_CFG = 12.0f

    const val DEFAULT_WIDTH = 256
    const val DEFAULT_HEIGHT = 256
    const val MIN_DIMENSION = 256
    const val MAX_DIMENSION = 768
    const val DIMENSION_STEP = 64

    const val DEFAULT_ASPECT_RATIO = "1:1"
    const val PROGRESS_PREVIEW_STRIDE = 1

    data class AspectPreset(
        val label: String,
        val width: Int,
        val height: Int
    )

    val aspectPresets: List<AspectPreset> = listOf(
        AspectPreset("1:1", DEFAULT_WIDTH, DEFAULT_HEIGHT),
        AspectPreset("16:9", 448, 256),
        AspectPreset("9:16", 256, 448),
        AspectPreset("4:3", 320, 256),
        AspectPreset("3:4", 256, 320)
    )

    enum class PerformanceProfile(
        val label: String,
        val steps: Int,
        val cfg: Float,
        val width: Int,
        val height: Int
    ) {
        FAST("⚡ Cepat", 12, 6.5f, DEFAULT_WIDTH, DEFAULT_HEIGHT),
        BALANCED("📱 Mobile (Rekomendasi)", DEFAULT_STEPS, DEFAULT_CFG, DEFAULT_WIDTH, DEFAULT_HEIGHT),
        QUALITY("🎨 Kualitas", 24, 7.5f, 512, 512)
    }

    fun clampDimension(value: Int): Int {
        if (value <= 0) return 0
        val stepped = (value / DIMENSION_STEP) * DIMENSION_STEP
        return stepped.coerceIn(MIN_DIMENSION, MAX_DIMENSION)
    }

    fun widthForAspectRatio(aspectRatio: String): Int {
        val preset = aspectPresets.find { aspectRatio.startsWith(it.label) }
        if (preset != null) return preset.width
        return when {
            aspectRatio.contains("Kustom") -> DEFAULT_WIDTH
            aspectRatio.contains("16:9") -> 448
            aspectRatio.contains("9:16") -> 256
            aspectRatio.contains("4:3") -> 320
            aspectRatio.contains("3:4") -> 256
            else -> DEFAULT_WIDTH
        }
    }

    fun heightForAspectRatio(aspectRatio: String): Int {
        val preset = aspectPresets.find { aspectRatio.startsWith(it.label) }
        if (preset != null) return preset.height
        return when {
            aspectRatio.contains("Kustom") -> DEFAULT_HEIGHT
            aspectRatio.contains("16:9") -> 256
            aspectRatio.contains("9:16") -> 448
            aspectRatio.contains("4:3") -> 256
            aspectRatio.contains("3:4") -> 320
            else -> DEFAULT_HEIGHT
        }
    }

    fun recommendBackend(context: Context): GpuAccelerationBackend {
        return detectBestMnnBackend(context)
    }

    const val BACKEND_AUTO = "auto"

    data class DeviceGpuProfile(
        val totalRamGb: Double,
        val socModel: String,
        val hardware: String,
        val isAdreno: Boolean,
        val isMali: Boolean,
        val isSnapdragon: Boolean
    )

    fun readDeviceGpuProfile(context: Context): DeviceGpuProfile {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        val totalRamGb = memInfo.totalMem / (1024.0 * 1024.0 * 1024.0)

        val soc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Build.SOC_MODEL.uppercase()
        } else {
            ""
        }
        val hardware = Build.HARDWARE.uppercase()
        val board = Build.BOARD.uppercase()
        val manufacturer = Build.MANUFACTURER.uppercase()
        val combined = "$soc $hardware $board $manufacturer"

        val isSnapdragon = soc.startsWith("SM") ||
            soc.contains("QCS") ||
            soc.contains("QCM") ||
            combined.contains("QUALCOMM")
        val isAdreno = isSnapdragon || combined.contains("ADRENO")
        val isMali = combined.contains("MALI") ||
            soc.startsWith("MT") ||
            hardware.startsWith("MT")

        return DeviceGpuProfile(
            totalRamGb = totalRamGb,
            socModel = soc,
            hardware = hardware,
            isAdreno = isAdreno,
            isMali = isMali,
            isSnapdragon = isSnapdragon
        )
    }

    /**
     * Pilih CPU vs OpenCL untuk model sd-mnn (MNN).
     * Wajib cek dukungan OpenCL runtime — RAM 8GB tidak menjamin OpenCL ada.
     */
    fun detectBestMnnBackend(context: Context): GpuAccelerationBackend {
        val profile = readDeviceGpuProfile(context)
        val openCl = OpenClCapability.probe(context)

        if (!openCl.isSupported) {
            return GpuAccelerationBackend.CPU
        }

        if (profile.totalRamGb < 5.0) {
            return GpuAccelerationBackend.CPU
        }

        if (profile.totalRamGb < 6.0) {
            return if (profile.isAdreno) {
                GpuAccelerationBackend.OPENCL
            } else {
                GpuAccelerationBackend.CPU
            }
        }

        if (profile.isAdreno || profile.isSnapdragon) {
            return GpuAccelerationBackend.OPENCL
        }

        if (profile.isMali) {
            return if (profile.totalRamGb >= 7.0) {
                GpuAccelerationBackend.OPENCL
            } else {
                GpuAccelerationBackend.CPU
            }
        }

        return if (profile.totalRamGb >= 8.0) {
            GpuAccelerationBackend.OPENCL
        } else {
            GpuAccelerationBackend.CPU
        }
    }

    fun resolveMnnBackend(context: Context, savedBackendId: String?): GpuAccelerationBackend {
        if (savedBackendId == null || savedBackendId == BACKEND_AUTO) {
            return detectBestMnnBackend(context)
        }
        val saved = GpuAccelerationBackend.entries.find { it.id == savedBackendId }
            ?: return detectBestMnnBackend(context)
        return when (saved) {
            GpuAccelerationBackend.OPENCL -> {
                if (OpenClCapability.probe(context).isSupported) {
                    GpuAccelerationBackend.OPENCL
                } else {
                    GpuAccelerationBackend.CPU
                }
            }
            GpuAccelerationBackend.QNN_NPU -> GpuAccelerationBackend.QNN_NPU
            GpuAccelerationBackend.CPU,
            GpuAccelerationBackend.VULKAN -> GpuAccelerationBackend.CPU
        }
    }

    fun describeAutoBackendChoice(context: Context, backend: GpuAccelerationBackend): String {
        val profile = readDeviceGpuProfile(context)
        val openCl = OpenClCapability.probe(context)
        val ram = String.format("%.1f", profile.totalRamGb)
        val chip = when {
            profile.socModel.isNotBlank() -> profile.socModel
            profile.hardware.isNotBlank() -> profile.hardware
            else -> "Android"
        }
        val mode = if (backend == GpuAccelerationBackend.OPENCL) "OpenCL GPU" else "CPU"
        if (!openCl.isSupported) {
            return "RAM ${ram}GB • $chip • ${openCl.reason} → CPU"
        }
        val gpu = openCl.gpuRenderer.take(28).ifBlank {
            when {
                profile.isAdreno -> "Adreno"
                profile.isMali -> "Mali"
                else -> "GPU"
            }
        }
        return "RAM ${ram}GB • $chip • $gpu → $mode"
    }

    fun getOpenClSupportInfo(context: Context): OpenClSupportInfo {
        return OpenClCapability.probe(context)
    }

    fun isAutoBackendPreference(savedBackendId: String?): Boolean {
        return savedBackendId == null || savedBackendId == BACKEND_AUTO
    }

    fun recommendedProfileForDevice(context: Context): PerformanceProfile {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        val totalRamGb = memInfo.totalMem / (1024.0 * 1024.0 * 1024.0)
        return when {
            totalRamGb < 6.0 -> PerformanceProfile.FAST
            totalRamGb < 8.0 -> PerformanceProfile.BALANCED
            else -> PerformanceProfile.BALANCED
        }
    }
}
