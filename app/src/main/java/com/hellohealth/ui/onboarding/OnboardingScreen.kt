package com.hellohealth.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Onboarding wizard placeholder (Step 4). The real multi-step flow (Basics / Body / Activity /
 * Confirm) lands in Steps 6-9; for now this stub only proves the route is reachable and lets the
 * user continue to the Dashboard. It carries the shared visual language — a vertical gradient
 * sourced from [ColorScheme.primary] (never a raw hex) so the P1 mood-tint drops in unchanged.
 *
 * @param onFinished invoked when the user chooses to continue; the caller pops Onboarding.
 */
@Composable
fun OnboardingScreen(
    onFinished: () -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val backgroundColor = MaterialTheme.colorScheme.background

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(primaryColor.copy(alpha = 0.1f), backgroundColor)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Welcome to Hello Health",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Let's set up your profile so your goals, calories, and insights are tuned to you.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = onFinished,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Continue", fontWeight = FontWeight.Bold)
            }
        }
    }
}
