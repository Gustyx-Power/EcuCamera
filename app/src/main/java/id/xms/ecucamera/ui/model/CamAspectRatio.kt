package id.xms.ecucamera.ui.model

/**
 * CamAspectRatio defines the available aspect ratios for camera preview and capture.
 * 
 * - RATIO_1_1: Square format (1:1) - popular for social media
 * - RATIO_4_3: Standard camera sensor format (4:3) - native to most sensors
 * - RATIO_16_9: Widescreen format (16:9) - cinematic look
 * - RATIO_FULL: Full screen format - matches device screen aspect ratio
 */
enum class CamAspectRatio(val displayName: String) {
    RATIO_1_1("1:1"),
    RATIO_4_3("4:3"),
    RATIO_16_9("16:9"),
    RATIO_FULL("Full");
    
    /**
     * Converts the aspect ratio to a float value (width / height).
     * Note: RATIO_FULL returns 0f as a sentinel value - actual ratio is determined at runtime.
     */
    fun toFloat(): Float {
        return when (this) {
            RATIO_1_1 -> 1f / 1f
            RATIO_4_3 -> 4f / 3f
            RATIO_16_9 -> 16f / 9f
            RATIO_FULL -> 0f // Sentinel value - will be calculated from screen dimensions
        }
    }
    
    /**
     * Cycles to the next aspect ratio in the sequence.
     */
    fun next(): CamAspectRatio {
        val values = values()
        val nextIndex = (ordinal + 1) % values.size
        return values[nextIndex]
    }
    
    /**
     * Checks if this is a fixed aspect ratio (not RATIO_FULL).
     */
    fun isFixed(): Boolean = this != RATIO_FULL
}
