package com.example.service

import android.content.Context
import android.net.Uri
import com.example.data.model.DocumentChunk
import com.example.data.model.StudyDocument
import com.example.data.repository.EduLocalRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.UUID
import kotlin.math.sqrt

/**
 * Handles Local RAG (Retrieval-Augmented Generation)
 * - Text Extraction from Files (PDF/TXT)
 * - Semantic Text Chunking (100-200 words per chunk with overlap)
 * - Local Text Embedding Generation (Simulating/stubbing mini All-MiniLM-L6-v2)
 * - Fast Vector Similarity Cosine Search inside SQLite/Room memory.
 */
class LocalRAGPipeline(
    private val context: Context,
    private val repository: EduLocalRepository
) {

    /**
     * Parses document content and processes it into vector-embedded chunks.
     */
    suspend fun processDocument(uri: Uri, fileName: String): Result<StudyDocument> = withContext(Dispatchers.IO) {
        try {
            // 1. Parse/Extract Text
            val textContent = extractText(uri)
            if (textContent.isBlank()) {
                return@withContext Result.failure(Exception("File kosong atau format tidak didukung."))
            }

            // Create document meta
            val docId = UUID.randomUUID().toString()
            val doc = StudyDocument(
                id = docId,
                fileName = fileName,
                filePath = uri.toString(),
                fileSize = textContent.length.toLong()
            )

            // 2. Document Chunking
            val chunksText = chunkText(textContent, chunkSize = 400, overlap = 100)
            
            // 3. Generate Local Embeddings & Build Chunks list
            val documentChunks = chunksText.mapIndexed { index, chunkText ->
                val embeddingVector = generateEmbedding(chunkText)
                DocumentChunk(
                    id = UUID.randomUUID().toString(),
                    documentId = docId,
                    content = chunkText,
                    pageNumber = (index / 2) + 1, // Simulated page number
                    embedding = embeddingVector
                )
            }

            // 4. Save to Room database
            repository.addDocument(doc, documentChunks)

            Result.success(doc)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Extracts plain text from the file Uri (TXT/Simulated PDF)
     */
    private fun extractText(uri: Uri): String {
        val stringBuilder = StringBuilder()
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                var line: String? = reader.readLine()
                while (line != null) {
                    stringBuilder.append(line).append("\n")
                    line = reader.readLine()
                }
            }
        }
        return stringBuilder.toString()
    }

    /**
     * Simple sliding window text chunker that splits text into chunks of roughly N characters
     * with a specified overlap.
     */
    private fun chunkText(text: String, chunkSize: Int, overlap: Int): List<String> {
        val words = text.split(Regex("\\s+"))
        if (words.size <= chunkSize) return listOf(text)

        val chunks = mutableListOf<String>()
        var start = 0
        while (start < words.size) {
            val end = kotlin.math.min(start + chunkSize, words.size)
            val chunk = words.subList(start, end).joinToString(" ")
            chunks.add(chunk)
            if (end == words.size) break
            start += (chunkSize - overlap)
        }
        return chunks
    }

    /**
     * Lightweight local embedding generator representation.
     * Generates a 384-dimensional float vector based on text semantic frequencies,
     * ensuring fully stable JVM vector calculations.
     */
    fun generateEmbedding(text: String): List<Float> {
        // Simple hash-deterministic semantic float encoder representing All-MiniLM-L6-v2 embeddings
        val vector = FloatArray(384) { 0.0f }
        val tokens = text.lowercase().split(Regex("[^a-zA-Z0-9]+")).filter { it.isNotBlank() }
        
        tokens.forEach { token ->
            val hashCode = token.hashCode()
            // Map token hash to multiple positions in vector (dense representation simulation)
            for (i in 0..4) {
                val index = Math.abs((hashCode + i * 31) % 384)
                vector[index] += 1.0f / (i + 1)
            }
        }

        // L2 Normalization
        var sumSquares = 0.0f
        for (f in vector) {
            sumSquares += f * f
        }
        val norm = sqrt(sumSquares)
        if (norm > 0) {
            for (i in vector.indices) {
                vector[i] = vector[i] / norm
            }
        }

        return vector.toList()
    }

    /**
     * Performs an on-device Cosine Similarity query search through all Room database chunks.
     */
    suspend fun retrieveRelevantContext(query: String, topK: Int = 2): String? = withContext(Dispatchers.IO) {
        val queryEmbedding = generateEmbedding(query)
        val allChunks = repository.getAllChunks()
        if (allChunks.isEmpty()) return@withContext null

        // Measure Cosine Similarity for each chunk
        val scoredChunks = allChunks.map { chunk ->
            val score = cosineSimilarity(queryEmbedding, chunk.embedding)
            chunk to score
        }

        // Sort descending and retrieve Top K
        val topMatches = scoredChunks
            .filter { it.second > 0.05f } // similarity threshold
            .sortedByDescending { it.second }
            .take(topK)

        if (topMatches.isEmpty()) return@withContext null

        topMatches.joinToString("\n\n---\n\n") {
            "[Dari File: ${it.first.documentId.take(5)}... Hal: ${it.first.pageNumber}]:\n${it.first.content}"
        }
    }

    private fun cosineSimilarity(v1: List<Float>, v2: List<Float>): Float {
        if (v1.size != v2.size || v1.isEmpty()) return 0.0f
        var dotProduct = 0.0f
        var normA = 0.0f
        var normB = 0.0f
        for (i in v1.indices) {
            dotProduct += v1[i] * v2[i]
            normA += v1[i] * v1[i]
            normB += v2[i] * v2[i]
        }
        return if (normA > 0 && normB > 0) {
            dotProduct / (sqrt(normA) * sqrt(normB))
        } else {
            0.0f
        }
    }
}
