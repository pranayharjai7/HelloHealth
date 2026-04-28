package com.hellohealth.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.hellohealth.ui.auth.AuthViewModel
import com.hellohealth.ui.dashboard.components.WorkoutCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    authViewModel: AuthViewModel,
    dashboardViewModel: DashboardViewModel = hiltViewModel(),
    onLogout: () -> Unit,
    onNavigateToWorkoutDetails: () -> Unit
) {
    val uiState by dashboardViewModel.uiState.collectAsState()
    val authState by authViewModel.authState.collectAsState()
    
    val context = androidx.compose.ui.platform.LocalContext.current
    val primaryColor = MaterialTheme.colorScheme.primary
    val backgroundColor = MaterialTheme.colorScheme.background

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("HelloHealth", fontWeight = FontWeight.Black) },
                actions = {
                    IconButton(onClick = { 
                        authViewModel.signOut()
                        onLogout()
                    }) {
                        Icon(Icons.Default.ExitToApp, contentDescription = "Logout", tint = MaterialTheme.colorScheme.error)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = Color.Transparent // So background gradient shows through
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            primaryColor.copy(alpha = 0.15f),
                            backgroundColor
                        )
                    )
                )
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                
                // Welcome Section
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = primaryColor
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "Welcome back,",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                        Text(
                            text = "Healthy User",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Activity Overview
                WorkoutCard(
                    summary = uiState.workoutSummary,
                    hasPermissions = uiState.hasHealthPermissions,
                    healthConnectAvailability = uiState.healthConnectAvailability,
                    isSyncing = uiState.isLoading,
                    lastSyncTime = uiState.lastSyncTime,
                    error = uiState.error,
                    onPermissionRequest = { dashboardViewModel.checkPermissionsAndLoadData() },
                    onOpenSettings = { dashboardViewModel.openHealthConnectSettings(context) },
                    onSync = { dashboardViewModel.loadWorkoutSummary() },
                    onClick = onNavigateToWorkoutDetails
                )

                Spacer(modifier = Modifier.height(32.dp))
                
                // Placeholder for other features
                Text(
                    text = "Upcoming: Nutrition & AI Coaching",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                    modifier = Modifier.padding(bottom = 32.dp)
                )
            }
        }
    }
}
