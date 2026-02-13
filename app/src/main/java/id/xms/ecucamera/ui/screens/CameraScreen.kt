package id.xms.ecucamera.ui.screens

import android.graphics.Bitmap
import android.media.MediaActionSound
import android.view.Surface
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import id.xms.ecucamera.R
import id.xms.ecucamera.engine.core.CameraState
import id.xms.ecucamera.ui.components.BottomControlBar
import id.xms.ecucamera.ui.components.TopControlBar
import id.xms.ecucamera.ui.components.hud.HistogramGraph
import id.xms.ecucamera.ui.components.hud.ShutterEffectOverlay
import id.xms.ecucamera.ui.model.CamAspectRatio
import id.xms.ecucamera.ui.model.CameraMode
import id.xms.ecucamera.ui.model.ManualTarget
import id.xms.ecucamera.ui.screens.viewfinder.ViewfinderScreen
import id.xms.ecucamera.utils.GalleryManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun CameraScreen(
    histogramData: String,
    cameraState: CameraState,
    previewAspectRatio: Float,
    targetCropRatio: Float,
    deviceOrientation: Int,
    onSurfaceReady: (Surface) -> Unit,
    onSurfaceDestroyed: () -> Unit,
    onSurfaceChanged: (Surface) -> Unit = {},
    onTouchEvent: (android.view.MotionEvent) -> Boolean,
    onZoomChange: (Float) -> Unit,
    onFlashToggle: () -> Unit,
    onShutterClick: () -> Unit,
    onSwitchCamera: () -> Unit,
    onAspectRatioChange: (CamAspectRatio) -> Unit = {},
    onManualModeChange: (Boolean) -> Unit = {},
    onIsoChange: (Float) -> Unit = {},
    onShutterChange: (Float) -> Unit = {},
    onFocusChange: (Float) -> Unit = {},
    onCloseApp: () -> Unit = {},
    onPhotoTaken: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var currentZoom by remember { mutableFloatStateOf(1.0f) }
    var flashMode by remember { mutableIntStateOf(0) }
    var selectedMode by remember { mutableStateOf(CameraMode.PHOTO) }
    
    var isoValue by remember { mutableFloatStateOf(0.5f) }
    var shutterValue by remember { mutableFloatStateOf(0.5f) }
    var focusValue by remember { mutableFloatStateOf(0.5f) }
    
    var activeManualTarget by remember { mutableStateOf(ManualTarget.NONE) }
    
    // Gallery thumbnail state
    var latestThumbnail by remember { mutableStateOf<Bitmap?>(null) }
    
    // Shutter animation state
    var triggerShutterAnim by remember { mutableStateOf(false) }
    
    // Gallery viewer state
    var showGallery by remember { mutableStateOf(false) }
    
    // Aspect ratio state — NO switching protection needed anymore (instant!)
    var currentRatio by remember { mutableStateOf(CamAspectRatio.RATIO_4_3) }
    
    // Histogram visibility state
    var showHistogram by remember { mutableStateOf(true) }
    
    // Audio feedback setup
    val mediaActionSound = remember { MediaActionSound() }
    
    // Load shutter sound and initial thumbnail
    LaunchedEffect(Unit) {
        mediaActionSound.load(MediaActionSound.SHUTTER_CLICK)
        latestThumbnail = GalleryManager.getLastImageThumbnail(context)
    }
    
    // Cleanup audio resources
    DisposableEffect(Unit) {
        onDispose {
            mediaActionSound.release()
        }
    }
    
    // State locking to prevent flicker
    var hasError by remember { mutableStateOf(false) }
    var secondsLeft by remember { mutableIntStateOf(3) }
    
    // Lock error state once detected
    if (cameraState is CameraState.Error && !hasError) {
        hasError = true
    }
    
    val isProMode = selectedMode == CameraMode.PRO
    
    // Handle back button when gallery is open
    BackHandler(enabled = showGallery) {
        showGallery = false
        // Reload thumbnail when returning from gallery
        latestThumbnail = GalleryManager.getLastImageThumbnail(context)
    }
    
    Box(modifier = modifier.fillMaxSize()) {
        // LAYER 1 (Bottom): Camera Preview with Virtual Crop — NEVER restarts
        ViewfinderScreen(
            aspectRatio = previewAspectRatio,
            targetCropRatio = targetCropRatio,
            onSurfaceReady = onSurfaceReady,
            onSurfaceDestroyed = onSurfaceDestroyed,
            onSurfaceChanged = onSurfaceChanged,
            onTouchEvent = onTouchEvent
        )
        
        // LAYER 2: Shutter Effect Overlay (Black flash animation)
        if (!showGallery) {
            ShutterEffectOverlay(
                trigger = triggerShutterAnim,
                onFinished = { triggerShutterAnim = false }
            )
        }
        
        // LAYER 3: Grid Overlay
        if (!showGallery) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height
                
                for (i in 1..2) {
                    val x = width * i / 3
                    drawLine(
                        color = Color.White.copy(alpha = 0.3f),
                        start = Offset(x, 0f),
                        end = Offset(x, height),
                        strokeWidth = 1f
                    )
                }
                
                for (i in 1..2) {
                    val y = height * i / 3
                    drawLine(
                        color = Color.White.copy(alpha = 0.3f),
                        start = Offset(0f, y),
                        end = Offset(width, y),
                        strokeWidth = 1f
                    )
                }
            }
        }
        
        // NOTE: The old "Aspect Ratio Switching Overlay" (LAYER 3.5) is REMOVED.
        // Virtual crop is instant — no blocking overlay needed.
        
        // LAYER 4: Camera Controls (only visible when gallery is closed)
        if (!showGallery) {
            // Top controls container with display cutout padding
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.displayCutout)
                    .windowInsetsPadding(WindowInsets.statusBars)
            ) {
                TopControlBar(
                    currentRatio = currentRatio,
                    onRatioChanged = {
                        // Instant switch — no debounce needed!
                        val newRatio = currentRatio.next()
                        currentRatio = newRatio
                        onAspectRatioChange(newRatio)
                    },
                    isHistogramVisible = showHistogram,
                    onToggleHistogram = {
                        showHistogram = !showHistogram
                    },
                    flashMode = flashMode,
                    onFlashToggle = {
                        flashMode = if (flashMode == 0) 1 else 0
                        onFlashToggle()
                    },
                    onSettingsClick = { },
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
            
            // Orientation Indicator - Arrow that rotates to show device orientation
            // Positioned below TopControlBar with extra padding to avoid notch
            val rotationAngle by animateFloatAsState(
                targetValue = -deviceOrientation.toFloat(),  // Negative to counter-rotate
                animationSpec = tween(durationMillis = 300),
                label = "orientation_rotation"
            )
            
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.displayCutout)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(top = 72.dp)  // Below TopControlBar (64dp height + 8dp spacing)
                    .size(32.dp)
                    .rotate(rotationAngle)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowDropUp,
                    contentDescription = "Device orientation indicator",
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxSize()
                )
            }
            
            // Histogram positioned below TopControlBar with cutout padding
            AnimatedVisibility(
                visible = showHistogram,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .windowInsetsPadding(WindowInsets.displayCutout)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(top = 72.dp, start = 16.dp)  // Below TopControlBar
            ) {
                HistogramGraph(
                    dataCsv = histogramData,
                    modifier = Modifier
                )
            }
            
            // Bottom controls container with navigation bar padding
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                BottomControlBar(
                    currentZoom = currentZoom,
                    onZoomChange = { zoom ->
                        currentZoom = zoom
                        onZoomChange(zoom)
                    },
                    selectedMode = selectedMode,
                    onModeChange = { mode ->
                        val wasProMode = selectedMode == CameraMode.PRO
                        selectedMode = mode
                        val isNowProMode = mode == CameraMode.PRO
                        
                        if (wasProMode != isNowProMode) {
                            activeManualTarget = ManualTarget.NONE
                            onManualModeChange(isNowProMode)
                        }
                    },
                    onGalleryClick = {
                        showGallery = true
                    },
                    onShutterClick = {
                        // 1. Play shutter sound immediately
                        mediaActionSound.play(MediaActionSound.SHUTTER_CLICK)
                        
                        // 2. Trigger visual feedback (black flash)
                        triggerShutterAnim = true
                        
                        // 3. Take the picture
                        onShutterClick()
                        
                        // 4. Reload thumbnail after a short delay to allow image to be saved
                        coroutineScope.launch {
                            delay(500)
                            latestThumbnail = GalleryManager.getLastImageThumbnail(context)
                        }
                    },
                    onSwitchCamera = onSwitchCamera,
                    activeManualTarget = activeManualTarget,
                    onManualTargetChange = { target ->
                        activeManualTarget = target
                    },
                    isoDisplayValue = "${(100 + isoValue * 3100).toInt()}",
                    shutterDisplayValue = "1/${(1 + shutterValue * 999).toInt()}",
                    focusDisplayValue = "${String.format("%.1f", focusValue * 10)}m",
                    galleryThumbnail = latestThumbnail,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
            
            // Manual controls slider
            AnimatedVisibility(
                visible = isProMode && activeManualTarget != ManualTarget.NONE,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 300.dp)
                    .fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.6f),
                                    Color.Black.copy(alpha = 0.3f)
                                )
                            )
                        )
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                ) {
                    when (activeManualTarget) {
                        ManualTarget.ISO -> {
                            Slider(
                                value = isoValue,
                                onValueChange = { value ->
                                    isoValue = value
                                    onIsoChange(value)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFFFFC107),
                                    activeTrackColor = Color(0xFFFFC107),
                                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                                )
                            )
                        }
                        ManualTarget.SHUTTER -> {
                            Slider(
                                value = shutterValue,
                                onValueChange = { value ->
                                    shutterValue = value
                                    onShutterChange(value)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFFFFC107),
                                    activeTrackColor = Color(0xFFFFC107),
                                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                                )
                            )
                        }
                        ManualTarget.FOCUS -> {
                            Slider(
                                value = focusValue,
                                onValueChange = { value ->
                                    focusValue = value
                                    onFocusChange(value)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFFFFC107),
                                    activeTrackColor = Color(0xFFFFC107),
                                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                                )
                            )
                        }
                        ManualTarget.NONE -> { /* No slider */ }
                    }
                }
            }
        }
        
        // LAYER 5 (Top): Gallery Overlay - Slides in from right
        AnimatedVisibility(
            visible = showGallery,
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            GalleryScreen(
                onBack = {
                    showGallery = false
                    // Reload thumbnail when returning from gallery
                    latestThumbnail = GalleryManager.getLastImageThumbnail(context)
                }
            )
        }
        
        // LAYER 6 (Topmost): Error overlay with countdown
        if (hasError) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .zIndex(10f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Text(
                        text = stringResource(R.string.camera_error_title),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Red,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Cannot connect to the camera. The app will close in $secondsLeft seconds.",
                        fontSize = 16.sp,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                }
                
                LaunchedEffect(hasError) {
                    if (hasError) {
                        while (secondsLeft > 0) {
                            delay(1000L)
                            secondsLeft--
                        }
                        onCloseApp()
                    }
                }
            }
        }
    }
}