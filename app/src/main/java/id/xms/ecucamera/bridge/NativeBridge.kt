package id.xms.ecucamera.bridge

import android.util.Log

/**
 * Native bridge for ECU Camera engine communication.
 * This class provides the interface between Kotlin/Java and the native Rust engine.
 */
object NativeBridge {
    private const val TAG = "NativeBridge"
    
    init {
        try {
            // Load the native C++ bridge library
            System.loadLibrary("ecu-bridge")
            Log.i(TAG, "Native bridge library loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load native bridge library", e)
            throw RuntimeException("Failed to load native bridge library", e)
        }
    }
    
    /**
     * Test function to verify Rust connection
     * @return String from Rust engine confirming connection
     */
    external fun stringFromRust(): String
    
    /**
     * Get the current status of the ECU engine
     * @return Status string from the Rust engine
     */
    external fun getEngineStatus(): String
    
    /**
     * Initialize the ECU engine
     * @return Initialization result message
     */
    external fun initializeEngine(): String
    
    /**
     * Get C++ bridge information
     * @return C++ bridge status and info
     */
    external fun getCppBridgeInfo(): String
    
    /**
     * Check if the native bridge is properly loaded and functional
     * @return true if bridge is working, false otherwise
     */
    fun isNativeBridgeReady(): Boolean {
        return try {
            val testResult = stringFromRust()
            Log.d(TAG, "Bridge test result: $testResult")
            testResult.contains("Rust V8 Connected")
        } catch (e: Exception) {
            Log.e(TAG, "Native bridge test failed", e)
            false
        }
    }
}