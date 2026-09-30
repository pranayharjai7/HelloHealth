package com.hellohealth.ui.nutrition

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.concurrent.futures.await
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.hellohealth.core.logging.AppLogger
import com.hellohealth.core.logging.FeatureTag
import com.hellohealth.data.food.BarcodeAnalyzer
import java.util.concurrent.Executors

/**
 * Back-camera barcode scan screen (Nutrition). CameraX preview + an [ImageAnalysis] pipeline running
 * [BarcodeAnalyzer]; the first EAN/UPC hit fires [onBarcodeScanned] exactly once and the caller pops
 * this screen to resolve the code. A "Enter manually" escape hatch is ALWAYS available (top-bar +
 * permission fallback) so the feature works when the camera is denied or absent — nothing crashes.
 *
 * Reuses the camera-permission idiom from [com.hellohealth.ui.emotioncapture.EmotionCaptureScreen]:
 * a `remember`ed permission flag re-checked on resume (covers the Settings round-trip), a
 * RequestPermission launcher, and [openAppSettings] on a hard denial.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarcodeScanScreen(
    onBack: () -> Unit,
    onBarcodeScanned: (String) -> Unit,
    onEnterManually: () -> Unit,
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val backgroundColor = MaterialTheme.colorScheme.background

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var permissionRequested by remember { mutableStateOf(false) }
    // Latch so a burst of frames delivering the same code only pops the screen once.
    var handled by remember { mutableStateOf(false) }

    // Re-check on resume so a grant made in Settings (after an in-app denial) takes effect on return.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasCameraPermission = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        permissionRequested = true
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Scan a barcode", fontWeight = FontWeight.Black) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onEnterManually) {
                        Icon(
                            Icons.Default.Keyboard,
                            contentDescription = "Enter manually",
                            tint = primaryColor
                        )
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
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Point the camera at a product barcode — we'll look it up automatically.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )

                if (hasCameraPermission) {
                    BarcodeCameraView(
                        onBarcode = { code ->
                            if (!handled) {
                                handled = true
                                onBarcodeScanned(code)
                            }
                        }
                    )
                    OutlinedButton(onClick = onEnterManually) {
                        Icon(Icons.Default.Keyboard, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text("Enter manually instead")
                    }
                } else {
                    PermissionFallback(
                        permissionRequested = permissionRequested,
                        primaryColor = primaryColor,
                        onRequestPermission = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                        onOpenSettings = { openAppSettings(context) },
                        onEnterManually = onEnterManually
                    )
                }
            }
        }
    }
}

@Composable
private fun BarcodeCameraView(
    onBarcode: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    // Single background thread for ML Kit analysis so detection never runs on the UI thread.
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(analysisExecutor) { onDispose { analysisExecutor.shutdown() } }
    // Hold the bound provider so the unbind on dispose can reach it (bind happens in LaunchedEffect).
    val providerHolder = remember { arrayOfNulls<ProcessCameraProvider>(1) }

    // Bind preview + analysis to the back camera once. The analyzer latches internally after the
    // first hit; the whole graph unbinds when this composable leaves the composition.
    LaunchedEffect(previewView) {
        try {
            val provider = ProcessCameraProvider.getInstance(context).await()
            providerHolder[0] = provider
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(analysisExecutor, BarcodeAnalyzer(onBarcode)) }
            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                analysis
            )
        } catch (t: Throwable) {
            AppLogger.w(FeatureTag.NUTRITION, "barcode camera bind failed: ${t.message}")
        }
    }
    DisposableEffect(Unit) {
        onDispose { providerHolder[0]?.unbindAll() }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(3f / 4f),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun PermissionFallback(
    permissionRequested: Boolean,
    primaryColor: Color,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onEnterManually: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(3f / 4f)
            .background(
                color = primaryColor.copy(alpha = 0.08f),
                shape = RoundedCornerShape(24.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                text = if (permissionRequested) {
                    "Camera access is off. Enable it in Settings, or enter the barcode by hand."
                } else {
                    "Grant camera access to scan a barcode, or enter it by hand."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            if (permissionRequested) {
                Button(onClick = onOpenSettings) { Text("Open Settings") }
            } else {
                Button(onClick = onRequestPermission) { Text("Enable camera") }
            }
            OutlinedButton(onClick = onEnterManually) {
                Icon(Icons.Default.Keyboard, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("Enter manually")
            }
        }
    }
}

private fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}
