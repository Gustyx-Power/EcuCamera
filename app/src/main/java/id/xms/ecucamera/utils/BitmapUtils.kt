package id.xms.ecucamera.utils

import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import kotlin.math.abs

object BitmapUtils {
    private const val TAG = "BitmapUtils"
    
    fun rotateBitmap(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return bitmap
        
        Log.d(TAG, "Rotating bitmap by $degrees°")
        
        val matrix = Matrix()
        matrix.postRotate(degrees.toFloat())
        
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
    
    fun calculateDimensionMatchingRotation(
        bitmap: Bitmap,
        deviceOrientationDegrees: Int,
        sensorOrientation: Int
    ): Int {
        val isDeviceLandscape = deviceOrientationDegrees == 90 || deviceOrientationDegrees == 270
        val isBitmapLandscape = bitmap.width > bitmap.height
        
        Log.d(TAG, "Dimension check: device=${deviceOrientationDegrees}° (landscape=$isDeviceLandscape), " +
                "bitmap=${bitmap.width}x${bitmap.height} (landscape=$isBitmapLandscape)")
        
        val rotationDegrees = when {
            isDeviceLandscape && !isBitmapLandscape -> {
                if (deviceOrientationDegrees == 90) 90 else 270
            }
            !isDeviceLandscape && isBitmapLandscape -> 90
            deviceOrientationDegrees == 180 -> 180
            else -> 0
        }
        
        Log.d(TAG, "Dimension-matching rotation: $rotationDegrees° (sensor=$sensorOrientation°, device=$deviceOrientationDegrees°)")
        return rotationDegrees
    }
    
    fun cropBitmapToRatio(bitmap: Bitmap, targetRatio: Float): Bitmap {
        val srcWidth = bitmap.width
        val srcHeight = bitmap.height
        val srcRatio = srcWidth.toFloat() / srcHeight.toFloat()
        
        val isPortrait = srcHeight > srcWidth
        
        val adjustedTargetRatio = if (isPortrait && targetRatio > 1f) {
            1f / targetRatio
        } else {
            targetRatio
        }
        
        Log.d(TAG, "cropBitmapToRatio: src=${srcWidth}x${srcHeight} (${String.format("%.3f", srcRatio)}), " +
                "target=${String.format("%.3f", targetRatio)}, " +
                "adjusted=${String.format("%.3f", adjustedTargetRatio)}, " +
                "isPortrait=$isPortrait")
        
        if (abs(srcRatio - adjustedTargetRatio) < 0.05f) {
            return bitmap
        }
        
        val cropWidth: Int
        val cropHeight: Int
        
        if (adjustedTargetRatio > srcRatio) {
            cropWidth = srcWidth
            cropHeight = (srcWidth / adjustedTargetRatio).toInt()
        } else {
            cropHeight = srcHeight
            cropWidth = (srcHeight * adjustedTargetRatio).toInt()
        }
        
        val x = (srcWidth - cropWidth) / 2
        val y = (srcHeight - cropHeight) / 2
        
        Log.d(TAG, "Cropping: (${x},${y}) size=${cropWidth}x${cropHeight}")
        
        return Bitmap.createBitmap(bitmap, x, y, cropWidth, cropHeight)
    }
}
