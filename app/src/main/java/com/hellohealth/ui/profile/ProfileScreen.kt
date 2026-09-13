package com.hellohealth.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.hellohealth.domain.model.UnitPreference
import com.hellohealth.domain.model.UserProfile
import com.hellohealth.ui.auth.AuthViewModel
import com.hellohealth.ui.common.cmToFeetInches
import com.hellohealth.ui.common.displayLabel
import com.hellohealth.ui.common.formatNumber
import com.hellohealth.ui.common.kgToLb
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: AuthViewModel,
    onBack: () -> Unit,
    onOpenSyncDebug: () -> Unit = {},
    onEditProfile: () -> Unit = {}
) {
    val user by viewModel.currentUser.collectAsState()
    // The onboarding vitals live on the stored UserProfile. Reuse the editor's read path
    // (profileEditorState) to display them read-only here — no new VM plumbing. The pencil action
    // in the top bar opens EditProfileScreen, which uses this same state to edit and save them.
    val editorState by viewModel.profileEditorState.collectAsState()
    LaunchedEffect(Unit) { viewModel.loadProfileForEditing() }
    val profile = editorState.profile
    val primaryColor = MaterialTheme.colorScheme.primary
    val backgroundColor = MaterialTheme.colorScheme.background

    // Hidden developer gesture: 7 quick taps on the "Profile" title opens the sync debug screen.
    // No visible affordance; the tap streak resets if taps are more than 600ms apart.
    var tapCount by remember { mutableIntStateOf(0) }
    var lastTapAt by remember { mutableLongStateOf(0L) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Profile",
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            val now = System.currentTimeMillis()
                            tapCount = if (now - lastTapAt <= 600L) tapCount + 1 else 1
                            lastTapAt = now
                            if (tapCount >= 7) {
                                tapCount = 0
                                onOpenSyncDebug()
                            }
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Edit affordance lives here (top-right) so it's discoverable without scrolling
                    // past the cards to a bottom button.
                    IconButton(onClick = onEditProfile) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Profile")
                    }
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
                        colors = listOf(primaryColor.copy(alpha = 0.1f), backgroundColor)
                    )
                )
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Avatar Section
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(primaryColor.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (user?.avatarUrl != null) {
                        AsyncImage(
                            model = user?.avatarUrl,
                            contentDescription = "Profile Picture",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        val initial = user?.name?.take(1) ?: user?.email?.take(1) ?: "H"
                        Text(
                            text = initial.uppercase(),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Black,
                            color = primaryColor
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // User Info
                Text(
                    text = user?.name ?: "Healthy User",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = user?.email ?: "",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )

                Spacer(modifier = Modifier.height(40.dp))

                // Details Card
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        ProfileInfoItem("Name", user?.name ?: "--")
                        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                        ProfileInfoItem("Email", user?.email ?: "--")
                        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                        ProfileInfoItem(
                            "Member Since",
                            user?.createdAt?.let {
                                SimpleDateFormat("MMM yyyy", Locale.getDefault()).format(Date(it))
                            } ?: "--"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Body — the onboarding body measurements, shown read-only in a 2×2 tile grid.
                // Values are formatted in the user's stored unit preference; unset fields read "--"
                // (a partial profile is valid). Editing happens via the pencil action in the top bar.
                // Goal-direction fields (activity level, goal type, target weight) intentionally live
                // on the Preferences screen, which owns them — they are NOT surfaced here.
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text(
                            text = "Body",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        // 2×2 grid of metric tiles — fills the card width instead of stacking
                        // everything on the left. Each tile splits the row evenly via weight(1f);
                        // IntrinsicSize.Min + fillMaxHeight keeps both tiles in a row the same height
                        // even when one value wraps to two lines (e.g. imperial "5 ft 11 in").
                        Row(
                            modifier = Modifier.height(IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            MetricTile(
                                label = "Gender",
                                value = profile?.gender?.displayLabel() ?: "--",
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            )
                            MetricTile(
                                label = "Age",
                                value = profile?.ageYears()?.let { "$it yr" } ?: "--",
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.height(IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            MetricTile(
                                label = "Height",
                                value = formatHeight(profile),
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            )
                            MetricTile(
                                label = "Weight",
                                value = formatWeight(profile?.weightKg, profile?.unitPreference),
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileInfoItem(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * A single body-metric tile: the value shown prominently with its label beneath, on a subtly
 * tinted rounded surface. Sized by the caller (weight(1f) in the 2×2 grid) so tiles split the
 * card width evenly.
 */
@Composable
private fun MetricTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.06f))
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurface,
            // Values like "5 ft 11 in" can exceed a half-width tile at titleLarge; allow a second
            // line rather than hard-truncating. Both tiles in a Row size independently, so a taller
            // tile just grows its own row.
            maxLines = 2
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

/** Height in the profile's stored units: metric → "cm", imperial → "ft in". "--" when unset. */
private fun formatHeight(profile: UserProfile?): String {
    val cm = profile?.heightCm ?: return "--"
    return if (profile.unitPreference == UnitPreference.IMPERIAL) {
        val (feet, inches) = cmToFeetInches(cm)
        "$feet ft ${formatNumber(inches)} in"
    } else {
        "${formatNumber(cm)} cm"
    }
}

/** Weight in the given units: metric → "kg", imperial → "lb". "--" when unset. */
private fun formatWeight(weightKg: Double?, unitPreference: UnitPreference?): String {
    val kg = weightKg ?: return "--"
    return if (unitPreference == UnitPreference.IMPERIAL) {
        "${formatNumber(kg.kgToLb())} lb"
    } else {
        "${formatNumber(kg)} kg"
    }
}
