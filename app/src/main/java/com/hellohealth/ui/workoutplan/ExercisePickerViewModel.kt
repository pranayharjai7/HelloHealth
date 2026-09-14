package com.hellohealth.ui.workoutplan

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hellohealth.domain.model.Exercise
import com.hellohealth.domain.repository.ExerciseRepository
import com.hellohealth.domain.repository.WorkoutPlanRepository
import com.hellohealth.ui.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI state for the catalog picker. [results] is the current (debounced, category-filtered) search
 * hit list; [categories] populates the filter chips; [selectedCategory] is the active filter (null =
 * all). [catalogEmpty] flags a failed/incomplete seed so the screen shows a graceful "catalog
 * unavailable" state rather than a blank list. [isSearching] gates a small progress hint.
 */
data class ExercisePickerUiState(
    val query: String = "",
    val results: List<Exercise> = emptyList(),
    val categories: List<String> = emptyList(),
    val selectedCategory: String? = null,
    val catalogEmpty: Boolean = false,
    val isSearching: Boolean = false,
)

/**
 * Backs [ExercisePickerScreen]. Debounces the query and re-runs [ExerciseRepository.search] on every
 * query/category change (flatMapLatest cancels a stale search). Selecting an exercise appends it to
 * the day with default targets (targetSets=3) via [WorkoutPlanRepository.addExercise] and signals the
 * screen to navigate back. The catalog is read-only and local — no session/sync surface here.
 */
@OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class ExercisePickerViewModel @Inject constructor(
    private val exerciseRepository: ExerciseRepository,
    private val workoutPlanRepository: WorkoutPlanRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val dayId: String = savedStateHandle.get<String>(Screen.ExercisePicker.dayIdArg).orEmpty()

    private val _query = MutableStateFlow("")
    private val _selectedCategory = MutableStateFlow<String?>(null)
    private val _categories = MutableStateFlow<List<String>>(emptyList())
    private val _catalogEmpty = MutableStateFlow(false)
    private val _isSearching = MutableStateFlow(false)

    /** Emitted once when an exercise has been added, so the screen can pop back. */
    private val _addedEvent = MutableStateFlow(false)
    val addedEvent: StateFlow<Boolean> = _addedEvent.asStateFlow()

    private val searchResults: StateFlow<List<Exercise>> =
        combine(_query.debounce(250).distinctUntilChanged(), _selectedCategory) { q, cat -> q to cat }
            .flatMapLatest { (q, cat) ->
                flow {
                    _isSearching.value = true
                    val hits = exerciseRepository.search(q)
                    emit(if (cat == null) hits else hits.filter { it.category.equals(cat, ignoreCase = true) })
                    _isSearching.value = false
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val uiState: StateFlow<ExercisePickerUiState> = combine(
        combine(_query, searchResults) { q, r -> q to r },
        _categories,
        _selectedCategory,
        _catalogEmpty,
        _isSearching,
    ) { (query, results), categories, selectedCategory, empty, searching ->
        ExercisePickerUiState(
            query = query,
            results = results,
            categories = categories,
            selectedCategory = selectedCategory,
            catalogEmpty = empty,
            isSearching = searching,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ExercisePickerUiState(),
    )

    init {
        viewModelScope.launch {
            _categories.value = exerciseRepository.getAllCategories()
            _catalogEmpty.value = exerciseRepository.count() == 0
        }
    }

    fun updateQuery(q: String) { _query.value = q }

    fun selectCategory(category: String?) { _selectedCategory.value = category }

    fun addExercise(exerciseId: String) {
        viewModelScope.launch {
            workoutPlanRepository.addExercise(dayId = dayId, exerciseId = exerciseId)
            _addedEvent.value = true
        }
    }
}
