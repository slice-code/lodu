package com.example.data.database

import androidx.room.*
import com.example.data.model.DocumentChunk
import com.example.data.model.StudyDocument
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {
    @Query("SELECT * FROM study_documents ORDER BY addedTime DESC")
    fun getAllDocuments(): Flow<List<StudyDocument>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: StudyDocument)

    @Delete
    suspend fun deleteDocument(document: StudyDocument)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChunks(chunks: List<DocumentChunk>)

    @Query("DELETE FROM document_chunks WHERE documentId = :documentId")
    suspend fun deleteChunksByDocumentId(documentId: String)

    @Query("SELECT * FROM document_chunks")
    suspend fun getAllChunks(): List<DocumentChunk>
}
