package com.example.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object ModelDownloadManager {
    private val _downloadingModelIds = MutableStateFlow<Set<String>>(emptySet())
    val downloadingModelIds = _downloadingModelIds.asStateFlow()

    private val _modelDownloadStatus = MutableStateFlow("")
    val modelDownloadStatus = _modelDownloadStatus.asStateFlow()

    private val _modelDownloadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val modelDownloadProgress = _modelDownloadProgress.asStateFlow()

    fun setDownloading(modelId: String, isDownloading: Boolean) {
        if (isDownloading) {
            _downloadingModelIds.value = _downloadingModelIds.value + modelId
        } else {
            _downloadingModelIds.value = _downloadingModelIds.value - modelId
            _modelDownloadProgress.value = _modelDownloadProgress.value - modelId
        }
    }

    fun setStatus(status: String) {
        _modelDownloadStatus.value = status
    }

    fun setProgress(modelId: String, progress: Float) {
        _modelDownloadProgress.value = _modelDownloadProgress.value + (modelId to progress)
    }
}
