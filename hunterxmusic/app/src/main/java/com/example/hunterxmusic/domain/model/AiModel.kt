package com.example.hunterxmusic.domain.model

/**
 * Flagship AI models selectable by the user in CyroSonic AI Chat.
 * Memory and multi-turn conversation context is preserved across model switches.
 */
enum class AiModel(
    val id: String,
    val displayName: String,
    val tag: String,
    val iconEmoji: String,
    val description: String,
    val pollinationsModel: String,
    val fallbackEndpoint: String
) {
    DEEPSEEK_R1(
        id = "deepseek_r1",
        displayName = "DeepSeek R1",
        tag = "Deep Reasoning",
        iconEmoji = "🧠",
        description = "Advanced reasoning, chord analysis & music theory breakdowns",
        pollinationsModel = "deepseek-r1",
        fallbackEndpoint = "https://prexzyapis.com/ai/deepquery"
    ),
    GEMINI_PRO(
        id = "gemini_flash",
        displayName = "Gemini 2.5 Flash",
        tag = "Fast & Creative",
        iconEmoji = "⚡",
        description = "Lightning-fast lyrics writing, mood playlists & artist history",
        pollinationsModel = "gemini",
        fallbackEndpoint = "https://prexzyapis.com/ai/gemini"
    ),
    GPT_4O(
        id = "gpt_4o",
        displayName = "GPT-4o Mini",
        tag = "Conversational Genius",
        iconEmoji = "🌟",
        description = "Charming, natural and empathetic music recommendations",
        pollinationsModel = "openai",
        fallbackEndpoint = "https://prexzyapis.com/ai/ch"
    ),
    LLAMA_3(
        id = "llama_3",
        displayName = "Llama 3.3 70B",
        tag = "Open Meta",
        iconEmoji = "🦙",
        description = "Comprehensive discography knowledge & deep audio insights",
        pollinationsModel = "llama",
        fallbackEndpoint = "https://prexzyapis.com/ai/llama"
    ),
    MISTRAL_LARGE(
        id = "mistral_large",
        displayName = "Mistral Large 2",
        tag = "Music Critic",
        iconEmoji = "🔮",
        description = "Nuanced critique of song production, beats and mix engineering",
        pollinationsModel = "mistral",
        fallbackEndpoint = "https://prexzyapis.com/ai/mistral"
    ),
    QWEN_POLYGLOT(
        id = "qwen_2_5",
        displayName = "Qwen 2.5 Polyglot",
        tag = "Multilingual",
        iconEmoji = "🌐",
        description = "Hindi, Bollywood, Punjabi, K-Pop, J-Pop & global lyric translation",
        pollinationsModel = "qwen",
        fallbackEndpoint = "https://prexzyapis.com/ai/ch"
    );

    companion object {
        val DEFAULT = DEEPSEEK_R1

        fun fromId(id: String?): AiModel {
            return entries.firstOrNull { it.id == id } ?: DEFAULT
        }
    }
}
