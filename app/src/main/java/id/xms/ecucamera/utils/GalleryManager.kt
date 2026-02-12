package id.xms.ecucamera.utils

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import java.io.IOException

object GalleryManager {
    private const val TAG = "GalleryManager"
    
    /**
     * Retrieves the thumbnail of the most recently taken photo from MediaStore.
     * @return Bitmap thumbnail or null if no images found
     */
    fun getLastImageThumbnail(context: Context): Bitmap? {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_TAKEN
        )
        
        val sortOrder = "${MediaStore.Images.Media.DATE_TAKEN} DESC"
        
        try {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                    val id = cursor.getLong(idColumn)
                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id
                    )
                    
                    return loadThumbnail(context, contentUri)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading thumbnail", e)
        }
        
        return null
    }
    
    /**
     * Loads a thumbnail bitmap from the given URI.
     * @param context Application context
     * @param uri Content URI of the image
     * @return Bitmap thumbnail or null on error
     */
    private fun loadThumbnail(context: Context, uri: Uri): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val options = BitmapFactory.Options().apply {
                    inSampleSize = 4 // Downsample for thumbnail
                    inJustDecodeBounds = false
                }
                BitmapFactory.decodeStream(inputStream, null, options)
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error decoding thumbnail", e)
            null
        }
    }
    
    /**
     * Opens the system gallery app to view images.
     * @param context Application context
     */
    fun openGallery(context: Context) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                type = "image/*"
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening gallery", e)
        }
    }
    
    /**
     * Retrieves a list of recent image URIs from MediaStore.
     * Fetches the last 20 images sorted by date taken (most recent first).
     * 
     * @param context Application context
     * @param limit Maximum number of images to retrieve (default: 20)
     * @return List of image content URIs
     */
    fun getRecentImages(context: Context, limit: Int = 20): List<Uri> {
        val imageUris = mutableListOf<Uri>()
        
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATA
        )
        
        val sortOrder = "${MediaStore.Images.Media.DATE_TAKEN} DESC"
        
        try {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                var count = 0
                
                while (cursor.moveToNext() && count < limit) {
                    val id = cursor.getLong(idColumn)
                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id
                    )
                    imageUris.add(contentUri)
                    count++
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading recent images", e)
        }
        
        return imageUris
    }
}
