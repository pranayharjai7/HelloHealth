package com.hellohealth.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.navigation.NavType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.hellohealth.ui.activitydetail.ActivityDetailScreen
import com.hellohealth.ui.activitydetail.ActivityDetailViewModel
import com.hellohealth.ui.activitysettings.ActivitySettingsScreen
import com.hellohealth.ui.activitysettings.ActivitySettingsViewModel
import com.hellohealth.ui.auth.AuthViewModel
import com.hellohealth.ui.auth.LoginScreen
import com.hellohealth.ui.dashboard.DashboardScreen
import com.hellohealth.ui.dashboard.DashboardViewModel
import com.hellohealth.ui.dashboard.EmotionsViewModel


import com.hellohealth.ui.emotioncapture.EmotionCaptureScreen
import com.hellohealth.ui.emotioncapture.EmotionCaptureViewModel
import com.hellohealth.ui.emotioninsights.EmotionInsightsScreen
import com.hellohealth.ui.emotioninsights.EmotionInsightsViewModel
import com.hellohealth.ui.moodtimeline.MoodTimelineScreen
import com.hellohealth.ui.moodtimeline.MoodTimelineViewModel
import com.hellohealth.ui.debug.SyncDebugScreen
import com.hellohealth.ui.preferences.PreferencesScreen
import com.hellohealth.ui.preferences.PreferencesViewModel
import com.hellohealth.ui.goals.GoalsScreen
import com.hellohealth.ui.goals.GoalsViewModel
import com.hellohealth.ui.logemotion.LogEmotionScreen
import com.hellohealth.ui.logemotion.LogEmotionViewModel
import com.hellohealth.ui.help.HelpScreen
import com.hellohealth.ui.insights.InsightsScreen
import com.hellohealth.ui.insights.InsightsViewModel
import com.hellohealth.ui.onboarding.OnboardingScreen
import com.hellohealth.ui.profile.EditProfileScreen
import com.hellohealth.ui.profile.ProfileScreen
import com.hellohealth.ui.splash.SplashScreen
import com.hellohealth.ui.workoutplan.DayDetailScreen
import com.hellohealth.ui.workoutplan.DayDetailViewModel
import com.hellohealth.ui.workoutplan.ExerciseDetailScreen
import com.hellohealth.ui.workoutplan.ExerciseDetailViewModel
import com.hellohealth.ui.workoutplan.ExercisePickerScreen
import com.hellohealth.ui.workoutplan.ExercisePickerViewModel
import com.hellohealth.ui.workoutplan.RoutineDetailScreen
import com.hellohealth.ui.workoutplan.RoutineDetailViewModel
import com.hellohealth.ui.workoutplan.RoutinesScreen
import com.hellohealth.ui.workoutplan.RoutinesViewModel
import com.hellohealth.ui.workoutsession.ActiveWorkoutScreen
import com.hellohealth.ui.workoutsession.ActiveWorkoutViewModel
import com.hellohealth.ui.wellness.WellnessScreen
import com.hellohealth.ui.wellness.WellnessViewModel

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController(),
    authViewModel: AuthViewModel = hiltViewModel()
) {
    // Wrap the whole NavHost so the dashboard Activity card and the Health screen's Activity section
    // can morph into one another (Stage 6 shared element). The scope is published via a
    // CompositionLocal; each destination publishes its own AnimatedVisibilityScope below.
    SharedTransitionLayout {
        CompositionLocalProvider(LocalSharedTransitionScope provides this) {
            NavHost(
                navController = navController,
                startDestination = Screen.Splash.route
            ) {
        composable(
            route = Screen.Splash.route,
            exitTransition = { fadeOut(tween(500)) }
        ) {
            SplashScreen(
                viewModel = authViewModel,
                onNavigateToLogin = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                },
                onNavigateToDashboard = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                },
                onNavigateToOnboarding = {
                    navController.navigate(Screen.Onboarding.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Screen.Login.route,
            enterTransition = { fadeIn(tween(300)) },
            exitTransition = { fadeOut(tween(300)) }
        ) {
            LoginScreen(
                viewModel = authViewModel,
                onLoginSuccess = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onNeedsOnboarding = {
                    navController.navigate(Screen.Onboarding.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }
        
        composable(
            route = Screen.Dashboard.route,
            enterTransition = { fadeIn(tween(400)) + slideInHorizontally(tween(400)) { it } },
            exitTransition = { fadeOut(tween(300)) + slideOutHorizontally(tween(300)) { -it } },
            popEnterTransition = { fadeIn(tween(400)) + slideInHorizontally(tween(400)) { -it } }
        ) {
            val dashboardViewModel: DashboardViewModel = hiltViewModel()
            val emotionsViewModel: EmotionsViewModel = hiltViewModel()
            CompositionLocalProvider(LocalActivityAnimatedVisibilityScope provides this@composable) {
            DashboardScreen(
                authViewModel = authViewModel,
                dashboardViewModel = dashboardViewModel,
                emotionsViewModel = emotionsViewModel,
                onLogout = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Dashboard.route) { inclusive = true }
                    }
                },
                onNavigateToHealth = {
                    navController.navigate(Screen.Health.route)
                },
                onNavigateToWorkoutPlan = {
                    navController.navigate(Screen.Routines.route)
                },
                onResumeWorkout = {
                    navController.navigate(Screen.ActiveWorkout.createRoute())
                },
                onNavigateToProfile = { navController.navigate(Screen.Profile.route) },
                onNavigateToGoals = { navController.navigate(Screen.Goals.route) },
                onNavigateToActivitySettings = { navController.navigate(Screen.ActivitySettings.route) },
                onNavigateToFoodPreferences = { navController.navigate(Screen.Preferences.route) },
                onNavigateToHelp = { navController.navigate(Screen.Help.route) },
                onNavigateToLogEmotion = { navController.navigate(Screen.LogEmotion.route) },
                onNavigateToEmotionCapture = { navController.navigate(Screen.EmotionCapture.route) },
                onNavigateToEmotionGallery = {
                    navController.navigate(Screen.EmotionCapture.createRoute(startInGallery = true))
                },
                onNavigateToMoodTimeline = { navController.navigate(Screen.MoodTimeline.route) },
                onNavigateToNutrition = { navController.navigate(Screen.Nutrition.route) },
                onNavigateToCoaching = { navController.navigate(Screen.Coaching.route) },
                onNavigateToWellness = { navController.navigate(Screen.Wellness.route) }
            )
            }
        }
        
        composable(
            route = Screen.Health.route,
            enterTransition = {
                fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 2 }
            },
            exitTransition = {
                fadeOut(tween(300)) + slideOutVertically(tween(300)) { it / 2 }
            }
        ) {
            val healthViewModel: com.hellohealth.ui.health.HealthViewModel = hiltViewModel()
            CompositionLocalProvider(LocalActivityAnimatedVisibilityScope provides this@composable) {
            com.hellohealth.ui.health.HealthScreen(
                viewModel = healthViewModel,
                onBack = { navController.popBackStack() },
                onOpenActivityDetail = { session ->
                    if (session.id.isNotBlank()) {
                        navController.navigate(
                            Screen.ActivityDetail.createRoute(
                                activityId = session.id,
                                sessionStart = session.startTime.toEpochMilli(),
                                sessionEnd = session.endTime.toEpochMilli()
                            )
                        )
                    }
                },
                onOpenVitalsTrends = { navController.navigate(Screen.VitalsTrends.route) },
                onOpenBodyTrends = { navController.navigate(Screen.BodyTrends.route) },
                onOpenInsights = { navController.navigate(Screen.Insights.route) },
            )
            }
        }

        composable(
            route = Screen.ActivityDetail.route,
            arguments = listOf(
                navArgument(Screen.ActivityDetail.activityIdArg) { type = NavType.StringType },
                navArgument(Screen.ActivityDetail.sessionStartArg) {
                    type = NavType.LongType
                    defaultValue = -1L
                },
                navArgument(Screen.ActivityDetail.sessionEndArg) {
                    type = NavType.LongType
                    defaultValue = -1L
                }
            ),
            enterTransition = {
                fadeIn(tween(350)) + slideInHorizontally(tween(350)) { it / 3 }
            },
            exitTransition = {
                fadeOut(tween(250)) + slideOutHorizontally(tween(250)) { it / 3 }
            }
        ) {
            val viewModel: ActivityDetailViewModel = hiltViewModel()
            ActivityDetailScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.Profile.route,
            enterTransition = { fadeIn(tween(300)) + slideInHorizontally(tween(350)) { it } },
            exitTransition = { fadeOut(tween(300)) + slideOutHorizontally(tween(350)) { -it } },
            popEnterTransition = { fadeIn(tween(300)) + slideInHorizontally(tween(350)) { -it } },
            popExitTransition = { fadeOut(tween(300)) + slideOutHorizontally(tween(350)) { it } }
        ) {
            ProfileScreen(
                viewModel = authViewModel,
                onBack = { navController.popBackStack() },
                onOpenSyncDebug = { navController.navigate(Screen.SyncDebug.route) },
                onEditProfile = { navController.navigate(Screen.EditProfile.route) }
            )
        }

        composable(
            route = Screen.EditProfile.route,
            enterTransition = { fadeIn(tween(300)) + slideInHorizontally(tween(350)) { it } },
            exitTransition = { fadeOut(tween(300)) + slideOutHorizontally(tween(350)) { -it } },
            popEnterTransition = { fadeIn(tween(300)) + slideInHorizontally(tween(350)) { -it } },
            popExitTransition = { fadeOut(tween(300)) + slideOutHorizontally(tween(350)) { it } }
        ) {
            EditProfileScreen(
                viewModel = authViewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.SyncDebug.route) {
            SyncDebugScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = Screen.Goals.route,
            enterTransition = { fadeIn(tween(300)) + slideInHorizontally(tween(350)) { it } },
            exitTransition = { fadeOut(tween(300)) + slideOutHorizontally(tween(350)) { -it } },
            popEnterTransition = { fadeIn(tween(300)) + slideInHorizontally(tween(350)) { -it } },
            popExitTransition = { fadeOut(tween(300)) + slideOutHorizontally(tween(350)) { it } }
        ) {
            val viewModel: GoalsViewModel = hiltViewModel()
            GoalsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }

        composable(
            route = Screen.LogEmotion.route,
            enterTransition = { fadeIn(tween(300)) + slideInHorizontally(tween(350)) { it } },
            exitTransition = { fadeOut(tween(300)) + slideOutHorizontally(tween(350)) { -it } },
            popEnterTransition = { fadeIn(tween(300)) + slideInHorizontally(tween(350)) { -it } },
            popExitTransition = { fadeOut(tween(300)) + slideOutHorizontally(tween(350)) { it } }
        ) {
            val viewModel: LogEmotionViewModel = hiltViewModel()
            LogEmotionScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.EmotionCapture.route,
            arguments = listOf(
                navArgument(Screen.EmotionCapture.startInGalleryArg) {
                    type = NavType.BoolType
                    defaultValue = false
                }
            ),
            enterTransition = { fadeIn(tween(300)) + slideInHorizontally(tween(350)) { it } },
            exitTransition = { fadeOut(tween(300)) + slideOutHorizontally(tween(350)) { -it } },
            popEnterTransition = { fadeIn(tween(300)) + slideInHorizontally(tween(350)) { -it } },
            popExitTransition = { fadeOut(tween(300)) + slideOutHorizontally(tween(350)) { it } }
        ) { backStackEntry ->
            val startInGallery = backStackEntry.arguments
                ?.getBoolean(Screen.EmotionCapture.startInGalleryArg) ?: false
            val viewModel: EmotionCaptureViewModel = hiltViewModel()
            EmotionCaptureScreen(
                viewModel = viewModel,
                startInGallery = startInGallery,
                onBack = { navController.popBackStack() },
                onLogManually = {
                    // Replace the scanner on the back stack so "back" from manual logging returns
                    // to the dashboard, not to the (dismissed) scanner.
                    navController.navigate(Screen.LogEmotion.route) {
                        popUpTo(Screen.EmotionCapture.route) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Screen.EmotionInsights.route,            enterTransition = { fadeIn(tween(300)) + slideInHorizontally(tween(350)) { it } },
            exitTransition = { fadeOut(tween(300)) + slideOutHorizontally(tween(350)) { -it } },
            popEnterTransition = { fadeIn(tween(300)) + slideInHorizontally(tween(350)) { -it } },
            popExitTransition = { fadeOut(tween(300)) + slideOutHorizontally(tween(350)) { it } }
        ) {
            val viewModel: EmotionInsightsViewModel = hiltViewModel()
            EmotionInsightsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }

        composable(
            route = Screen.MoodTimeline.route,
            enterTransition = { fadeIn(tween(300)) + slideInHorizontally(tween(350)) { it } },
            exitTransition = { fadeOut(tween(300)) + slideOutHorizontally(tween(350)) { -it } },
            popEnterTransition = { fadeIn(tween(300)) + slideInHorizontally(tween(350)) { -it } },
            popExitTransition = { fadeOut(tween(300)) + slideOutHorizontally(tween(350)) { it } }
        ) {
            val viewModel: MoodTimelineViewModel = hiltViewModel()
            MoodTimelineScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenInsights = { navController.navigate(Screen.EmotionInsights.route) }
            )
        }

        composable(
            route = Screen.VitalsTrends.route,
            enterTransition = { fadeIn(tween(300)) + slideInHorizontally(tween(350)) { it } },
            exitTransition = { fadeOut(tween(300)) + slideOutHorizontally(tween(350)) { -it } },
            popEnterTransition = { fadeIn(tween(300)) + slideInHorizontally(tween(350)) { -it } },
            popExitTransition = { fadeOut(tween(300)) + slideOutHorizontally(tween(350)) { it } }
        ) {
            val viewModel: com.hellohealth.ui.vitals.VitalsTrendsViewModel = hiltViewModel()
            com.hellohealth.ui.vitals.VitalsTrendsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.BodyTrends.route,
            enterTransition = { fadeIn(tween(300)) + slideInHorizontally(tween(350)) { it } },
            exitTransition = { fadeOut(tween(300)) + slideOutHorizontally(tween(350)) { -it } },
            popEnterTransition = { fadeIn(tween(300)) + slideInHorizontally(tween(350)) { -it } },
            popExitTransition = { fadeOut(tween(300)) + slideOutHorizontally(tween(350)) { it } }
        ) {
            val viewModel: com.hellohealth.ui.body.BodyTrendsViewModel = hiltViewModel()
            com.hellohealth.ui.body.BodyTrendsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.Nutrition.route,
            enterTransition = { fadeIn(tween(300)) + slideInHorizontally(tween(350)) { it } },
            exitTransition = { fadeOut(tween(300)) + slideOutHorizontally(tween(350)) { -it } },
            popEnterTransition = { fadeIn(tween(300)) + slideInHorizontally(tween(350)) { -it } },
            popExitTransition = { fadeOut(tween(300)) + slideOutHorizontally(tween(350)) { it } }
        ) { backStackEntry ->
            val viewModel: com.hellohealth.ui.nutrition.NutritionScreenViewModel = hiltViewModel()
            // The barcode scanner returns the scanned code by setting it on THIS entry's saved state
            // before popping back (the standard Compose Navigation result idiom). Observe it as state.
            val scannedBarcode by backStackEntry.savedStateHandle
                .getStateFlow<String?>("scanned_barcode", null)
                .collectAsState()
            com.hellohealth.ui.nutrition.NutritionScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onScanBarcode = { navController.navigate(Screen.BarcodeScan.route) },
                scannedBarcode = scannedBarcode,
                onScannedBarcodeConsumed = {
                    backStackEntry.savedStateHandle["scanned_barcode"] = null
                },
            )
        }

        composable(
            route = Screen.BarcodeScan.route,
            enterTransition = { fadeIn(tween(300)) + slideInVertically(tween(350)) { it } },
            exitTransition = { fadeOut(tween(300)) },
            popEnterTransition = { fadeIn(tween(300)) },
            popExitTransition = { fadeOut(tween(300)) + slideOutVertically(tween(350)) { it } }
        ) {
            com.hellohealth.ui.nutrition.BarcodeScanScreen(
                onBack = { navController.popBackStack() },
                onBarcodeScanned = { code ->
                    // Hand the scanned code back to the Nutrition screen, then pop.
                    navController.previousBackStackEntry
                        ?.savedStateHandle?.set("scanned_barcode", code)
                    navController.popBackStack()
                },
                onEnterManually = { navController.popBackStack() },
            )
        }

        composable(
            route = Screen.Coaching.route,
            enterTransition = { fadeIn(tween(300)) + slideInHorizontally(tween(350)) { it } },
            exitTransition = { fadeOut(tween(300)) + slideOutHorizontally(tween(350)) { -it } },
            popEnterTransition = { fadeIn(tween(300)) + slideInHorizontally(tween(350)) { -it } },
            popExitTransition = { fadeOut(tween(300)) + slideOutHorizontally(tween(350)) { it } }
        ) {
            val viewModel: com.hellohealth.ui.coaching.CoachingScreenViewModel = hiltViewModel()
            com.hellohealth.ui.coaching.CoachingScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Screen.ActivitySettings.route,
            enterTransition = { fadeIn(tween(300)) + slideInHorizontally(tween(350)) { it } },
            exitTransition = { fadeOut(tween(300)) + slideOutHorizontally(tween(350)) { -it } },
            popEnterTransition = { fadeIn(tween(300)) + slideInHorizontally(tween(350)) { -it } },
            popExitTransition = { fadeOut(tween(300)) + slideOutHorizontally(tween(350)) { it } }
        ) {
            val viewModel: ActivitySettingsViewModel = hiltViewModel()
            ActivitySettingsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }

        composable(
            route = Screen.Preferences.route,
            enterTransition = { fadeIn(tween(300)) + slideInHorizontally(tween(350)) { it } },
            exitTransition = { fadeOut(tween(300)) + slideOutHorizontally(tween(350)) { -it } },
            popEnterTransition = { fadeIn(tween(300)) + slideInHorizontally(tween(350)) { -it } },
            popExitTransition = { fadeOut(tween(300)) + slideOutHorizontally(tween(350)) { it } }
        ) {
            val viewModel: PreferencesViewModel = hiltViewModel()
            PreferencesScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }

        composable(
            route = Screen.Insights.route,
            enterTransition = { fadeIn(tween(300)) + slideInHorizontally(tween(350)) { it } },
            exitTransition = { fadeOut(tween(300)) + slideOutHorizontally(tween(350)) { -it } },
            popEnterTransition = { fadeIn(tween(300)) + slideInHorizontally(tween(350)) { -it } },
            popExitTransition = { fadeOut(tween(300)) + slideOutHorizontally(tween(350)) { it } }
        ) {
            val viewModel: InsightsViewModel = hiltViewModel()
            InsightsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }

        composable(
            route = Screen.Help.route,
            enterTransition = { fadeIn(tween(300)) + slideInHorizontally(tween(350)) { it } },
            exitTransition = { fadeOut(tween(300)) + slideOutHorizontally(tween(350)) { -it } },
            popEnterTransition = { fadeIn(tween(300)) + slideInHorizontally(tween(350)) { -it } },
            popExitTransition = { fadeOut(tween(300)) + slideOutHorizontally(tween(350)) { it } }
        ) {
            HelpScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onFinished = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }

        // ---- Workout planning flow (Routines → RoutineDetail → DayDetail → ExercisePicker/ExerciseDetail) ----

        composable(
            route = Screen.Routines.route,
            enterTransition = { fadeIn(tween(300)) + slideInHorizontally(tween(350)) { it } },
            exitTransition = { fadeOut(tween(300)) + slideOutHorizontally(tween(350)) { -it } },
            popEnterTransition = { fadeIn(tween(300)) + slideInHorizontally(tween(350)) { -it } },
            popExitTransition = { fadeOut(tween(300)) + slideOutHorizontally(tween(350)) { it } }
        ) {
            val viewModel: RoutinesViewModel = hiltViewModel()
            RoutinesScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenRoutine = { planId ->
                    navController.navigate(Screen.RoutineDetail.createRoute(planId))
                }
            )
        }

        composable(
            route = Screen.RoutineDetail.route,
            arguments = listOf(
                navArgument(Screen.RoutineDetail.planIdArg) { type = NavType.StringType }
            ),
            enterTransition = { fadeIn(tween(300)) + slideInHorizontally(tween(350)) { it } },
            exitTransition = { fadeOut(tween(300)) + slideOutHorizontally(tween(350)) { -it } },
            popEnterTransition = { fadeIn(tween(300)) + slideInHorizontally(tween(350)) { -it } },
            popExitTransition = { fadeOut(tween(300)) + slideOutHorizontally(tween(350)) { it } }
        ) {
            val viewModel: RoutineDetailViewModel = hiltViewModel()
            RoutineDetailScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenDay = { dayId ->
                    navController.navigate(Screen.DayDetail.createRoute(dayId))
                }
            )
        }

        composable(
            route = Screen.DayDetail.route,
            arguments = listOf(
                navArgument(Screen.DayDetail.dayIdArg) { type = NavType.StringType }
            ),
            enterTransition = { fadeIn(tween(300)) + slideInHorizontally(tween(350)) { it } },
            exitTransition = { fadeOut(tween(300)) + slideOutHorizontally(tween(350)) { -it } },
            popEnterTransition = { fadeIn(tween(300)) + slideInHorizontally(tween(350)) { -it } },
            popExitTransition = { fadeOut(tween(300)) + slideOutHorizontally(tween(350)) { it } }
        ) {
            val viewModel: DayDetailViewModel = hiltViewModel()
            DayDetailScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onAddExercise = { dayId ->
                    navController.navigate(Screen.ExercisePicker.createRoute(dayId))
                },
                onOpenExercise = { plannedId ->
                    navController.navigate(Screen.ExerciseDetail.createRoute(plannedId))
                },
                onStartWorkout = { dayId ->
                    navController.navigate(Screen.ActiveWorkout.createRoute(dayId))
                }
            )
        }

        composable(
            route = Screen.ExercisePicker.route,
            arguments = listOf(
                navArgument(Screen.ExercisePicker.dayIdArg) { type = NavType.StringType }
            ),
            enterTransition = { fadeIn(tween(300)) + slideInVertically(tween(400)) { it / 2 } },
            exitTransition = { fadeOut(tween(300)) },
            popExitTransition = { fadeOut(tween(250)) + slideOutVertically(tween(300)) { it / 2 } }
        ) {
            val viewModel: ExercisePickerViewModel = hiltViewModel()
            ExercisePickerScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.ExerciseDetail.route,
            arguments = listOf(
                navArgument(Screen.ExerciseDetail.plannedIdArg) { type = NavType.StringType }
            ),
            enterTransition = { fadeIn(tween(300)) + slideInHorizontally(tween(350)) { it } },
            exitTransition = { fadeOut(tween(300)) + slideOutHorizontally(tween(350)) { -it } },
            popEnterTransition = { fadeIn(tween(300)) + slideInHorizontally(tween(350)) { -it } },
            popExitTransition = { fadeOut(tween(300)) + slideOutHorizontally(tween(350)) { it } }
        ) {
            val viewModel: ExerciseDetailViewModel = hiltViewModel()
            ExerciseDetailScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.ActiveWorkout.route,
            arguments = listOf(
                navArgument(Screen.ActiveWorkout.dayIdArg) {
                    type = NavType.StringType
                    defaultValue = "none"
                }
            ),
            deepLinks = listOf(navDeepLink { uriPattern = Screen.ActiveWorkout.DEEP_LINK }),
            enterTransition = { fadeIn(tween(300)) + slideInVertically(tween(400)) { it / 2 } },
            exitTransition = { fadeOut(tween(300)) },
            popExitTransition = { fadeOut(tween(250)) + slideOutVertically(tween(300)) { it / 2 } }
        ) {
            val viewModel: ActiveWorkoutViewModel = hiltViewModel()
            ActiveWorkoutScreen(
                viewModel = viewModel,
                onFinished = { navController.popBackStack() },
            )
        }

        composable(
            route = Screen.Wellness.route,
            enterTransition = { fadeIn(tween(300)) + slideInHorizontally(tween(350)) { it } },
            exitTransition = { fadeOut(tween(300)) + slideOutHorizontally(tween(350)) { -it } },
            popEnterTransition = { fadeIn(tween(300)) + slideInHorizontally(tween(350)) { -it } },
            popExitTransition = { fadeOut(tween(300)) + slideOutHorizontally(tween(350)) { it } }
        ) {
            val viewModel: WellnessViewModel = hiltViewModel()
            WellnessScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
            }
        }
    }
}

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Login : Screen("login")
    object Dashboard : Screen("dashboard")
    object ActivityDetail : Screen("activity_detail/{activityId}?sessionStart={sessionStart}&sessionEnd={sessionEnd}") {
        const val activityIdArg = "activityId"
        const val sessionStartArg = "sessionStart"
        const val sessionEndArg = "sessionEnd"

        fun createRoute(
            activityId: String,
            sessionStart: Long,
            sessionEnd: Long
        ): String {
            return "activity_detail/$activityId?sessionStart=$sessionStart&sessionEnd=$sessionEnd"
        }
    }
    object Profile : Screen("profile")
    object EditProfile : Screen("edit_profile")
    object SyncDebug : Screen("sync_debug")
    object Goals : Screen("goals")
    object LogEmotion : Screen("log_emotion")
    object EmotionCapture : Screen("emotion_capture?startInGallery={startInGallery}") {
        const val startInGalleryArg = "startInGallery"

        /** Route with an explicit gallery-on-entry flag; defaults to false (plain camera scan). */
        fun createRoute(startInGallery: Boolean = false): String =
            "emotion_capture?startInGallery=$startInGallery"
    }
    object EmotionInsights : Screen("emotion_insights")
    object MoodTimeline : Screen("mood_timeline")
    object VitalsTrends : Screen("vitals_trends")
    object BodyTrends : Screen("body_trends")
    object Health : Screen("health")
    object Nutrition : Screen("nutrition")
    object BarcodeScan : Screen("barcode_scan")
    object Coaching : Screen("coaching")
    object ActivitySettings : Screen("activity_settings")
    object Preferences : Screen("food_preferences")
    object Insights : Screen("insights")
    object Help : Screen("help")
    object Onboarding : Screen("onboarding")

    /** The wellness score + gamification detail screen (F3). */
    object Wellness : Screen("wellness")

    // ---- Workout planning (WorkoutPlan → WorkoutDay → PlannedExercise + read-only catalog) ----

    /** The routines list — entry point from the dashboard's workout hero card. */
    object Routines : Screen("routines")

    /** A single routine's days. Carries the plan id. */
    object RoutineDetail : Screen("routine_detail/{planId}") {
        const val planIdArg = "planId"
        fun createRoute(planId: String): String = "routine_detail/$planId"
    }

    /** A single day's planned exercises. Carries the day id. */
    object DayDetail : Screen("day_detail/{dayId}") {
        const val dayIdArg = "dayId"
        fun createRoute(dayId: String): String = "day_detail/$dayId"
    }

    /** Catalog picker to add an exercise to a day. Carries the day id it adds to. */
    object ExercisePicker : Screen("exercise_picker/{dayId}") {
        const val dayIdArg = "dayId"
        fun createRoute(dayId: String): String = "exercise_picker/$dayId"
    }

    /**
     * Detail + target editor for one planned exercise. Carries the planned-exercise id (the row whose
     * targets are edited); the screen resolves its catalog exercise for the gif + instructions.
     */
    object ExerciseDetail : Screen("exercise_detail/{plannedId}") {
        const val plannedIdArg = "plannedId"
        fun createRoute(plannedId: String): String = "exercise_detail/$plannedId"
    }

    /**
     * The live workout session screen (F1). Optionally carries the planned `dayId` it was started
     * from (prefills from that day); omit for an ad-hoc workout. "none" is the no-day sentinel.
     */
    object ActiveWorkout : Screen("active_workout?dayId={dayId}") {
        const val dayIdArg = "dayId"
        fun createRoute(dayId: String? = null): String = "active_workout?dayId=${dayId ?: "none"}"

        /**
         * Deep link the ongoing-workout notification uses to re-open this screen. Shares the same
         * `dayId` arg so the existing SavedStateHandle plumbing resolves it; `none` means "re-attach
         * to the live session" (the VM re-attaches via observeActiveSession and never double-starts).
         */
        const val DEEP_LINK = "hellohealth://active_workout?dayId={dayId}"
        fun deepLinkUri(dayId: String? = null): String = "hellohealth://active_workout?dayId=${dayId ?: "none"}"
    }
}
