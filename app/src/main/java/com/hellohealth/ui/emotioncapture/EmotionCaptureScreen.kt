package com.hellohealth.ui.emotioncapture

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import java.util.concurrent.Executors

/**
 * On-device face-scan screen (P2). Front-camera CameraX preview + still capture, with a gallery
 * fallback that needs no camera permission. A captured/picked bitmap goes to
 * [EmotionCaptureViewModel.analyze]; on success the mood is already logged (`source=camera`) so the
 * app re-tints live. Every non-success outcome renders a clean message + a "Log manually" escape
 * hatch — nothing crashes (roadmap guardrail).
 *
 * Reuses the shared transparent-Scaffold + primary gradient idiom from [com.hellohealth.ui.logemotion.LogEmotionScreen].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmotionCaptureScreen(
    viewModel: EmotionCaptureViewModel,
    onBack: () -> Unit,
    onLogManually: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
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

    // Re-check the permission whenever the screen resumes. Covers the "denied in-app → sent to
    // Settings → granted there → returned" path: the remember initializer doesn't re-run on resume,
    // so without this the UI would stay wedged on the gallery-only fallback despite the grant.
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

    // Gallery path — no permission needed (PickVisualMedia is a system picker). Decode to a
    // software bitmap so the ML pipeline can read pixels (getPixels needs a non-hardware config).
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val bitmap = decodeSoftwareBitmap(context, uri)
            if (bitmap != null) viewModel.analyze(bitmap)
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Scan your mood", fontWeight = FontWeight.Black) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        galleryLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }) {
                        Icon(
                            Icons.Default.PhotoLibrary,
                            contentDescription = "Pick from gallery",
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
                when (val state = uiState) {
                    EmotionCaptureUiState.Idle, EmotionCaptureUiState.Analyzing -> {
                        CaptureSurface(
                            hasCameraPermission = hasCameraPermission,
                            permissionRequested = permissionRequested,
                            isAnalyzing = state is EmotionCaptureUiState.Analyzing,
                            primaryColor = primaryColor,
                            onRequestPermission = {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            },
                            onOpenSettings = { openAppSettings(context) },
                            onCaptured = { viewModel.analyze(it) },
                            onPickGallery = {
                                galleryLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                        )
                    }

                    is EmotionCaptureUiState.Success -> ResultPanel(
                        emoji = state.emotion.emoji(),
                        title = state.emotion.displayLabel(),
                        subtitle = "Confidence ${(state.confidence * 100).toInt()}% · logged and applied to your theme",
                        primaryActionLabel = "Scan again",
                        onPrimaryAction = viewModel::reset,
                        secondaryActionLabel = "Done",
                        onSecondaryAction = onBack,
                        primaryColor = primaryColor
                    )

                    EmotionCaptureUiState.NoFace -> ResultPanel(
                        emoji = "🫥",
                        title = "No face detected",
                        subtitle = "Center your face in the frame with good lighting and try again.",
                        primaryActionLabel = "Try again",
                        onPrimaryAction = viewModel::reset,
                        secondaryActionLabel = "Log manually",
                        onSecondaryAction = onLogManually,
                        primaryColor = primaryColor
                    )

                    EmotionCaptureUiState.ModelUnavailable -> ResultPanel(
                        emoji = "⚙️",
                        title = "Scanner unavailable",
                        subtitle = "On-device mood detection couldn't start on this device. You can still log your mood by hand.",
                        primaryActionLabel = "Log manually",
                        onPrimaryAction = onLogManually,
                        secondaryActionLabel = "Back",
                        onSecondaryAction = onBack,
                        primaryColor = primaryColor
                    )

                    is EmotionCaptureUiState.Error -> ResultPanel(
                        emoji = "⚠️",
                        title = "Couldn't read that",
                        subtitle = state.message,
                        primaryActionLabel = "Try again",
                        onPrimaryAction = viewModel::reset,
                        secondaryActionLabel = "Log manually",
                        onSecondaryAction = onLogManually,
                        primaryColor = primaryColor
                    )
                }
            }
        }
    }
}

@Composable
private fun CaptureSurface(
    hasCameraPermission: Boolean,
    permissionRequested: Boolean,
    isAnalyzing: Boolean,
    primaryColor: Color,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onCaptured: (Bitmap) -> Unit,
    onPickGallery: () -> Unit
) {
    Text(
        text = "Point the front camera at your face and capture — the mood is read entirely on your device.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    )

    if (hasCameraPermission) {
        CameraCaptureView(
            isAnalyzing = isAnalyzing,
            primaryColor = primaryColor,
            onCaptured = onCaptured
        )
    } else {
        // Permission not (yet) granted. Offer camera access, and always offer the gallery path so
        // the feature works even if the user permanently denies the camera.
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
                        "Camera access is off. Enable it in Settings, or pick a photo from your gallery."
                    } else {
                        "Grant camera access to scan your mood, or pick a photo from your gallery."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                if (permissionRequested) {
                    Button(onClick = onOpenSettings) { Text("Open Settings") }
                } else {
                    Button(onClick = onRequestPermission) { Text("Enable camera") }
                }
                OutlinedButton(onClick = onPickGallery) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Pick from gallery")
                }
            }
        }
    }
}

@Composable
private fun CameraCaptureView(
    isAnalyzing: Boolean,
    primaryColor: Color,
    onCaptured: (Bitmap) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val imageCapture = remember { ImageCapture.Builder().build() }
    val mainExecutor = remember { ContextCompat.getMainExecutor(context) }
    // Dedicated background thread for the capture callback so the full-resolution decode +
    // rotate/mirror (imageProxyToUprightBitmap) never runs on the UI thread and janks capture.
    val captureExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(captureExecutor) {
        onDispose { captureExecutor.shutdown() }
    }
    val previewView = remember { PreviewView(context) }

    // Bind the front-camera preview + capture use case once, off the addListener path (camera-
    // lifecycle 1.3.x has no awaitInstance()), awaiting the provider from a coroutine.
    LaunchedEffect(previewView) {
        try {
            val provider = ProcessCameraProvider.getInstance(context).await()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_FRONT_CAMERA,
                preview,
                imageCapture
            )
        } catch (t: Throwable) {
            AppLogger.w(FeatureTag.EMOTION_ML, "Camera bind failed", t)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(3f / 4f),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier
                .fillMaxSize()
        )
        if (isAnalyzing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color.White)
            }
        }
    }

    Button(
        onClick = {
            imageCapture.takePicture(
                captureExecutor,
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        // Runs on captureExecutor (background): decode + rotate/mirror the full-res
                        // frame off the UI thread, then hand the result back on the main thread
                        // because onCaptured touches Compose/ViewModel state.
                        val bitmap = imageProxyToUprightBitmap(image)
                        image.close()
                        if (bitmap != null) mainExecutor.execute { onCaptured(bitmap) }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        AppLogger.w(FeatureTag.EMOTION_ML, "takePicture failed", exception)
                    }
                }
            )
        },
        enabled = !isAnalyzing,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
    ) {
        Text(if (isAnalyzing) "Analyzing…" else "Capture", fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ResultPanel(
    emoji: String,
    title: String,
    subtitle: String,
    primaryActionLabel: String,
    onPrimaryAction: () -> Unit,
    secondaryActionLabel: String,
    onSecondaryAction: () -> Unit,
    primaryColor: Color
) {
    Spacer(Modifier.height(24.dp))
    Text(text = emoji, style = MaterialTheme.typography.displayLarge)
    Text(
        text = title,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Black,
        color = MaterialTheme.colorScheme.onSurface
    )
    Text(
        text = subtitle,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
    )
    Spacer(Modifier.height(8.dp))
    Button(
        onClick = onPrimaryAction,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
    ) {
        Text(primaryActionLabel, fontWeight = FontWeight.Bold)
    }
    TextButton(onClick = onSecondaryAction) {
        Text(secondaryActionLabel, color = primaryColor)
    }
}

/**
 * Convert a captured [ImageProxy] to an upright, front-mirrored software bitmap: rotate by the
 * frame's [ImageProxy.getImageInfo] rotationDegrees so the classifier sees an upright face, then
 * horizontally flip because the front camera image is mirrored. Returns null on decode failure.
 */
private fun imageProxyToUprightBitmap(image: ImageProxy): Bitmap? {
    return try {
        val raw = image.toBitmap()
        val rotation = image.imageInfo.rotationDegrees.toFloat()
        val matrix = Matrix().apply {
            postRotate(rotation)
            postScale(-1f, 1f) // front-camera mirror
        }
        val upright = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
        // A non-identity matrix always yields a distinct bitmap, so `raw` is now dead weight —
        // recycle it instead of leaving a full-res frame for GC on every capture.
        if (upright !== raw) raw.recycle()
        upright
    } catch (t: Throwable) {
        AppLogger.w(FeatureTag.EMOTION_ML, "ImageProxy -> bitmap failed", t)
        null
    }
}

/** Decode [uri] to a software (mutable-pixel-readable) bitmap; ML pixel reads need a non-hardware config. */
private fun decodeSoftwareBitmap(context: Context, uri: Uri): Bitmap? {
    return try {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = true
        }
    } catch (t: Throwable) {
        AppLogger.w(FeatureTag.EMOTION_ML, "Gallery decode failed", t)
        null
    }
}

private fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}
