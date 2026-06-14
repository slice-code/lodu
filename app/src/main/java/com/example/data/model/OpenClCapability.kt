package com.example.data.model

import android.content.Context
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES20
import java.io.File

/**
 * Probes whether the device can run MNN OpenCL (libOpenCL present + GPU not blacklisted).
 * RAM alone is not enough — many 8GB phones ship without a working OpenCL driver.
 */
data class OpenClSupportInfo(
    val isSupported: Boolean,
    val gpuRenderer: String,
    val openClLibraryPaths: List<String>,
    val reason: String
)

object OpenClCapability {

    @Volatile
    private var cached: OpenClSupportInfo? = null

    fun probe(context: Context, forceRefresh: Boolean = false): OpenClSupportInfo {
        if (!forceRefresh) {
            val hit = cached
            if (hit != null) return hit
        }

        val libs = findOpenClLibraries()
        val gpuRenderer = queryGpuRenderer()
        val profile = SdMobileDefaults.readDeviceGpuProfile(context)
        val hasOpenClLib = libs.any { path ->
            path.contains("libOpenCL.so") || path.contains("libPVROCL.so")
        }

        val blacklistReason = openClBlacklistReason(gpuRenderer, profile)
        val emulator = gpuRenderer.contains("emulator", ignoreCase = true) ||
            gpuRenderer.contains("swiftshader", ignoreCase = true) ||
            gpuRenderer.contains("goldfish", ignoreCase = true)

        val supported = hasOpenClLib && blacklistReason == null && !emulator

        val reason = when {
            emulator -> "GPU virtual tidak mendukung OpenCL"
            !hasOpenClLib -> "libOpenCL.so tidak ada di perangkat"
            blacklistReason != null -> blacklistReason
            else -> "OpenCL tersedia (${gpuRenderer.take(40)})"
        }

        val info = OpenClSupportInfo(
            isSupported = supported,
            gpuRenderer = gpuRenderer,
            openClLibraryPaths = libs,
            reason = reason
        )
        cached = info
        return info
    }

    fun clearCache() {
        cached = null
    }

    private fun findOpenClLibraries(): List<String> {
        val libNames = listOf(
            "libOpenCL.so",
            "libPVROCL.so",
            "libOpenCL_android.so"
        )
        val dirs = mutableListOf(
            "/vendor/lib64",
            "/vendor/lib",
            "/system/lib64",
            "/system/lib",
            "/vendor/lib64/egl"
        )

        try {
            val maliSymlink = File("/system/vendor/lib64/egl/libGLES_mali.so")
            if (maliSymlink.exists()) {
                val parts = maliSymlink.canonicalPath.split("/")
                val soc = parts.getOrNull(parts.size - 2)
                if (soc != null) {
                    dirs.add("/vendor/lib64/$soc")
                    dirs.add("/vendor/lib64/egl/$soc")
                }
            }
        } catch (_: Exception) {
            // ignored
        }

        val found = mutableListOf<String>()
        for (dir in dirs.distinct()) {
            for (name in libNames) {
                val file = File(dir, name)
                if (file.exists() && file.length() > 0L) {
                    found.add(file.absolutePath)
                }
            }
        }
        return found.distinct()
    }

    private fun queryGpuRenderer(): String {
        return try {
            val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (display == EGL14.EGL_NO_DISPLAY) return "unknown"

            val version = IntArray(2)
            if (!EGL14.eglInitialize(display, version, 0, version, 1)) return "unknown"

            val attribList = intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_NONE
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            if (!EGL14.eglChooseConfig(display, attribList, 0, configs, 0, 1, numConfigs, 0) ||
                numConfigs[0] == 0
            ) {
                EGL14.eglTerminate(display)
                return "unknown"
            }

            val contextAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
            val eglContext = EGL14.eglCreateContext(
                display, configs[0], EGL14.EGL_NO_CONTEXT, contextAttribs, 0
            )
            if (eglContext == null || eglContext == EGL14.EGL_NO_CONTEXT) {
                EGL14.eglTerminate(display)
                return "unknown"
            }

            val surfaceAttribs = intArrayOf(
                EGL14.EGL_WIDTH, 1,
                EGL14.EGL_HEIGHT, 1,
                EGL14.EGL_NONE
            )
            val surface = EGL14.eglCreatePbufferSurface(display, configs[0], surfaceAttribs, 0)
            if (surface == null || surface == EGL14.EGL_NO_SURFACE) {
                EGL14.eglDestroyContext(display, eglContext)
                EGL14.eglTerminate(display)
                return "unknown"
            }

            if (!EGL14.eglMakeCurrent(display, surface, surface, eglContext)) {
                EGL14.eglDestroySurface(display, surface)
                EGL14.eglDestroyContext(display, eglContext)
                EGL14.eglTerminate(display)
                return "unknown"
            }

            val renderer = GLES20.glGetString(GLES20.GL_RENDERER) ?: "unknown"

            EGL14.eglMakeCurrent(
                display,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_CONTEXT
            )
            EGL14.eglDestroySurface(display, surface)
            EGL14.eglDestroyContext(display, eglContext)
            EGL14.eglTerminate(display)

            renderer.trim()
        } catch (e: Exception) {
            "unknown"
        }
    }

    private fun openClBlacklistReason(
        gpuRenderer: String,
        profile: SdMobileDefaults.DeviceGpuProfile
    ): String? {
        val renderer = gpuRenderer.uppercase()
        val combined = buildString {
            append(profile.socModel)
            append(' ')
            append(profile.hardware)
            append(' ')
            append(renderer)
        }

        val blockedPatterns = listOf(
            "UNISOC", "UIS786", "UIS787", "UIS788", "SC9863", "T610", "T612", "T616", "T700", "T820",
            "MT6761", "MT6762", "MT6765", "MT6768", "MT6769", "MT6771", "MT6833", "MT6853",
            "HELIO G25", "HELIO G35", "HELIO G36", "HELIO G37",
            "KIRIN710", "KIRIN810", "KIRIN820",
            "SWIFTSHADER", "GOLDFISH", "ANDROID EMULATOR", "LLVMPIPE"
        )
        for (pattern in blockedPatterns) {
            if (combined.contains(pattern)) {
                return "Chipset/GPU ini tidak stabil dengan OpenCL MNN"
            }
        }

        if (renderer == "UNKNOWN" || renderer.isBlank()) {
            return "GPU tidak teridentifikasi — OpenCL tidak digunakan"
        }

        return null
    }
}
