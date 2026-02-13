package id.xms.ecucamera.engine.core

import android.content.ContentValues
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.media.MediaRecorder
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.Surface
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class VideoCaptureManager(private val context: Context) {
    
    companion object {
        private const val TAG = "ECU_VIDEO"
        private const val VIDEO_BITRATE = 10_000_000 // 10 Mbps for 1080p
        private const val VIDEO_FRAME_RATE = 30
    }
    
    private var mediaRecorder: MediaRecorder? = null
    private var currentVideoFile: File? = null
    var isRecording: Boolean = false
        private set
    
    /**
     * Setup MediaRecorder and return its surface for camera session
     */
    fun setupMediaRecorder(
        cameraCharacteristics: CameraCharacteristics,
        deviceOrientation: Int,
        lensFacing: Int
    ): Surface {
        Log.d(TAG, "Setting up MediaRecorder")
        
        // Create MediaRecorder instance
        mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        
        val recorder = mediaRecorder ?: throw IllegalStateException("Failed to create MediaRecorder")
        
        try {
            // Create output file
            val videoFile = createVideoFile()
            currentVideoFile = videoFile
            
            // Configure MediaRecorder
            recorder.apply {
                // Set audio source first
                setAudioSource(MediaRecorder.AudioSource.CAMCORDER)
                // Then video source
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                
                // Set output format
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                
                // Set output file
                setOutputFile(videoFile.absolutePath)
                
                // Set video encoder
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setVideoEncodingBitRate(VIDEO_BITRATE)
                setVideoFrameRate(VIDEO_FRAME_RATE)
                setVideoSize(1920, 1080) // 1080p
                
                // Set audio encoder
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128_000) // 128 kbps
                setAudioSamplingRate(44100)
                
                // Calculate and set orientation hint
                val orientationHint = calculateOrientationHint(
                    cameraCharacteristics,
                    deviceOrientation,
                    lensFacing
                )
                setOrientationHint(orientationHint)
                
                Log.d(TAG, "MediaRecorder configured: 1080p@30fps, ${VIDEO_BITRATE / 1_000_000}Mbps, orientation=$orientationHint°")
                
                // Prepare the recorder
                prepare()
            }
            
            Log.d(TAG, "MediaRecorder prepared successfully")
            return recorder.surface
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup MediaRecorder", e)
            releaseMediaRecorder()
            throw e
        }
    }
    
    /**
     * Calculate the correct orientation hint for video recording
     */
    private fun calculateOrientationHint(
        characteristics: CameraCharacteristics,
        deviceOrientation: Int,
        lensFacing: Int
    ): Int {
        val sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
        
        // Convert device orientation to degrees
        val deviceDegrees = when (deviceOrientation) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
        
        // Calculate orientation hint based on lens facing
        val orientationHint = if (lensFacing == CameraCharacteristics.LENS_FACING_FRONT) {
            // Front camera: mirror the rotation
            (sensorOrientation - deviceDegrees + 360) % 360
        } else {
            // Back camera: normal rotation
            (sensorOrientation + deviceDegrees) % 360
        }
        
        Log.d(TAG, "Orientation calculation: sensor=$sensorOrientation°, device=$deviceDegrees°, hint=$orientationHint°")
        return orientationHint
    }
    
    /**
     * Create a new video file in the Movies directory
     */
    private fun createVideoFile(): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "VID_ECU_$timestamp.mp4"
        
        val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
        val ecuDir = File(moviesDir, "EcuCamera")
        
        if (!ecuDir.exists()) {
            ecuDir.mkdirs()
            Log.d(TAG, "Created directory: ${ecuDir.absolutePath}")
        }
        
        val videoFile = File(ecuDir, fileName)
        Log.d(TAG, "Video file created: ${videoFile.absolutePath}")
        
        return videoFile
    }
    
    /**
     * Start recording video
     */
    fun startRecording() {
        try {
            val recorder = mediaRecorder ?: run {
                Log.e(TAG, "Cannot start recording: MediaRecorder not initialized")
                return
            }
            
            if (isRecording) {
                Log.w(TAG, "Already recording")
                return
            }
            
            Log.d(TAG, "Starting video recording")
            recorder.start()
            isRecording = true
            Log.d(TAG, "Video recording started successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            isRecording = false
            releaseMediaRecorder()
        }
    }
    
    /**
     * Stop recording video and save to gallery
     */
    fun stopRecording(): File? {
        try {
            val recorder = mediaRecorder ?: run {
                Log.e(TAG, "Cannot stop recording: MediaRecorder not initialized")
                return null
            }
            
            if (!isRecording) {
                Log.w(TAG, "Not currently recording")
                return null
            }
            
            Log.d(TAG, "Stopping video recording")
            
            try {
                recorder.stop()
            } catch (e: RuntimeException) {
                Log.e(TAG, "Failed to stop recorder (may be too short)", e)
                currentVideoFile?.delete()
                return null
            } finally {
                isRecording = false
                releaseMediaRecorder()
            }
            
            val videoFile = currentVideoFile
            if (videoFile != null && videoFile.exists()) {
                Log.d(TAG, "Video saved: ${videoFile.absolutePath} (${videoFile.length() / 1024}KB)")
                
                // Scan the file so it appears in Gallery
                scanVideoFile(videoFile)
                
                return videoFile
            } else {
                Log.e(TAG, "Video file not found after recording")
                return null
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop recording", e)
            isRecording = false
            releaseMediaRecorder()
            return null
        }
    }
    
    /**
     * Scan video file to make it visible in Gallery
     */
    private fun scanVideoFile(file: File) {
        try {
            // Use MediaScannerConnection for older Android versions
            MediaScannerConnection.scanFile(
                context,
                arrayOf(file.absolutePath),
                arrayOf("video/mp4")
            ) { path, uri ->
                Log.d(TAG, "Video scanned: $path -> $uri")
            }
            
            // Also add to MediaStore for better compatibility
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/EcuCamera")
                    put(MediaStore.Video.Media.IS_PENDING, 0)
                }
                
                context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                Log.d(TAG, "Video added to MediaStore")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to scan video file", e)
        }
    }
    
    /**
     * Release MediaRecorder resources
     */
    private fun releaseMediaRecorder() {
        try {
            mediaRecorder?.apply {
                reset()
                release()
            }
            mediaRecorder = null
            Log.d(TAG, "MediaRecorder released")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing MediaRecorder", e)
        }
    }
    
    /**
     * Cleanup resources
     */
    fun cleanup() {
        if (isRecording) {
            stopRecording()
        }
        releaseMediaRecorder()
        currentVideoFile = null
    }
}
