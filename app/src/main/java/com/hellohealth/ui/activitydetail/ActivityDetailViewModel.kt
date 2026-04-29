package com.hellohealth.ui.activitydetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hellohealth.domain.model.ActivityDetail
import com.hellohealth.domain.repository.ActivityRepository
import com.hellohealth.ui.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

sealed interface ActivityDetailState {
    data object Loading : ActivityDetailState
    data class Success(val detail: ActivityDetail) : ActivityDetailState
    data class Error(val message: String) : ActivityDetailState
}

@HiltViewModel
class ActivityDetailViewModel @Inject constructor(
    private val activityRepository: ActivityRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val sessionId: String = savedStateHandle.get<String>(Screen.ActivityDetail.activityIdArg).orEmpty()
    private val startTimeHint: Instant? = savedStateHandle.get<Long>(Screen.ActivityDetail.sessionStartArg)
        ?.takeIf { it > 0L }
        ?.let(Instant::ofEpochMilli)
    private val endTimeHint: Instant? = savedStateHandle.get<Long>(Screen.ActivityDetail.sessionEndArg)
        ?.takeIf { it > 0L }
        ?.let(Instant::ofEpochMilli)

    private val _state = MutableStateFlow<ActivityDetailState>(ActivityDetailState.Loading)
    val state: StateFlow<ActivityDetailState> = _state.asStateFlow()

    init {
        loadActivityDetail()
    }

    fun retry() {
        loadActivityDetail()
    }

    private fun loadActivityDetail() {
        viewModelScope.launch {
            _state.value = ActivityDetailState.Loading
            val detail = activityRepository.getExerciseSessionDetail(
                sessionId = sessionId,
                startTimeHint = startTimeHint,
                endTimeHint = endTimeHint
            )
            _state.value = if (detail != null) {
                ActivityDetailState.Success(detail)
            } else {
                ActivityDetailState.Error("We couldn't load the analytics for this workout.")
            }
        }
    }
}
