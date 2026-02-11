package id.xms.ecucamera.engine.core

import android.graphics.Rect
import android.util.Log

sealed class LensType(val id: String, val baseZoom: Float) {
    object UltraWide : LensType("2", 0.6f)
    object Wide : LensType("0", 1.0f)
    object Macro : LensType("3", 1.0f)
}

class LensManager {
    
    companion object {
        private const val TAG = "ECU_LENS"
    }
    
    fun getLensForZoom(ratio: Float): String {
        return when {
            ratio < 1.0f -> {
                Log.d(TAG, "Zoom ratio $ratio -> Ultra-Wide lens (ID: 2)")
                LensType.UltraWide.id
            }
            else -> {
                Log.d(TAG, "Zoom ratio $ratio -> Wide lens (ID: 0)")
                LensType.Wide.id
            }
        }
    }
    
    fun getMacroLens(): String {
        Log.d(TAG, "Macro mode -> Macro lens (ID: 3)")
        return LensType.Macro.id
    }
    
    fun calculateCropRegion(sensorSize: Rect, zoomRatio: Float): Rect {
        val centerX = sensorSize.centerX()
        val centerY = sensorSize.centerY()
        
        val cropWidth = (sensorSize.width() / zoomRatio).toInt()
        val cropHeight = (sensorSize.height() / zoomRatio).toInt()
        
        val left = centerX - cropWidth / 2
        val top = centerY - cropHeight / 2
        val right = left + cropWidth
        val bottom = top + cropHeight
        
        val cropRegion = Rect(
            maxOf(left, sensorSize.left),
            maxOf(top, sensorSize.top),
            minOf(right, sensorSize.right),
            minOf(bottom, sensorSize.bottom)
        )
        
        Log.d(TAG, "Crop region calculated for zoom $zoomRatio: $cropRegion")
        return cropRegion
    }
    
    fun getLensType(cameraId: String): LensType? {
        return when (cameraId) {
            "0" -> LensType.Wide
            "2" -> LensType.UltraWide
            "3" -> LensType.Macro
            else -> null
        }
    }
}