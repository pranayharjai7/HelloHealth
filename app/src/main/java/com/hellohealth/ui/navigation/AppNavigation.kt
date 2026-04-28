package com.hellohealth.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.hellohealth.ui.auth.AuthViewModel
import com.hellohealth.ui.auth.LoginScreen
import com.hellohealth.ui.dashboard.DashboardScreen
import com.hellohealth.ui.dashboard.DashboardViewModel
import com.hellohealth.ui.dashboard.WorkoutDetailsScreen
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
            DashboardScreen(
                authViewModel = authViewModel,
                onLogout = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Dashboard.route) { inclusive = true }
                    }
                },
                onNavigateToWorkoutDetails = {
                    navController.navigate(Screen.WorkoutDetails.route)
                }
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
        ) {
            val dashboardViewModel: DashboardViewModel = hiltViewModel()
            WorkoutDetailsScreen(
                viewModel = dashboardViewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Login : Screen("login")
    object Dashboard : Screen("dashboard")
    object WorkoutDetails : Screen("workout_details")
}
