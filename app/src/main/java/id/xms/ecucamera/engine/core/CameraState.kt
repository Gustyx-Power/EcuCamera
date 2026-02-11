package id.xms.ecucamera.engine.core

/**
 * Represents the current state of the camera system
 */
sealed class CameraState {
    object Closed : CameraState()
    object Opening : CameraState()
    object Open : CameraState()
    object Configured : CameraState()
    data class Error(val message: String, val exception: Throwable? = null) : CameraState()
    
    override fun toString(): String = when (this) {
        is Closed -> "CLOSED"
        is Opening -> "OPENING"
        is Open -> "OPEN"
        is Configured -> "CONFIGURED"
        is Error -> "ERROR: $message"
    }
}