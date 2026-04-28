package com.hellohealth.ui.auth

import android.content.Context
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.hellohealth.BuildConfig
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    viewModel: AuthViewModel,
    onLoginSuccess: () -> Unit
) {
    val authState by viewModel.authState.collectAsState()
    var isSignUp by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(authState) {
        if (authState is AuthState.Success) {
            onLoginSuccess()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Logo / Header
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Hello Health",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            androidx.compose.animation.AnimatedContent(
                targetState = isSignUp,
                label = "HeaderSubtitleAnimation"
            ) { targetIsSignUp ->
                Text(
                    text = if (targetIsSignUp) "Create an account to start your journey" else "Log in to your healthy lifestyle",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Auth Fields with Animation
            androidx.compose.animation.AnimatedContent(
                targetState = isSignUp,
                label = "FieldsAnimation"
            ) { targetIsSignUp ->
                Column {
                    // Email Field
                    ModernTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = "Email",
                        leadingIcon = Icons.Default.Email,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Password Field
                    ModernTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = "Password",
                        leadingIcon = Icons.Default.Lock,
                        isPassword = true,
                        passwordVisible = passwordVisible,
                        onPasswordToggle = { passwordVisible = !passwordVisible }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Login/Signup Button with Animation
            Button(
                onClick = {
                    if (isSignUp) viewModel.signUp(email, password)
                    else viewModel.signIn(email, password)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                if (authState is AuthState.Loading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                } else {
                    androidx.compose.animation.AnimatedContent(
                        targetState = isSignUp,
                        label = "ButtonTextAnimation"
                    ) { targetIsSignUp ->
                        Text(
                            text = if (targetIsSignUp) "Sign Up" else "Log In",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Google Sign In
            OutlinedButton(
                onClick = {
                    handleGoogleSignIn(context, viewModel, scope)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.AccountCircle, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Continue with Google")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Toggle Login/Signup
            TextButton(onClick = { isSignUp = !isSignUp }) {
                Text(
                    text = if (isSignUp) "Already have an account? Log In" else "New to HelloHealth? Sign Up",
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Error Message
            AnimatedVisibility(
                visible = authState is AuthState.Error,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                val errorMsg = (authState as? AuthState.Error)?.message ?: ""
                Text(
                    text = errorMsg,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
        }
    }
}

@Composable
fun ModernTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    leadingIcon: ImageVector,
    isPassword: Boolean = false,
    passwordVisible: Boolean = false,
    onPasswordToggle: (() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = { Icon(imageVector = leadingIcon, contentDescription = null) },
        trailingIcon = {
            if (isPassword && onPasswordToggle != null) {
                IconButton(onClick = onPasswordToggle) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = null
                    )
                }
            }
        },
        visualTransformation = if (isPassword && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = keyboardOptions,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f)
        )
    )
}

private fun handleGoogleSignIn(context: Context, viewModel: AuthViewModel, scope: kotlinx.coroutines.CoroutineScope) {
    val credentialManager = CredentialManager.create(context)
    
    Log.d("GoogleSignIn", "Starting Google Sign-In with Client ID: ${BuildConfig.GOOGLE_WEB_CLIENT_ID}")

    val googleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(false)
        .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
        .setAutoSelectEnabled(false) // Disable auto-select to force account picker
        .build()

    val request = GetCredentialRequest.Builder()
        .addCredentialOption(googleIdOption)
        .build()

    scope.launch {
        try {
            viewModel.setLoading()
            val result = credentialManager.getCredential(context, request)
            val credential = result.credential
            
            Log.d("GoogleSignIn", "Credential received: ${credential.type}")

            if (credential is androidx.credentials.CustomCredential && 
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val googleIdToken = googleIdTokenCredential.idToken
                
                if (googleIdToken.isNotEmpty()) {
                    Log.d("GoogleSignIn", "ID Token obtained successfully")
                    viewModel.signInWithGoogle(
                        idToken = googleIdToken,
                        name = googleIdTokenCredential.displayName,
                        avatarUrl = googleIdTokenCredential.profilePictureUri?.toString()
                    )
                } else {
                    Log.e("GoogleSignIn", "ID Token is empty. This can happen if the Client ID type is incorrect.")
                    viewModel.setError("Google Sign-In: ID Token is empty. Ensure you are using a 'Web application' Client ID.")
                }
            } else {
                Log.e("GoogleSignIn", "Unexpected credential type: ${credential.type}")
                viewModel.setError("Google Sign-In: Unexpected credential type.")
            }
        } catch (e: androidx.credentials.exceptions.GetCredentialCancellationException) {
            Log.w("GoogleSignIn", "Sign-in cancelled by user. Note: This also happens if the SHA-1 or Client ID is misconfigured.")
            viewModel.setError("Login failed: The request was cancelled. Please check your Google Cloud Console configuration (SHA-1 and Client ID type).")
        } catch (e: androidx.credentials.exceptions.GetCredentialException) {
            Log.e("GoogleSignIn", "GetCredentialException: ${e.message}", e)
            viewModel.setError("Login failed: ${e.message}")
        } catch (e: Exception) {
            Log.e("GoogleSignIn", "Unexpected error: ${e.localizedMessage}", e)
            viewModel.setError("Unexpected error: ${e.localizedMessage}")
        }
    }
}
