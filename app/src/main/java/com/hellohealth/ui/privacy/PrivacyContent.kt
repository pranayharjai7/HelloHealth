package com.hellohealth.ui.privacy

/**
 * Canonical privacy-policy + data-use copy for HelloHealth, kept in one place so the in-app
 * [PrivacyPolicyScreen], the Health Connect [HealthRationaleScreen], and the hostable
 * `assets/privacy/privacy-policy.html` all stay in sync. Plain data (no Compose/Android deps) so it
 * is trivially unit-testable and reusable.
 *
 * The hosted HTML at a public URL (Google Play + Health Connect both require a reachable policy URL)
 * must carry the same substance as [SECTIONS] — when this copy changes, regenerate that HTML.
 */
object PrivacyContent {

    const val APP_NAME = "Hello Health"
    const val CONTACT_EMAIL = "pranayharjai7@gmail.com"
    const val LAST_UPDATED = "November 2026"

    /** One policy section: a heading and its body paragraphs. */
    data class Section(val title: String, val paragraphs: List<String>)

    val INTRO: String =
        "This policy explains what data $APP_NAME collects, why, where it is stored, and the control " +
            "you have over it. $APP_NAME is a personal health companion: your health data stays yours. " +
            "Last updated $LAST_UPDATED."

    val SECTIONS: List<Section> = listOf(
        Section(
            "Data we collect",
            listOf(
                "Account: your email address and display name (and avatar, if you sign in with Google), " +
                    "used to authenticate you and sync your data across devices.",
                "Profile & body metrics: gender, age, height, weight, and the activity/goal preferences " +
                    "you enter during onboarding or in settings.",
                "Health & fitness data from Android Health Connect (only the types you explicitly grant): " +
                    "steps, distance, floors climbed, active/total/basal calories, heart rate, resting heart " +
                    "rate, heart-rate variability, respiratory rate, oxygen saturation, blood pressure, blood " +
                    "glucose, body temperature, VO2 max, sleep, exercise sessions, speed, elevation, hydration, " +
                    "nutrition, height, weight, and body fat.",
                "Mood & emotions: mood entries you log manually, and — only if you choose the face-scan " +
                    "feature — a camera image processed ON YOUR DEVICE to estimate an emotion. The face image " +
                    "itself is never uploaded; only the resulting mood label and score are stored.",
                "Nutrition: the foods and quantities you log, and your nutrition goals.",
                "Workouts: routines, planned exercises, and logged workout sessions.",
                "Approximate/precise location: used only while a feature that needs it is active (e.g. " +
                    "showing an exercise route on a map). Location is not tracked in the background."
            )
        ),
        Section(
            "How we use your data",
            listOf(
                "To show your dashboards, trends, insights, wellness score, and progress.",
                "To sync your data to your account so it is available on your other devices.",
                "To provide AI coaching: when you use the coach, the relevant summary of your health " +
                    "context is sent to our secure server-side proxy, which forwards it to a third-party AI " +
                    "provider (Google Gemini, with an OpenRouter fallback) to generate a response. API keys " +
                    "are held server-side and never shipped in the app.",
                "We do NOT sell your data, and we do NOT use it for advertising."
            )
        ),
        Section(
            "Where your data is stored",
            listOf(
                "Your data syncs to Supabase (our managed backend), stored encrypted in transit and at " +
                    "rest, isolated per-user by row-level security so only you can read or write your rows.",
                "A copy is cached on your device so the app works offline.",
                "The on-device face-scan model and its image processing never leave your phone."
            )
        ),
        Section(
            "AI coaching & third parties",
            listOf(
                "AI coaching requests are rate-limited and tied to your authenticated account. The text " +
                    "sent is the coaching prompt assembled from your health summary; it is processed by the " +
                    "AI provider to generate the reply and is not used by us to build an advertising profile.",
                "Mapping/route display uses Google Maps.",
                "Nutrition lookups may query public food databases (USDA FoodData Central, Open Food Facts) " +
                    "using only the search term you type — never your identity."
            )
        ),
        Section(
            "Your controls & data deletion",
            listOf(
                "You can revoke Health Connect permissions at any time from Android settings or within the " +
                    "app; $APP_NAME stops reading those types immediately.",
                "You can permanently delete your account from Profile → Delete Account. This erases all your " +
                    "records from our servers (health, workout, nutrition, mood, profile), removes your stored " +
                    "files, revokes your sessions, and deletes your login identity. The deletion is immediate " +
                    "and irreversible."
            )
        ),
        Section(
            "Contact",
            listOf(
                "Questions about this policy or your data: $CONTACT_EMAIL."
            )
        )
    )

    /**
     * The Health Connect rationale: for each permission category the app requests, a short, honest
     * reason — shown on [HealthRationaleScreen] when Health Connect launches the app to justify access.
     */
    data class RationaleItem(val category: String, val reason: String)

    val RATIONALE_INTRO: String =
        "$APP_NAME reads the following health and fitness data from Health Connect to power your " +
            "dashboards, trends, and coaching. We only read the categories you grant, you can revoke " +
            "any of them at any time, and we never share this data for advertising."

    val RATIONALE_ITEMS: List<RationaleItem> = listOf(
        RationaleItem("Activity — steps, distance, floors, speed, elevation", "To show your daily movement, activity rings, and workout routes."),
        RationaleItem("Energy — active, total & basal calories", "To estimate calories burned and balance it against nutrition."),
        RationaleItem("Heart — heart rate, resting HR, HRV", "To show cardiovascular trends and compute recovery/readiness."),
        RationaleItem("Vitals — SpO2, blood pressure, blood glucose, respiratory rate, body temperature, VO2 max", "To display your vitals and surface meaningful changes over time."),
        RationaleItem("Sleep", "To show sleep duration, staging, and how rest affects your readiness."),
        RationaleItem("Exercise sessions", "To display your workouts and route them into your training history."),
        RationaleItem("Body — height, weight, body fat", "To track body-composition trends and personalize calorie/goal math."),
        RationaleItem("Nutrition & hydration", "To combine what you eat and drink with the rest of your health picture.")
    )
}
