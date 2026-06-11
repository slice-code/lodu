package com.example.data.model

enum class GpuAccelerationBackend(
    val id: String,
    val displayName: String,
    val description: String,
    val speedText: String,
    val speedMultiplier: Float, // Adjusts generation speed and visual simulation response
    val technicalName: String, // E.g., VkDevice, CL_DEVICE_TYPE_GPU
    val hardwareRecommendation: String
) {
    CPU(
        id = "cpu",
        displayName = "Alibaba MNN CPU (Fallback)",
        description = "Menjalankan model Stable Diffusion mobile melalui Alibaba MNN dengan optimasi ARM NEON dan multi-threading. Paling kompatibel untuk semua perangkat Android.",
        speedText = "SD 1.5: ~15.0s/step • SDXL: tidak disarankan",
        speedMultiplier = 3.5f,
        technicalName = "MNN Interpreter / ARM NEON",
        hardwareRecommendation = "Dukungan 100% pada semua ponsel pintar (Universal)."
    ),
    VULKAN(
        id = "vulkan",
        displayName = "MNN Vulkan / OpenCL GPU",
        description = "Menjalankan operator tensor model difusi melalui delegate GPU MNN untuk perangkat yang mendukung Vulkan atau OpenCL.",
        speedText = "SD 1.5: ~1.1s/step • SDXL Turbo: cepat",
        speedMultiplier = 0.5f,
        technicalName = "MNN Vulkan/OpenCL Backend",
        hardwareRecommendation = "Sangat Direkomendasikan untuk Xiaomi Redmi Note 14 (Adreno 610/613/710 atau GPU Mali-G615) & semua chipset modern."
    ),
    OPENCL(
        id = "opencl",
        displayName = "MNN OpenCL / OpenGL",
        description = "Memetakan instruksi tensor ke kernel OpenCL atau OpenGL ES sebagai alternatif jika driver Vulkan tidak stabil.",
        speedText = "SD 1.5: ~2.2s/step",
        speedMultiplier = 1.2f,
        technicalName = "MNN OpenCL/OpenGL Backend",
        hardwareRecommendation = "Pilihan stabil untuk ponsel Android kelas menengah/lama (Mali GPU & Adreno lama)."
    ),
    QNN_NPU(
        id = "qnn",
        displayName = "Qualcomm QNN NPU",
        description = "Mengeksekusi graph Stable Diffusion terkuantisasi melalui Qualcomm QNN SDK pada Hexagon NPU/DSP Snapdragon.",
        speedText = "SD 1.5: ~0.8s/step • SDXL Turbo: optimal",
        speedMultiplier = 0.3f,
        technicalName = "Qualcomm QNN SDK / Hexagon NPU",
        hardwareRecommendation = "Teroptimasi khusus untuk perangkat Snapdragon modern dengan QNN runtime tersedia."
    )
}
