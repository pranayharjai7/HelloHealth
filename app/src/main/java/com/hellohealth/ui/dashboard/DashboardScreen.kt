package com.hellohealth.ui.dashboard

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.hellohealth.domain.model.User
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
    var showProfileMenu by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    
    val primaryColor = MaterialTheme.colorScheme.primary
    val backgroundColor = MaterialTheme.colorScheme.background
    val context = LocalContext.current

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("HelloHealth", fontWeight = FontWeight.Black) },
                actions = {
                    ProfileAvatar(
                        user = uiState.user,
                        onClick = { showProfileMenu = true }
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = Color.Transparent
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
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(primaryColor.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.WavingHand,
                            contentDescription = null,
                            tint = primaryColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "Welcome back,",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                        Text(
                            text = uiState.user?.name ?: "Healthy User",
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
                
                Text(
                    text = "Upcoming: Nutrition & AI Coaching",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                    modifier = Modifier.padding(bottom = 32.dp)
                )
            }
        }

        if (showProfileMenu) {
            ProfileBottomSheet(
                user = uiState.user,
                onDismiss = { showProfileMenu = false },
                onOptionClick = { option ->
                    showProfileMenu = false
                    if (option == "Sign Out") {
                        showLogoutDialog = true
                    }
                }
            )
        }

        if (showLogoutDialog) {
            AlertDialog(
                onDismissRequest = { showLogoutDialog = false },
                title = { Text("Sign Out") },
                text = { Text("Are you sure you want to sign out?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showLogoutDialog = false
                            authViewModel.signOut()
                            onLogout()
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Sign Out")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showLogoutDialog = false }) {
                        Text("Cancel")
                    }
                },
                shape = RoundedCornerShape(24.dp),
                containerColor = MaterialTheme.colorScheme.surface
            )
        }
    }
}

@Composable
fun ProfileAvatar(
    user: User?,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
            .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (user?.avatarUrl != null) {
            AsyncImage(
                model = user.avatarUrl,
                contentDescription = "Profile",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            val initial = user?.name?.take(1) ?: user?.email?.take(1) ?: "H"
            Text(
                text = initial.uppercase(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileBottomSheet(
    user: User?,
    onDismiss: () -> Unit,
    onOptionClick: (String) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            // User Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ProfileAvatar(user = user, onClick = {})
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = user?.name ?: "Healthy User",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = user?.email ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            Divider(modifier = Modifier.padding(horizontal = 24.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))

            // Menu Options
            Column(modifier = Modifier.padding(16.dp)) {
                ProfileMenuItem(Icons.Default.Person, "Profile", onOptionClick)
                ProfileMenuItem(Icons.Default.Flag, "Daily Goals", onOptionClick)
                ProfileMenuItem(Icons.Default.Settings, "Activity Settings", onOptionClick)
                ProfileMenuItem(Icons.Default.Restaurant, "Food Preferences", onOptionClick)
                ProfileMenuItem(Icons.Default.Analytics, "Insights", onOptionClick)
                ProfileMenuItem(Icons.Default.Help, "Help & Support", onOptionClick)
                Spacer(modifier = Modifier.height(16.dp))
                ProfileMenuItem(
                    Icons.Default.Logout, 
                    "Sign Out", 
                    onOptionClick, 
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun ProfileMenuItem(
    icon: ImageVector,
    label: String,
    onClick: (String) -> Unit,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    Surface(
        onClick = { onClick(label) },
        color = Color.Transparent,
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = color,
                fontWeight = if (label == "Sign Out") FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}
