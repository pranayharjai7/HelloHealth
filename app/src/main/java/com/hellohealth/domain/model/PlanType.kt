package com.hellohealth.domain.model

/**
 * The scheduling shape of a [WorkoutPlan] (routine). This drives which "slots" a plan's
 * [WorkoutDay]s occupy and how the UI labels them:
 *
 *  - [WEEKLY]  → one day per weekday, slot keys `MONDAY`..`SUNDAY`.
 *  - [MONTHLY] → up to 31 day-of-month slots, slot keys `D01`..`D31`.
 *  - [CUSTOM]  → free-form ordered days, slot keys `C01`..`C99`.
 *
 * Stored as the enum `name` (a stable String) on the entity, so adding a new plan type later is
 * additive and never reshapes existing rows. [fromName] decodes defensively — an unknown/legacy
 * value falls back to [WEEKLY] rather than throwing, so a corrupt or forward-version row degrades
 * gracefully instead of crashing the app.
 */
enum class PlanType(val displayName: String) {
    WEEKLY("Weekly"),
    MONTHLY("Monthly"),
    CUSTOM("Custom");

    companion object {
        /** Decode a stored `name` to a [PlanType], falling back to [WEEKLY] for unknown values. */
        fun fromName(raw: String?): PlanType =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: WEEKLY

        /** Number of CUSTOM slots we expose (`C01`..`C99`). */
        private const val CUSTOM_SLOT_COUNT = 99

        /**
         * The ordered, canonical set of slot keys a plan of [planType] can hold a [WorkoutDay] in.
         * Pure and deterministic — used both to build the "add day" picker and to sort a plan's days
         * for display. Slot keys are opaque, stable Strings stored on [WorkoutDay.slotKey]; the UI
         * maps them to human labels (e.g. `MONDAY` → "Monday", `D01` → "Day 1", `C01` → "Custom 1").
         */
        fun slotKeysFor(planType: PlanType): List<String> = when (planType) {
            WEEKLY -> listOf("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY")
            MONTHLY -> (1..31).map { "D%02d".format(it) }
            CUSTOM -> (1..CUSTOM_SLOT_COUNT).map { "C%02d".format(it) }
        }
    }
}
