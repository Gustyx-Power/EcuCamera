package id.xms.ecucamera.engine.core

import android.content.Context
import android.graphics.BitmapFactory
import android.media.ImageReader
import android.net.Uri
import android.os.Handler
import android.util.Log
import id.xms.ecucamera.engine.controller.CaptureController
import id.xms.ecucamera.utils.BitmapUtils
import java.io.ByteArrayOutputStream
import kotlin.math.abs

class ImageCaptureManager(private val context: Context) {
    
    companion object {
        private const val TAG = "ImageCaptureManager"
    }
    
    private val captureController = CaptureController(context)
    
    fun setupJpegListener(
        jpegReader: ImageReader,
        backgroundHandler: Handler,
        targetCropRatio: () -> Float,
        deviceOrientationDegrees: () -> Int,
        sensorOrientation: () -> Int
    ) {
        jpegReader.setOnImageAvailableListener({ reader ->
            try {
                val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                
                try {
                    Log.d(TAG, "JPEG captured: ${image.width}x${image.height}")
                    
                    val plane = image.planes[0]
                    val buffer = plane.buffer
                    
                    val cropRatio = targetCropRatio()
                    
                    Log.d(TAG, "Capture params: cropRatio=${String.format("%.3f", cropRatio)}, deviceOrientation=${deviceOrientationDegrees()}°")
                    
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)
                    
                    var bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bitmap == null) {
                        Log.e(TAG, "Failed to decode JPEG")
                        return@setOnImageAvailableListener
                    }
                    
                    val rotationDegrees = BitmapUtils.calculateDimensionMatchingRotation(
                        bitmap,
                        deviceOrientationDegrees(),
                        sensorOrientation()
                    )
                    
                    if (rotationDegrees != 0) {
                        val rotatedBitmap = BitmapUtils.rotateBitmap(bitmap, rotationDegrees)
                        if (rotatedBitmap !== bitmap) {
                            bitmap.recycle()
                            bitmap = rotatedBitmap
                        }
                        Log.d(TAG, "Bitmap rotated: now ${bitmap.width}x${bitmap.height}")
                    }
                    
                    val imageRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
                    val needsCrop = abs(imageRatio - cropRatio) >= 0.05f && cropRatio != 0f
                    
                    val finalBitmap = if (needsCrop) {
                        Log.d(TAG, "Applying crop: ${String.format("%.3f", imageRatio)} -> ${String.format("%.3f", cropRatio)}")
                        BitmapUtils.cropBitmapToRatio(bitmap, cropRatio)
                    } else {
                        Log.d(TAG, "No crop needed")
                        bitmap
                    }
                    
                    val outputStream = ByteArrayOutputStream()
                    finalBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, outputStream)
                    val finalBytes = outputStream.toByteArray()
                    val finalBuffer = java.nio.ByteBuffer.wrap(finalBytes)
                    
                    val uri = captureController.saveImage(finalBuffer, 0)
                    if (uri != null) {
                        Log.d(TAG, "Photo saved: $uri (${finalBitmap.width}x${finalBitmap.height})")
                    } else {
                        Log.e(TAG, "Failed to save photo")
                    }
                    
                    if (bitmap !== finalBitmap) {
                        bitmap.recycle()
                    }
                    finalBitmap.recycle()
                    
                } finally {
                    image.close()
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "JPEG listener error", e)
            }
        }, backgroundHandler)
    }
}
