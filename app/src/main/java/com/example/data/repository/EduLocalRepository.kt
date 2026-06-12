package com.example.data.repository

import com.example.data.database.ChatDao
import com.example.data.database.DocumentDao
import com.example.data.model.ChatMessage
import com.example.data.model.ChatSession
import com.example.data.model.DocumentChunk
import com.example.data.model.StudyDocument
import kotlinx.coroutines.flow.Flow

class EduLocalRepository(
    private val chatDao: ChatDao,
    private val documentDao: DocumentDao
) {
    val allSessions: Flow<List<ChatSession>> = chatDao.getAllSessions()
    val allDocuments: Flow<List<StudyDocument>> = documentDao.getAllDocuments()

    fun getMessagesForSession(sessionId: String): Flow<List<ChatMessage>> {
        return chatDao.getMessagesForSession(sessionId)
    }

    suspend fun insertSession(session: ChatSession) {
        chatDao.insertSession(session)
    }

    suspend fun insertMessage(message: ChatMessage) {
        chatDao.insertMessage(message)
    }

    suspend fun updateSessionTitle(sessionId: String, title: String) {
        chatDao.updateSessionTitle(sessionId, title)
    }

    suspend fun deleteSession(sessionId: String) {
        chatDao.deleteSession(sessionId)
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
