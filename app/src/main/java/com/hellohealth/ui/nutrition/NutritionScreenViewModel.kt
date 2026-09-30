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
) : ViewModel() {

    private val today: String = LocalDate.now(ZoneId.systemDefault()).toString()

    val daySummary: StateFlow<NutritionDaySummary> =
        nutritionRepository.observeDaySummary(today).stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = NutritionDaySummary.empty(today),
        )

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /**
     * Debounced typeahead results. A blank query short-circuits to an empty list (no network); a
     * non-blank query debounces 300 ms, dedupes, then hits the repo (cache → remote → cache). The
     * repo swallows all remote failures, so this Flow never errors — worst case it is the local
     * matches. Wrapped per-query in a `flow` that logs and yields empty on the off-chance the repo
     * throws, so a search miss can never crash the screen.
     */
    val searchResults: StateFlow<List<CachedFoodEntity>> =
        _query
            .debounce(300)
            .distinctUntilChanged()
            .flatMapLatest { q ->
                flow {
                    if (q.isBlank()) {
                        emit(emptyList())
                    } else {
                        emit(runCatching { nutritionRepository.searchFoods(q) }.getOrElse { e ->
                            AppLogger.w(FeatureTag.NUTRITION, "searchFoods failed for '$q'", e)
                            emptyList()
                        })
                    }
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList(),
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
