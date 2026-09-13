package com.hellohealth.domain.model

/**
 * Display unit system. Storage is always metric (cm/kg); this only affects how the UI
 * presents and collects height/weight.
 */
enum class UnitPreference {
    METRIC,
    IMPERIAL
}
