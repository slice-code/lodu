package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

enum class MessageSender {
    USER, ASSISTANT, SYSTEM
}

enum class MessageType {
    TEXT, IMAGE, DIAGRAM, FILE
}

@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val sessionId: String,
    val text: String,
    val sender: MessageSender,
    val timestamp: Long = System.currentTimeMillis(),
    val type: MessageType = MessageType.TEXT,
    val attachmentPath: String? = null,
    val rContext: String? = null // For storing reference context used in RAG
)
