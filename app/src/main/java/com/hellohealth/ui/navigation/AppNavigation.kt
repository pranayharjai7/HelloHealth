package com.hellohealth.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.navArgument
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
import com.hellohealth.ui.dashboard.WorkoutDetailsScreen
import com.hellohealth.ui.foodpreferences.FoodPreferencesScreen
import com.hellohealth.ui.foodpreferences.FoodPreferencesViewModel
import com.hellohealth.ui.goals.GoalsScreen
import com.hellohealth.ui.goals.GoalsViewModel
import com.hellohealth.ui.help.HelpScreen
import com.hellohealth.ui.insights.InsightsScreen
import com.hellohealth.ui.insights.InsightsViewModel
import com.hellohealth.ui.profile.ProfileScreen
import com.hellohealth.ui.splash.SplashScreen

@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController(),
    authViewModel: AuthViewModel = hiltViewModel()
) {
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
            DashboardScreen(
                authViewModel = authViewModel,
                dashboardViewModel = dashboardViewModel,
                onLogout = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Dashboard.route) { inclusive = true }
                    }
                },
                onNavigateToWorkoutDetails = {
                    navController.navigate(Screen.WorkoutDetails.route)
                },
                onNavigateToProfile = { navController.navigate(Screen.Profile.route) },
                onNavigateToGoals = { navController.navigate(Screen.Goals.route) },
                onNavigateToActivitySettings = { navController.navigate(Screen.ActivitySettings.route) },
                onNavigateToFoodPreferences = { navController.navigate(Screen.FoodPreferences.route) },
                onNavigateToInsights = { navController.navigate(Screen.Insights.route) },
                onNavigateToHelp = { navController.navigate(Screen.Help.route) }
            )
        }
        
        composable(
            route = Screen.WorkoutDetails.route,
            enterTransition = { 
                fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 2 } 
            },
            exitTransition = { 
                fadeOut(tween(300)) + slideOutVertically(tween(300)) { it / 2 } 
            }
        ) { backStackEntry ->
            val parentEntry = remember(backStackEntry) {
                navController.getBackStackEntry(Screen.Dashboard.route)
            }
            val dashboardViewModel: DashboardViewModel = hiltViewModel(parentEntry)
            WorkoutDetailsScreen(
                viewModel = dashboardViewModel,
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
                }
            )
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

        composable(Screen.Profile.route) {
            ProfileScreen(viewModel = authViewModel, onBack = { navController.popBackStack() })
        }

        composable(Screen.Goals.route) {
            val viewModel: GoalsViewModel = hiltViewModel()
            GoalsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }

        composable(Screen.ActivitySettings.route) {
            val viewModel: ActivitySettingsViewModel = hiltViewModel()
            ActivitySettingsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }

        composable(Screen.FoodPreferences.route) {
            val viewModel: FoodPreferencesViewModel = hiltViewModel()
            FoodPreferencesScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }

        composable(Screen.Insights.route) {
            val viewModel: InsightsViewModel = hiltViewModel()
            InsightsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }

        composable(Screen.Help.route) {
            HelpScreen(onBack = { navController.popBackStack() })
        }
    }
}

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Login : Screen("login")
    object Dashboard : Screen("dashboard")
    object WorkoutDetails : Screen("workout_details")
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
    object Goals : Screen("goals")
    object ActivitySettings : Screen("activity_settings")
    object FoodPreferences : Screen("food_preferences")
    object Insights : Screen("insights")
    object Help : Screen("help")
}
