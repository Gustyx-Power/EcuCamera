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
    private const val APP_FOLDER_NAME = "EcuCamera"
    
    /**
     * Retrieves the URI of the most recently taken photo from the EcuCamera folder.
     * @return Content URI or null if no images found in EcuCamera folder
     */
    fun getLastImageUri(context: Context): Uri? {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME
        )
        
        // Filter by bucket name (folder name)
        val selection = "${MediaStore.Images.Media.BUCKET_DISPLAY_NAME} = ?"
        val selectionArgs = arrayOf(APP_FOLDER_NAME)
        
        val sortOrder = "${MediaStore.Images.Media.DATE_TAKEN} DESC"
        
        try {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                    val id = cursor.getLong(idColumn)
                    val uri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id
                    )
                    Log.d(TAG, "Found latest image in $APP_FOLDER_NAME folder: $uri")
                    return uri
                } else {
                    Log.d(TAG, "No images found in $APP_FOLDER_NAME folder")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading last image URI from $APP_FOLDER_NAME folder", e)
        }
        
        return null
    }
    
    /**
     * Retrieves the thumbnail of the most recently taken photo from the EcuCamera folder.
     * @return Bitmap thumbnail or null if no images found in EcuCamera folder
     */
    fun getLastImageThumbnail(context: Context): Bitmap? {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME
        )
        
        // Filter by bucket name (folder name)
        val selection = "${MediaStore.Images.Media.BUCKET_DISPLAY_NAME} = ?"
        val selectionArgs = arrayOf(APP_FOLDER_NAME)
        
        val sortOrder = "${MediaStore.Images.Media.DATE_TAKEN} DESC"
        
        try {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                    val id = cursor.getLong(idColumn)
                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id
                    )
                    
                    Log.d(TAG, "Loading thumbnail from $APP_FOLDER_NAME folder: $contentUri")
                    return loadThumbnail(context, contentUri)
                } else {
                    Log.d(TAG, "No images found in $APP_FOLDER_NAME folder for thumbnail")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading thumbnail from $APP_FOLDER_NAME folder", e)
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
     * Opens the system gallery app to view a specific image.
     * @param context Application context
     * @param uri Content URI of the image to view
     */
    fun openGallery(context: Context, uri: Uri? = null) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                if (uri != null) {
                    setDataAndType(uri, "image/*")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                } else {
                    type = "image/*"
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening gallery", e)
        }
    }
    
    /**
     * Retrieves a list of recent image URIs from the EcuCamera folder.
     * Fetches the last 20 images sorted by date taken (most recent first).
     * 
     * @param context Application context
     * @param limit Maximum number of images to retrieve (default: 20)
     * @return List of image content URIs from EcuCamera folder
     */
    fun getRecentImages(context: Context, limit: Int = 20): List<Uri> {
        val imageUris = mutableListOf<Uri>()
        
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME
        )
        
        // Filter by bucket name (folder name)
        val selection = "${MediaStore.Images.Media.BUCKET_DISPLAY_NAME} = ?"
        val selectionArgs = arrayOf(APP_FOLDER_NAME)
        
        val sortOrder = "${MediaStore.Images.Media.DATE_TAKEN} DESC"
        
        try {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
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
                
                Log.d(TAG, "Found ${imageUris.size} images in $APP_FOLDER_NAME folder")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading recent images from $APP_FOLDER_NAME folder", e)
        }
        
        return imageUris
    }
}
