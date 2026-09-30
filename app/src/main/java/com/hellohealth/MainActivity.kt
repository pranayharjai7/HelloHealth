package com.hellohealth

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.lifecycleScope
import com.hellohealth.data.exercise.ExerciseSeeder
import com.hellohealth.data.food.NutritionSeeder
import com.hellohealth.data.vitals.VitalsBackfiller
import com.hellohealth.ui.navigation.AppNavigation
import com.hellohealth.ui.theme.HelloHealthTheme
import com.hellohealth.ui.theme.ThemeViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /**
     * Seeds the read-only exercise catalog on every launch (idempotent, count-gated). Injected here
     * so it covers BOTH fresh installs and users migrated v8→v9 — see [ExerciseSeeder].
     */
    @Inject
    lateinit var exerciseSeeder: ExerciseSeeder

    /**
     * Seeds the daily vitals rollup from Health Connect history (P3). Idempotent, count-gated, and a
     * no-op until a user is signed in with vitals permissions granted — see [VitalsBackfiller].
     */
    @Inject
    lateinit var vitalsBackfiller: VitalsBackfiller

    /**
     * Seeds the read-only common-foods catalog on every launch (idempotent, count-gated). Same
     * fresh-install-and-migrated-user rationale as [ExerciseSeeder] — see [NutritionSeeder].
     */
    @Inject
    lateinit var nutritionSeeder: NutritionSeeder

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Make status bar transparent and match the app theme
        enableEdgeToEdge()

        // Populate the exercise catalog if empty. Fire-and-forget on the lifecycle scope: the seeder
        // is IO-dispatched and never throws, so this can't block or crash app start.
        lifecycleScope.launch { exerciseSeeder.seedIfNeeded() }

        // Backfill ~30 days of vitals rollup from Health Connect on first eligible launch. Same
        // fire-and-forget contract: IO-dispatched, count-gated, runCatching-wrapped — never blocks
        // or crashes app start, and self-skips once the rollup is populated.
        lifecycleScope.launch { vitalsBackfiller.backfillIfNeeded() }

        // Populate the common-foods catalog if empty. Same fire-and-forget contract as the exercise
        // seed: IO-dispatched, count-gated, runCatching-wrapped — never blocks or crashes app start.
        lifecycleScope.launch { nutritionSeeder.seedIfNeeded() }

        setContent {
            val themeViewModel: ThemeViewModel = hiltViewModel()
            val accent by themeViewModel.accent.collectAsState()

            HelloHealthTheme(accent = accent) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }
}
