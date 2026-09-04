package com.example.hunterxmusic.presentation.ai

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hunterxmusic.domain.model.AiModel
import com.example.hunterxmusic.domain.repository.AiChatStatus
import com.example.hunterxmusic.domain.repository.ChatMessage
import com.example.hunterxmusic.domain.repository.AiRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * MVI-style state container managing chat screen interactions.
 */
data class AiChatState(
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val isLoading: Boolean = false,
    val selectedModel: AiModel = AiModel.DEEPSEEK_R1,
    val isUnlocked: Boolean = false,
    val error: String? = null
)

/**
 * ViewModel acting as the state machine for the CyroSonic AI Assistant section.
 */
class AiChatViewModel(
    private val aiRepository: AiRepository
) : ViewModel() {

    private val _state = mutableStateOf(AiChatState())
    val state: State<AiChatState> = _state

    init {
        updateState()
    }

    /**
     * Switches active AI model while preserving full multi-turn conversation memory.
     */
    fun selectModel(model: AiModel) {
        aiRepository.setModel(model)
        _state.value = _state.value.copy(selectedModel = model)
    }

    /**
     * Updates input text typed by the user in the Chat Input field.
     */
    fun onInputTextChanged(text: String) {
        _state.value = _state.value.copy(inputText = text)
    }

    /**
     * Dispatches user message, handles prompt wrappers inside Repository, and updates loading states.
     */
    fun sendMessage(customText: String? = null) {
        val query = (customText ?: _state.value.inputText).trim()
        if (query.isEmpty() || _state.value.isLoading) return

        // Clear input immediately and set loading to prevent double requests
        _state.value = _state.value.copy(
            inputText = "",
            isLoading = true,
            messages = aiRepository.getChatHistory()
        )
        
        viewModelScope.launch {
            aiRepository.sendMessage(query).collectLatest { status ->
                when (status) {
                    is AiChatStatus.Loading -> {
                        _state.value = _state.value.copy(
                            isLoading = true,
                            messages = aiRepository.getChatHistory()
                        )
                    }
                    is AiChatStatus.Success -> {
                        _state.value = _state.value.copy(
                            isLoading = false,
                            messages = aiRepository.getChatHistory(),
                            isUnlocked = aiRepository.isBypassUnlocked(),
                            error = null
                        )
                    }
                    is AiChatStatus.Error -> {
                        _state.value = _state.value.copy(
                            isLoading = false,
                            messages = aiRepository.getChatHistory(),
                            error = status.message
                        )
                    }
                }
            }
        }
    }

    fun explainSong(title: String, artist: String) {
        val query = "Explain the song \"$title\" by \"$artist\" in detail. What is its musical theory, chords, mood, inspiration, and history?"
        sendMessage(query)
    }

    /**
     * Clears history and resets unlock credentials.
     */
    fun clearChat() {
        aiRepository.clearChat()
        updateState()
    }

    private fun updateState() {
        _state.value = AiChatState(
            messages = aiRepository.getChatHistory(),
            selectedModel = aiRepository.getSelectedModel(),
            isUnlocked = aiRepository.isBypassUnlocked(),
            isLoading = false
        )
    }
}
