package id.xms.ecucamera.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Extension property to create DataStore instance
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "camera_settings")

/**
 * SettingsManager handles persistence of user preferences using DataStore.
 * Stores flash mode, aspect ratio, histogram visibility, and grid mode.
 */
class SettingsManager(private val context: Context) {
    
    companion object {
        // Preference keys
        private val FLASH_MODE_KEY = intPreferencesKey("flash_mode")
        private val ASPECT_RATIO_INDEX_KEY = intPreferencesKey("aspect_ratio_index")
        private val SHOW_HISTOGRAM_KEY = booleanPreferencesKey("show_histogram")
        private val GRID_MODE_KEY = intPreferencesKey("grid_mode")
        
        // Default values
        private const val DEFAULT_FLASH_MODE = 0 // OFF
        private const val DEFAULT_ASPECT_RATIO_INDEX = 1 // 4:3
        private const val DEFAULT_SHOW_HISTOGRAM = false // OFF
        private const val DEFAULT_GRID_MODE = 0 // OFF
    }
    
    // Flow for flash mode (0 = OFF, 1 = ON)
    val flashModeFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[FLASH_MODE_KEY] ?: DEFAULT_FLASH_MODE
    }
    
    // Flow for aspect ratio index (0 = 16:9, 1 = 4:3, 2 = 1:1)
    val aspectRatioIndexFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[ASPECT_RATIO_INDEX_KEY] ?: DEFAULT_ASPECT_RATIO_INDEX
    }
    
    // Flow for histogram visibility
    val showHistogramFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[SHOW_HISTOGRAM_KEY] ?: DEFAULT_SHOW_HISTOGRAM
    }
    
    // Flow for grid mode (0 = OFF, 1 = Rule of Thirds, 2 = Golden Ratio)
    val gridModeFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[GRID_MODE_KEY] ?: DEFAULT_GRID_MODE
    }
    
    // Setter functions
    
    /**
     * Set flash mode (0 = OFF, 1 = ON)
     */
    suspend fun setFlashMode(mode: Int) {
        context.dataStore.edit { preferences ->
            preferences[FLASH_MODE_KEY] = mode
        }
    }
    
    /**
     * Set aspect ratio index (0 = 16:9, 1 = 4:3, 2 = 1:1)
     */
    suspend fun setAspectRatioIndex(index: Int) {
        context.dataStore.edit { preferences ->
            preferences[ASPECT_RATIO_INDEX_KEY] = index
        }
    }
    
    /**
     * Toggle histogram visibility
     */
    suspend fun setShowHistogram(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SHOW_HISTOGRAM_KEY] = enabled
        }
    }
    
    /**
     * Set grid mode (0 = OFF, 1 = Rule of Thirds, 2 = Golden Ratio)
     */
    suspend fun setGridMode(mode: Int) {
        context.dataStore.edit { preferences ->
            preferences[GRID_MODE_KEY] = mode
        }
    }
}
