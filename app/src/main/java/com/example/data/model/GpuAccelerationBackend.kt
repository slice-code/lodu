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
        displayName = "CPU Standard (Fallback)",
        description = "Menggunakan instruksi ARM Neon & multi-threading teroptimasi. Tidak mengandalkan driver GPU eksternal, sangat stabil tetapi lebih lambat dan memakan banyak daya baterai.",
        speedText = "LTM: ~2 token/detik • Sketsa: 15.0s/step",
        speedMultiplier = 3.5f,
        technicalName = "XNNPACK / CPU Execution Provider",
        hardwareRecommendation = "Dukungan 100% pada semua ponsel pintar (Universal)."
    ),
    VULKAN(
        id = "vulkan",
        displayName = "GPU Vulkan API (Performa Tinggi)",
        description = "Mengkompilasi parser model langsung menjadi shader Vulkan (SPIR-V) pada chip grafis. Memberikan throughput FLOPS maksimal dan latensi rendah untuk model bahasa lokal & Stable Diffusion.",
        speedText = "LLM: ~22 token/detik • Sketsa: 1.1s/step",
        speedMultiplier = 0.5f,
        technicalName = "MediaPipe Delegate / Vulkan KHR API",
        hardwareRecommendation = "Sangat Direkomendasikan untuk Xiaomi Redmi Note 14 (Adreno 610/613/710 atau GPU Mali-G615) & semua chipset modern."
    ),
    OPENCL(
        id = "opencl",
        displayName = "GPU OpenCL / OpenGL (Kompatibilitas Luas)",
        description = "Memetakan instruksi tensor ke kernel OpenCL / OpenGL ES. Cocok sebagai alternatif jika driver Vulkan pada perangkat sedikit tidak stabil atau untuk model visual tertentu.",
        speedText = "LLM: ~14 token/detik • Sketsa: 2.2s/step",
        speedMultiplier = 1.2f,
        technicalName = "ONNX OpenCL Delegate / Mobile GLES",
        hardwareRecommendation = "Pilihan stabil untuk ponsel Android kelas menengah/lama (Mali GPU & Adreno lama)."
    ),
    QNN_NPU(
        id = "qnn",
        displayName = "Qualcomm QNN / NNAPI Accelerator",
        description = "Mengeksploitasi NPU (Neural Processing Unit) & DSP terdedikasi pada SoC Qualcomm Snapdragon via Qualcomm Neural Network SDK. Konsumsi daya ultra rendah dan respons instan.",
        speedText = "LLM: ~28 token/detik • Sketsa: 0.8s/step",
        speedMultiplier = 0.3f,
        technicalName = "QNN HTA / Android NNAPI Delegate",
        hardwareRecommendation = "Teroptimasi khusus untuk Xiaomi Redmi Note 14 Pro/Pro+ (Snapdragon 7s Gen 3 / Dimensity NPU)."
    )
}
