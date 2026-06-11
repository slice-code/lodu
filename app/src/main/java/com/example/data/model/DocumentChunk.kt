package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.util.UUID

@Entity(tableName = "study_documents")
data class StudyDocument(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val fileName: String,
    val filePath: String,
    val fileSize: Long,
    val addedTime: Long = System.currentTimeMillis()
)

@Entity(tableName = "document_chunks")
data class DocumentChunk(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val documentId: String,
    val content: String,
    val pageNumber: Int,
    val embedding: List<Float> // In-memory Float array for Vector Similarity Search
)

class Converters {
    private val moshi = Moshi.Builder().build()
    private val floatListType = Types.newParameterizedType(List::class.java, java.lang.Float::class.java)
    private val jsonAdapter = moshi.adapter<List<Float>>(floatListType)

    @TypeConverter
    fun fromEmbedding(value: List<Float>?): String? {
        return value?.let { jsonAdapter.toJson(it) }
    }

    @TypeConverter
    fun toEmbedding(value: String?): List<Float>? {
        return value?.let { jsonAdapter.fromJson(it) }
    }
}
