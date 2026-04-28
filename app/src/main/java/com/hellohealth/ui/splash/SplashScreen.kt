package com.hellohealth.ui.splash

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hellohealth.ui.auth.AuthState
import com.hellohealth.ui.auth.AuthViewModel
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    viewModel: AuthViewModel,
    onNavigateToLogin: () -> Unit,
    onNavigateToDashboard: () -> Unit
) {
    val authState by viewModel.authState.collectAsState()
    
    val primaryColor = MaterialTheme.colorScheme.primary
    val backgroundColor = MaterialTheme.colorScheme.background

    LaunchedEffect(authState) {
        if (authState is AuthState.Authenticated || authState is AuthState.Unauthenticated) {
            delay(1500L) // Show the beautiful branding for at least 1.5 seconds
            when (authState) {
                is AuthState.Authenticated -> onNavigateToDashboard()
                is AuthState.Unauthenticated -> onNavigateToLogin()
                else -> {}
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.2f),
                        backgroundColor
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // New Pulse Heart Brand Icon
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = null,
                    modifier = Modifier.size(120.dp),
                    tint = primaryColor
                )
                
                // Pulse line overlay
                Canvas(modifier = Modifier.size(72.dp)) {
                    val width = size.width
                    val height = size.height
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(0f, height / 2)
                        lineTo(width * 0.3f, height / 2)
                        lineTo(width * 0.45f, height * 0.1f)
                        lineTo(width * 0.65f, height * 0.9f)
                        lineTo(width * 0.8f, height / 2)
                        lineTo(width, height / 2)
                    }
                    drawPath(
                        path = path,
                        color = Color.White,
                        style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Beautified Split Style Title
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Light, color = primaryColor)) {
                        append("Hello ")
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
                fontSize = 42.sp,
                letterSpacing = (-1.5).sp
            )
            
            Spacer(modifier = Modifier.height(48.dp))
            
            CircularProgressIndicator(
                color = primaryColor,
                strokeWidth = 3.dp,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}
