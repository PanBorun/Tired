package com.tired.app.presentation.screens.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tired.app.domain.model.ChatRequestContext
import com.tired.app.domain.model.ChatConversationSummary
import com.tired.app.domain.model.ChatUiMessage
import com.tired.app.domain.repository.ChatRepository
import com.tired.app.domain.repository.UsageDataOrigin
import com.tired.app.domain.repository.UsageDataResult
import com.tired.app.domain.repository.UsageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject

private val DEFAULT_PROMPTS = listOf(
    "Based on today's usage, what should I adjust first?",
    "Why was I classified into this fatigue pattern?",
    "Give me a realistic 30-minute wind-down plan for tonight.",
    "Which apps affected my state the most today?"
)

data class AIAssistantUiState(
    val selectedConversationId: String = "",
    val conversations: List<ChatConversationSummary> = emptyList(),
    val messages: List<ChatUiMessage> = emptyList(),
    val input: String = "",
    val isSending: Boolean = false,
    val context: ChatRequestContext = ChatRequestContext(
        note = "Today's device activity is unavailable, so replies will use general wellness guidance."
    ),
    val errorMessage: String? = null,
    val suggestedPrompts: List<String> = DEFAULT_PROMPTS
)

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class AIAssistantViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val usageRepository: UsageRepository
) : ViewModel() {
    private val initialConversationId = newConversationId()

    private val _uiState = MutableStateFlow(
        AIAssistantUiState(selectedConversationId = initialConversationId)
    )
    val uiState: StateFlow<AIAssistantUiState> = _uiState.asStateFlow()
    private val selectedConversationId = MutableStateFlow(initialConversationId)

    init {
        observeConversations()
        observeMessages()
        refreshContext()
    }

    fun updateInput(input: String) {
        _uiState.update { it.copy(input = input) }
    }

    fun refreshContext() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    context = resolveContext(),
                    errorMessage = null
                )
            }
        }
    }

    fun sendCurrentMessage() {
        val message = _uiState.value.input.trim()
        if (message.isEmpty() || _uiState.value.isSending) return

        viewModelScope.launch {
            val conversationId = _uiState.value.selectedConversationId
            _uiState.update {
                it.copy(
                    isSending = true,
                    errorMessage = null,
                    input = ""
                )
            }

            val result = chatRepository.sendMessage(
                conversationId = conversationId,
                userInput = message,
                includeTodayUsage = _uiState.value.context.usesUsageContext
            )

            _uiState.update {
                it.copy(
                    isSending = false,
                    errorMessage = result.exceptionOrNull()?.message
                )
            }
        }
    }

    fun sendPrompt(prompt: String) {
        updateInput(prompt)
        sendCurrentMessage()
    }

    fun startNewConversation() {
        val newId = newConversationId()
        selectedConversationId.value = newId
        _uiState.update {
            it.copy(
                selectedConversationId = newId,
                messages = emptyList(),
                input = "",
                errorMessage = null
            )
        }
    }

    fun openConversation(conversationId: String) {
        selectedConversationId.value = conversationId
        _uiState.update {
            it.copy(
                selectedConversationId = conversationId,
                errorMessage = null,
                input = ""
            )
        }
    }

    private fun observeMessages() {
        viewModelScope.launch {
            selectedConversationId
                .flatMapLatest { conversationId ->
                    chatRepository.observeMessages(conversationId)
                }
                .collect { messages ->
                    _uiState.update {
                        it.copy(messages = messages)
                    }
                }
        }
    }

    private fun observeConversations() {
        viewModelScope.launch {
            chatRepository.observeConversations().collect { conversations ->
                _uiState.update {
                    val currentId = it.selectedConversationId
                    val resolvedId = when {
                        conversations.any { item -> item.conversationId == currentId } -> currentId
                        conversations.isNotEmpty() &&
                            it.messages.isEmpty() &&
                            currentId == initialConversationId -> conversations.first().conversationId
                        else -> currentId
                    }
                    if (resolvedId != currentId) {
                        selectedConversationId.value = resolvedId
                    }
                    it.copy(
                        selectedConversationId = resolvedId,
                        conversations = conversations
                    )
                }
            }
        }
    }

    private suspend fun resolveContext(): ChatRequestContext {
        return when (val result = usageRepository.loadDailyFatigueData(today())) {
            is UsageDataResult.Success -> {
                val summary = buildString {
                    append("Screen time ${result.data.totalScreenTimeMinutes} minutes, ")
                    append("${result.data.totalLaunchCount} launches, ")
                    append("${result.data.lateNightMinutes} minutes late at night.")
                }

                ChatRequestContext(
                    summary = summary,
                    note = when (result.origin) {
                        UsageDataOrigin.REAL_DEVICE -> "Replies will use today's real device activity."
                    },
                    usesUsageContext = true
                )
            }

            UsageDataResult.PermissionRequired,
            UsageDataResult.EmptyData -> {
                ChatRequestContext(
                    note = "Today's device activity is unavailable, so replies will use general wellness guidance.",
                    usesUsageContext = false
                )
            }
        }
    }

    private fun today(): String = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

    private companion object {
        fun newConversationId(): String = "draft-${UUID.randomUUID()}"
    }
}
