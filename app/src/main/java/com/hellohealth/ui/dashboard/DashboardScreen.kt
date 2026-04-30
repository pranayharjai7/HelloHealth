package com.hellohealth.ui.dashboard

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.*
import androidx.compose.runtime.*
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.HealthConnectClient
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hellohealth.domain.model.DailyHealthSnapshot
import coil.compose.AsyncImage
import com.hellohealth.domain.model.User
import com.hellohealth.ui.auth.AuthViewModel
import com.hellohealth.ui.dashboard.components.WorkoutCard
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    authViewModel: AuthViewModel,
    dashboardViewModel: DashboardViewModel,
    onLogout: () -> Unit,
    onNavigateToWorkoutDetails: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToGoals: () -> Unit,
    onNavigateToActivitySettings: () -> Unit,
    onNavigateToFoodPreferences: () -> Unit,
    onNavigateToInsights: () -> Unit,
    onNavigateToHelp: () -> Unit
) {
    val context = LocalContext.current
    val uiState by dashboardViewModel.uiState.collectAsState()
    val authUser by authViewModel.currentUser.collectAsState()
    var showProfileMenu by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showCalendarSheet by remember { mutableStateOf(false) }
    
    val snackbarHostState = remember { SnackbarHostState() }
    val pullRefreshState = rememberPullToRefreshState()

    // Health Connect Permission Launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        if (granted.isNotEmpty()) {
            dashboardViewModel.checkPermissionsAndLoadData()
        }
    }

    // Auto-request Health Connect permissions
    LaunchedEffect(uiState.hasHealthPermissions, uiState.healthConnectAvailability) {
        if (!uiState.hasHealthPermissions && uiState.healthConnectAvailability == HealthConnectClient.SDK_AVAILABLE) {
            val permissions = dashboardViewModel.getHealthPermissions()
            if (permissions.isNotEmpty()) {
                permissionLauncher.launch(permissions)
            }
        }
    }
    
    if (pullRefreshState.isRefreshing) {
        LaunchedEffect(true) {
            dashboardViewModel.refreshSelectedDate()
        }
    }
    
    LaunchedEffect(uiState.isLoading) {
        if (!uiState.isLoading && pullRefreshState.isRefreshing) {
            pullRefreshState.endRefresh()
        }
    }
    
    val primaryColor = MaterialTheme.colorScheme.primary
    val backgroundColor = MaterialTheme.colorScheme.background
    val selectedDateLabel = remember(uiState.selectedDate) {
        if (uiState.selectedDate == LocalDate.now()) {
            "Today"
        } else {
            uiState.selectedDate.format(DateTimeFormatter.ofPattern("MMM d"))
        }
    }

    LaunchedEffect(uiState.error) {
        if (uiState.error != null) {
            snackbarHostState.showSnackbar(uiState.error ?: "Sync failed")
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = {
                    Surface(
                        onClick = { showCalendarSheet = true },
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 3.dp,
                        border = BorderStroke(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                        ),
                        modifier = Modifier.padding(start = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = "Open calendar",
                                tint = primaryColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = selectedDateLabel,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                },
                title = {
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(fontWeight = FontWeight.Light, color = primaryColor)) {
                                append("Hello")
                            }
                            withStyle(
                                SpanStyle(
                                    fontWeight = FontWeight.Black,
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(primaryColor, Color(0xFF1B5E20))
                                    )
                                )
                            ) {
                                append("Health")
                            }
                        },
                        fontSize = 24.sp,
                        letterSpacing = (-0.5).sp,
                        maxLines = 1
                    )
                },
                actions = {
                    Box(modifier = Modifier.padding(end = 12.dp)) {
                        ProfileAvatar(
                            user = authUser,
                            onClick = { showProfileMenu = true },
                            size = 36.dp
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                .nestedScroll(pullRefreshState.nestedScrollConnection)
        ) {
            if (uiState.isLoading && !pullRefreshState.isRefreshing) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .align(Alignment.TopCenter),
                    color = primaryColor,
                    trackColor = Color.Transparent
                )
            }

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
                            text = authUser?.name ?: "Healthy User",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = if (uiState.selectedDate == LocalDate.now()) {
                                "Viewing today"
                            } else {
                                "Viewing ${uiState.selectedDate.format(DateTimeFormatter.ofPattern("EEE, MMM d"))}"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Activity Overview
                WorkoutCard(
                    summary = uiState.healthSummary,
                    selectedDate = uiState.selectedDate,
                    hasPermissions = uiState.hasHealthPermissions,
                    healthConnectAvailability = uiState.healthConnectAvailability,
                    isSyncing = uiState.isLoading,
                    lastSyncTime = uiState.lastSyncTime,
                    error = uiState.error,
                    onPermissionRequest = {
                        val permissions = dashboardViewModel.getHealthPermissions()
                        if (permissions.isNotEmpty()) {
                            permissionLauncher.launch(permissions)
                        }
                    },
                    onOpenSettings = { dashboardViewModel.openHealthConnectSettings(context) },
                    onSync = { dashboardViewModel.refreshSelectedDate() },
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
            
            if (pullRefreshState.isRefreshing || pullRefreshState.progress > 0f) {
                PullToRefreshContainer(
                    state = pullRefreshState,
                    modifier = Modifier.align(Alignment.TopCenter),
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = primaryColor
                )
            }
        }

        if (showProfileMenu) {
            ProfileBottomSheet(
                user = authUser,
                onDismiss = { showProfileMenu = false },
                onOptionClick = { option ->
                    showProfileMenu = false
                    when (option) {
                        "Profile" -> onNavigateToProfile()
                        "Daily Goals" -> onNavigateToGoals()
                        "Activity Settings" -> onNavigateToActivitySettings()
                        "Food Preferences" -> onNavigateToFoodPreferences()
                        "Insights" -> onNavigateToInsights()
                        "Help & Support" -> onNavigateToHelp()
                        "Sign Out" -> showLogoutDialog = true
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

        if (showCalendarSheet) {
            ModalBottomSheet(
                onDismissRequest = { showCalendarSheet = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            ) {
                HistoryCalendarPickerContent(
                    visibleMonth = uiState.visibleMonth,
                    selectedDate = uiState.selectedDate,
                    monthSnapshots = uiState.monthSnapshots,
                    isLoading = uiState.isCalendarLoading,
                    onPreviousMonth = { dashboardViewModel.changeMonth(-1) },
                    onNextMonth = { dashboardViewModel.changeMonth(1) },
                    onSelectDate = { date ->
                        dashboardViewModel.selectDate(date)
                        showCalendarSheet = false
                    },
                    onJumpToToday = {
                        dashboardViewModel.jumpToToday()
                        showCalendarSheet = false
                    }
                )
            }
        }
    }
}

@Composable
private fun HistoryCalendarPickerContent(
    visibleMonth: YearMonth,
    selectedDate: LocalDate,
    monthSnapshots: Map<LocalDate, DailyHealthSnapshot>,
    isLoading: Boolean,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onJumpToToday: () -> Unit
) {
    val today = LocalDate.now()
    val monthLabelFormatter = remember { DateTimeFormatter.ofPattern("MMMM yyyy") }
    val monthDates = remember(visibleMonth) { buildMonthCells(visibleMonth) }
    val weekDayLabels = remember {
        listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
    }
    val intensityColors = listOf(
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
        MaterialTheme.colorScheme.primary.copy(alpha = 0.32f),
        MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
        MaterialTheme.colorScheme.primary.copy(alpha = 0.78f)
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Choose date",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "Tap any day to switch the dashboard and Health Stats screen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            AssistChip(
                onClick = onJumpToToday,
                label = { Text("Today") }
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPreviousMonth) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
            }
            Text(
                text = visibleMonth.format(monthLabelFormatter),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            IconButton(
                onClick = onNextMonth,
                enabled = visibleMonth.isBefore(YearMonth.from(today))
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            weekDayLabels.forEach { weekday ->
                Text(
                    text = weekday.name.take(3),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        monthDates.chunked(7).forEach { week ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                week.forEach { date ->
                    if (date == null) {
                        Spacer(modifier = Modifier.weight(1f))
                    } else {
                        val snapshot = monthSnapshots[date]
                        val isFuture = date.isAfter(today)
                        val completionLevel = snapshot?.completionLevel() ?: 0
                        val backgroundColor = intensityColors[completionLevel]

                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f),
                            onClick = { if (!isFuture) onSelectDate(date) },
                            shape = RoundedCornerShape(16.dp),
                            color = backgroundColor,
                            border = if (date == selectedDate) {
                                BorderStroke(
                                    width = 2.dp,
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.primary,
                                            MaterialTheme.colorScheme.secondary
                                        )
                                    )
                                )
                            } else {
                                null
                            },
                            enabled = !isFuture
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = date.dayOfMonth.toString(),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (date == selectedDate) FontWeight.Black else FontWeight.Medium,
                                    color = if (isFuture) {
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (snapshot != null) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                Color.Transparent
                                            }
                                        )
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (isLoading) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Less",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                intensityColors.drop(1).forEach { color ->
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(color)
                    )
                }
                Text(
                    text = "More",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
    }
}

private fun buildMonthCells(month: YearMonth): List<LocalDate?> {
    val firstDay = month.atDay(1)
    val leadingEmptyCells = firstDay.dayOfWeek.value - 1
    val cells = MutableList<LocalDate?>(leadingEmptyCells) { null }

    repeat(month.lengthOfMonth()) { index ->
        cells.add(month.atDay(index + 1))
    }

    while (cells.size % 7 != 0) {
        cells.add(null)
    }

    return cells
}

@Composable
fun ProfileAvatar(
    user: User?,
    onClick: () -> Unit,
    size: androidx.compose.ui.unit.Dp = 40.dp
) {
    Box(
        modifier = Modifier
            .size(size)
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

            HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))

            // Menu Options
            Column(modifier = Modifier.padding(16.dp)) {
                ProfileMenuItem(Icons.Default.Person, "Profile", onOptionClick)
                ProfileMenuItem(Icons.Default.Flag, "Daily Goals", onOptionClick)
                ProfileMenuItem(Icons.Default.Restaurant, "Food Preferences", onOptionClick)
                ProfileMenuItem(Icons.Default.Analytics, "Insights", onOptionClick)
                ProfileMenuItem(Icons.Default.Settings, "Activity Settings", onOptionClick)
                ProfileMenuItem(Icons.AutoMirrored.Filled.Help, "Help & Support", onOptionClick)
                Spacer(modifier = Modifier.height(16.dp))
                ProfileMenuItem(
                    Icons.AutoMirrored.Filled.Logout,
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
