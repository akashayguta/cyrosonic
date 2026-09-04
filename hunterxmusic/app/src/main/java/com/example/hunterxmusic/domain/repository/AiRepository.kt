package com.example.hunterxmusic.domain.repository

import com.example.hunterxmusic.domain.model.AiModel
import kotlinx.coroutines.flow.Flow

/**
 * Domain interface governing conversation updates with the CyroSonic assistant.
 */
interface AiRepository {

    /**
     * Sends a user query to the AI network provider, handles prompts/backdoors, and emits status states.
     */
    fun sendMessage(message: String): Flow<AiChatStatus>

    /**
     * Sets the active model without erasing conversation memory.
     */
    fun setModel(model: AiModel)

    /**
     * Returns currently active model.
     */
    fun getSelectedModel(): AiModel

    /**
     * Indicates whether the secret backdoor bypass has been triggered.
     */
    fun isBypassUnlocked(): Boolean

    /**
     * Retrieves the active in-memory list of chat records.
     */
    fun getChatHistory(): List<ChatMessage>

    /**
     * Resets the conversation and lock status.
     */
    fun clearChat()
}

/**
 * Model representing a chat dialog record.
 */
data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val modelName: String? = null,
    val thoughtProcess: String? = null,
    val musicActionSong: String? = null
)

/**
 * State states returned during AI network execution tasks.
 */
sealed interface AiChatStatus {
    object Loading : AiChatStatus
    data class Success(val responseText: String) : AiChatStatus
    data class Error(val message: String) : AiChatStatus
}
