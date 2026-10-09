package com.hellohealth.ui.coaching

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hellohealth.domain.model.coaching.CoachingInsight
import com.hellohealth.domain.repository.CoachingRepository
import com.hellohealth.domain.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs the full Coaching screen: the daily insight, the opt-in consent toggle, and a grounded chat.
 * Every coaching call goes through [CoachingRepository] (LLM when opted in + reachable, else the
 * on-device rule-based coach — never throws), so this VM has no error state; a failed LLM call simply
 * returns rule-based text tagged accordingly.
 *
 * The chat is a simple in-memory transcript (not persisted) — a fresh conversation each visit, which
 * suits short "how's my day?" questions and keeps no extra health data at rest.
 */
@HiltViewModel
class CoachingScreenViewModel @Inject constructor(
    private val coachingRepository: CoachingRepository,
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    /** A single chat turn in the transcript. */
    data class ChatTurn(val fromUser: Boolean, val text: String, val isRuleBased: Boolean = false)

    data class CoachingScreenUiState(
        val enabled: Boolean = false,
        val insight: String = "",
        val insightIsRuleBased: Boolean = false,
        val insightLoading: Boolean = true,
        val transcript: List<ChatTurn> = emptyList(),
        val sending: Boolean = false,
    )

    private val _uiState = MutableStateFlow(CoachingScreenUiState())
    val uiState: StateFlow<CoachingScreenUiState> = _uiState.asStateFlow()

    init {
        // OBSERVE the opt-in flag continuously rather than sampling it once. On a fresh launch the
        // opted-in profile row may not yet be in local Room when the screen opens; a one-shot read
        // then saw `false` and served rule-based text, with the LLM result only appearing after the
        // user toggled the switch (which forced a write + reload). By reacting to every flag value we
        // re-run dailyInsight() when it resolves true, so the online result wins without any toggle.
        profileRepository.observeAiCoachingEnabled()
            .distinctUntilChanged()
            .onEach { enabled -> loadInsight(enabled) }
            .launchIn(viewModelScope)
    }

    private fun loadInsight(enabled: Boolean) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(enabled = enabled, insightLoading = true)
            val insight = coachingRepository.dailyInsight()
            _uiState.value = _uiState.value.copy(
                insight = insight.text,
                insightIsRuleBased = insight.source == CoachingInsight.Source.RULE_BASED,
                insightLoading = false,
            )
        }
    }

    /** Toggle AI Coaching consent. The flag stream reacts and reloads the insight (LLM once enabled). */
    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch {
            profileRepository.setAiCoachingEnabled(enabled)
        }
    }

    /** Send a question. Appends the user turn immediately, then the coach's reply. Blank is ignored. */
    fun send(question: String) {
        val trimmed = question.trim()
        if (trimmed.isEmpty() || _uiState.value.sending) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                transcript = _uiState.value.transcript + ChatTurn(fromUser = true, text = trimmed),
                sending = true,
            )
            val answer: CoachingInsight = coachingRepository.ask(trimmed)
            _uiState.value = _uiState.value.copy(
                transcript = _uiState.value.transcript + ChatTurn(
                    fromUser = false,
                    text = answer.text,
                    isRuleBased = answer.source == CoachingInsight.Source.RULE_BASED,
                ),
                sending = false,
            )
        }
    }
}
