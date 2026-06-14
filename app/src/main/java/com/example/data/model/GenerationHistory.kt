package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "generation_history")
data class GenerationHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val prompt: String,
    val negativePrompt: String,
    val modelId: String,
    val loraModel: String,
    val aspectRatio: String,
    val customWidth: Int,
    val customHeight: Int,
    val cfgScale: Float,
    val steps: Int,
    val seed: Long,
    val imagePath: String, // Path to saved PNG on local storage
    val timestamp: Long = System.currentTimeMillis()
)
