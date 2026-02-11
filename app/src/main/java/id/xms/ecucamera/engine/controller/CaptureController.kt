package id.xms.ecucamera.engine.controller

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*

class CaptureController(private val context: Context) {
    
    companion object {
        private const val TAG = "ECU_CAPTURE"
        private const val FOLDER_NAME = "EcuCamera"
    }
    
    /**
     * Save JPEG image buffer to MediaStore and return URI
     * @param buffer ByteBuffer containing JPEG data
     * @param rotation Image rotation in degrees
     * @return Uri of saved image or null if failed
     */
    fun saveImage(buffer: ByteBuffer, rotation: Int): Uri? {
        return try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val filename = "ECU_${timestamp}.jpg"
            
            Log.d(TAG, "Saving image: $filename, size: ${buffer.remaining()} bytes, rotation: ${rotation}°")
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Use MediaStore API for Android 10+
                saveImageToMediaStore(buffer, filename, rotation)
            } else {
                // Use legacy file system approach for older Android versions
                saveImageToLegacyStorage(buffer, filename, rotation)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save image", e)
            null
        }
    }
    
    /**
     * Save image using MediaStore API (Android 10+)
     */
    private fun saveImageToMediaStore(buffer: ByteBuffer, filename: String, rotation: Int): Uri? {
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_DCIM}/$FOLDER_NAME")
            put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
            put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
            if (rotation != 0) {
                put(MediaStore.Images.Media.ORIENTATION, rotation)
            }
        }
        
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        
        return uri?.let { imageUri ->
            try {
                resolver.openOutputStream(imageUri)?.use { outputStream ->
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)
                    outputStream.write(bytes)
                    outputStream.flush()
                    
                    Log.d(TAG, "Image saved to MediaStore: $imageUri")
                    imageUri
                }
            } catch (e: IOException) {
                Log.e(TAG, "Failed to write image to MediaStore", e)
                // Clean up the empty entry
                resolver.delete(imageUri, null, null)
                null
            }
        }
    }
    
    /**
     * Save image using legacy file system approach (Android 9 and below)
     */
    private fun saveImageToLegacyStorage(buffer: ByteBuffer, filename: String, rotation: Int): Uri? {
        val dcimDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
        val ecuCameraDir = File(dcimDir, FOLDER_NAME)
        
        // Create directory if it doesn't exist
        if (!ecuCameraDir.exists()) {
            if (!ecuCameraDir.mkdirs()) {
                Log.e(TAG, "Failed to create directory: ${ecuCameraDir.absolutePath}")
                return null
            }
        }
        
        val imageFile = File(ecuCameraDir, filename)
        
        return try {
            FileOutputStream(imageFile).use { outputStream ->
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                outputStream.write(bytes)
                outputStream.flush()
            }
            
            // Add to MediaStore so it appears in Gallery
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DATA, imageFile.absolutePath)
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
                put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
                if (rotation != 0) {
                    put(MediaStore.Images.Media.ORIENTATION, rotation)
                }
            }
            
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            Log.d(TAG, "Image saved to legacy storage: ${imageFile.absolutePath}, MediaStore URI: $uri")
            uri
        } catch (e: IOException) {
            Log.e(TAG, "Failed to save image to legacy storage", e)
            null
        }
    }
    
    /**
     * Get the directory where images are saved
     */
    fun getImageDirectory(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "${Environment.DIRECTORY_DCIM}/$FOLDER_NAME"
        } else {
            val dcimDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
            File(dcimDir, FOLDER_NAME).absolutePath
        }
    }
    
    /**
     * Check if external storage is available for writing
     */
    fun isExternalStorageWritable(): Boolean {
        return Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED
    }
}