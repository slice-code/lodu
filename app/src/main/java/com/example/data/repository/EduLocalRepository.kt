package com.example.data.repository

import com.example.data.database.ChatDao
import com.example.data.database.DocumentDao
import com.example.data.model.ChatMessage
import com.example.data.model.DocumentChunk
import com.example.data.model.StudyDocument
import kotlinx.coroutines.flow.Flow

class EduLocalRepository(
    private val chatDao: ChatDao,
    private val documentDao: DocumentDao
) {
    val allMessages: Flow<List<ChatMessage>> = chatDao.getAllMessages()
    val allDocuments: Flow<List<StudyDocument>> = documentDao.getAllDocuments()

    suspend fun insertMessage(message: ChatMessage) {
        chatDao.insertMessage(message)
    }

    suspend fun clearChatHistory() {
        chatDao.clearHistory()
    }

    suspend fun addDocument(document: StudyDocument, chunks: List<DocumentChunk>) {
        documentDao.insertDocument(document)
        documentDao.insertChunks(chunks)
    }

    suspend fun deleteDocument(document: StudyDocument) {
        documentDao.deleteDocument(document)
        documentDao.deleteChunksByDocumentId(document.id)
    }

    suspend fun getAllChunks(): List<DocumentChunk> {
        return documentDao.getAllChunks()
    }
}
