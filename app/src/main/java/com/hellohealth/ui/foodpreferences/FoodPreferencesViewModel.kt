package com.hellohealth.ui.foodpreferences

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hellohealth.domain.model.FoodPreferences
import com.hellohealth.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FoodPreferencesUiState(
    val preferences: FoodPreferences = FoodPreferences(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class FoodPreferencesViewModel @Inject constructor(
    private val repository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FoodPreferencesUiState())
    val uiState: StateFlow<FoodPreferencesUiState> = _uiState.asStateFlow()

    init {
        loadPreferences()
    }

    private fun loadPreferences() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            repository.getFoodPreferences().collectLatest { prefs ->
                _uiState.value = _uiState.value.copy(
                    preferences = prefs,
                    isLoading = false
                )
            }
        }
    }

    fun updateDietType(dietType: String) {
        _uiState.value = _uiState.value.copy(
            preferences = _uiState.value.preferences.copy(dietType = dietType)
        )
    }

    fun toggleAllergy(allergy: String) {
        val currentAllergies = _uiState.value.preferences.allergies.toMutableList()
        if (currentAllergies.contains(allergy)) {
            currentAllergies.remove(allergy)
        } else {
            currentAllergies.add(allergy)
        }
        _uiState.value = _uiState.value.copy(
            preferences = _uiState.value.preferences.copy(allergies = currentAllergies)
        )
    }

    fun toggleCuisine(cuisine: String) {
        val currentCuisines = _uiState.value.preferences.cuisinePreferences.toMutableList()
        if (currentCuisines.contains(cuisine)) {
            currentCuisines.remove(cuisine)
        } else {
            currentCuisines.add(cuisine)
        }
        _uiState.value = _uiState.value.copy(
            preferences = _uiState.value.preferences.copy(cuisinePreferences = currentCuisines)
        )
    }

    fun savePreferences() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true)
            try {
                repository.updateFoodPreferences(_uiState.value.preferences)
                _uiState.value = _uiState.value.copy(isSaving = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    error = e.message ?: "Failed to save preferences"
                )
            }
        }
    }
}
