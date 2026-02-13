package id.xms.ecucamera.utils

import android.app.Activity
import android.util.Log
import android.view.WindowManager

/**
 * WindowUtils - Helper functions for controlling screen brightness
 * Used for implementing "Screen Flash" on front cameras without physical flash
 */
object WindowUtils {
    
    private const val TAG = "WindowUtils"
    private var originalBrightness: Float = -1.0f
    
    /**
     * Set screen brightness programmatically
     * @param activity The activity whose window brightness to control
     * @param value Brightness value (0.0f to 1.0f, where 1.0f is maximum brightness)
     *              Use -1.0f to restore system default/auto brightness
     */
    fun setScreenBrightness(activity: Activity, value: Float) {
        try {
            val layoutParams = activity.window.attributes
            
            // Store original brightness before first change
            if (originalBrightness == -1.0f && value != -1.0f) {
                originalBrightness = layoutParams.screenBrightness
                Log.d(TAG, "Stored original brightness: $originalBrightness")
            }
            
            layoutParams.screenBrightness = value
            activity.window.attributes = layoutParams
            
            Log.d(TAG, "Screen brightness set to: $value")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set screen brightness", e)
        }
    }
    
    /**
     * Maximize screen brightness to maximum (1.0f)
     * @param activity The activity whose window brightness to control
     */
    fun maximizeBrightness(activity: Activity) {
        setScreenBrightness(activity, 1.0f)
    }
    
    /**
     * Restore screen brightness to the original value before any changes
     * @param activity The activity whose window brightness to control
     */
    fun restoreBrightness(activity: Activity) {
        val brightnessToRestore = if (originalBrightness != -1.0f) {
            originalBrightness
        } else {
            -1.0f // System default
        }
        
        setScreenBrightness(activity, brightnessToRestore)
        Log.d(TAG, "Restored brightness to: $brightnessToRestore")
        
        // Reset stored value
        originalBrightness = -1.0f
    }
}
