package com.hellohealth.ui.nutrition

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hellohealth.core.logging.AppLogger
import com.hellohealth.core.logging.FeatureTag
import com.hellohealth.data.local.entities.CachedFoodEntity
import com.hellohealth.domain.model.nutrition.MealCategory
import com.hellohealth.domain.model.nutrition.NutritionDaySummary
import com.hellohealth.domain.repository.NutritionRepository
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * Backs the full Nutrition screen: the day's grouped food log + water, plus a debounced food-search
 * typeahead over the local cache augmented by USDA/Open Food Facts (all with defined fallbacks in the
 * repo, so search never throws). Writes are fire-and-forget through the repository (Room-first,
 * offline-first); the day summary Flow re-emits on its own once the write lands, so the UI needs no
 * manual refresh.
 *
 * Deliberately its own VM (not the dashboard's [com.hellohealth.ui.dashboard.NutritionViewModel]) —
 * that one is the compact card join; this one owns the full-screen log + search + logging actions.
 */
@OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class NutritionScreenViewModel @Inject constructor(
    private val nutritionRepository: NutritionRepository,
    private val nutritionGoalsRepository: com.hellohealth.domain.repository.NutritionGoalsRepository,
    private val profileRepository: com.hellohealth.domain.repository.ProfileRepository,
    activityRepository: com.hellohealth.domain.repository.ActivityRepository,
) : ViewModel() {

    private val today: String = LocalDate.now(ZoneId.systemDefault()).toString()

    val daySummary: StateFlow<NutritionDaySummary> =
        nutritionRepository.observeDaySummary(today).stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = NutritionDaySummary.empty(today),
        )

    /** Resolved targets (profile derivation + the user's stored overrides) for the hero ring + macro bars. */
    val targets: StateFlow<com.hellohealth.domain.health.NutritionTargets.ResolvedTargets?> =
        nutritionGoalsRepository.observeNutritionGoals()
            .flatMapLatest { goals ->
                flow {
                    val profile = runCatching { profileRepository.getProfile() }.getOrNull()
                    emit(com.hellohealth.domain.health.NutritionTargets.resolve(profile, goals))
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Today's calories burned (active + BMR) for the "burned" side stat; null when unknown. */
    val caloriesOut: StateFlow<Double?> =
        activityRepository.observeCaloriesOutForDay(today)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /**
     * Debounced typeahead results paired with the query they were computed for. A blank query
     * short-circuits to empty (no network); a non-blank query debounces 300 ms, dedupes, then hits
     * the repo (cache → remote → cache). The repo swallows all remote failures, so this Flow never
     * errors — worst case it is the local matches. Carrying the source query lets [searchState]
     * tell "results for the CURRENT query" apart from "stale results for a previous query" (= still
     * loading), without any mutable latch.
     */
    private val resultsFor: StateFlow<Pair<String, List<CachedFoodEntity>>> =
        _query
            .debounce(300)
            .distinctUntilChanged()
            .flatMapLatest { q ->
                flow {
                    if (q.isBlank()) {
                        emit("" to emptyList())
                    } else {
                        val hits = runCatching { nutritionRepository.searchFoods(q) }.getOrElse { e ->
                            AppLogger.w(FeatureTag.NUTRITION, "searchFoods failed for '$q'", e)
                            emptyList()
                        }
                        emit(q to hits)
                    }
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = "" to emptyList(),
            )

    /** The current query's matches (empty while still debouncing/fetching). */
    val searchResults: StateFlow<List<CachedFoodEntity>> =
        combine(_query, resultsFor) { q, (resultQ, hits) ->
            if (q.isNotBlank() && q == resultQ) hits else emptyList()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Search UI state for the full-screen search surface: distinguishes "idle" (no query), "loading"
     * (query typed, results for THIS query not back yet — so the overlay shows a loader, not a
     * misleading empty state), "results", and "empty" (query typed, this query matched nothing).
     * Fully derived from [query] + [resultsFor]: when the latest resolved results are not for the
     * current query, the search is still in flight → Loading.
     */
    val searchState: StateFlow<SearchState> =
        combine(_query, resultsFor) { q, (resultQ, hits) ->
            when {
                q.isBlank() -> SearchState.Idle
                q != resultQ -> SearchState.Loading       // results haven't caught up to this query
                hits.isNotEmpty() -> SearchState.Results(hits)
                else -> SearchState.Empty
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SearchState.Idle,
        )

    fun onQueryChange(value: String) { _query.value = value }

    fun clearSearch() { _query.value = "" }

    /** Log a catalog food scaled to [quantity]/[unit] under [meal]. */
    fun addFromFood(meal: MealCategory, foodId: String, quantity: Double, unit: String) {
        viewModelScope.launch {
            nutritionRepository.addFromFood(today, meal, foodId, quantity, unit)
            clearSearch()
        }
    }

    /** Log a bare quick-add (name + calories, optional macros) under [meal]. */
    fun addQuickAdd(
        meal: MealCategory,
        foodName: String,
        quantity: Double,
        unit: String,
        calories: Double,
        proteinG: Double? = null,
        carbsG: Double? = null,
        fatG: Double? = null,
        fibreG: Double? = null,
    ) {
        viewModelScope.launch {
            nutritionRepository.addQuickAdd(
                localDate = today,
                mealCategory = meal,
                foodName = foodName,
                quantity = quantity,
                unit = unit,
                calories = calories,
                proteinG = proteinG,
                carbsG = carbsG,
                fatG = fatG,
                fibreG = fibreG,
            )
        }
    }

    /** Add one glass of water (default 250 ml). */
    fun addWater(waterMl: Double = 250.0) {
        viewModelScope.launch { nutritionRepository.addWater(today, waterMl) }
    }

    /** Soft-delete an entry (food or water) by id. */
    fun deleteEntry(id: String) {
        viewModelScope.launch { nutritionRepository.deleteEntry(id) }
    }

    /** Resolve a scanned barcode to a cached food (cache → OFF → cache); null when unresolved. */
    suspend fun resolveBarcode(barcode: String): CachedFoodEntity? =
        runCatching { nutritionRepository.resolveBarcode(barcode) }.getOrElse { e ->
            AppLogger.w(FeatureTag.NUTRITION, "resolveBarcode failed for '$barcode'", e)
            null
        }
}

/** UI state for the full-screen food-search surface — drives loader vs. empty vs. results. */
sealed interface SearchState {
    /** No query typed yet (or cleared). */
    data object Idle : SearchState
    /** A query is typed but its results are still debouncing/fetching — show a loader. */
    data object Loading : SearchState
    /** The current query matched nothing. */
    data object Empty : SearchState
    /** The current query's ranked matches. */
    data class Results(val foods: List<CachedFoodEntity>) : SearchState
}
