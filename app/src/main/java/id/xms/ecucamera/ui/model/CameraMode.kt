package id.xms.ecucamera.ui.model

import androidx.annotation.StringRes
import id.xms.ecucamera.R

enum class CameraMode(@StringRes val labelRes: Int) {
    NIGHT(R.string.mode_night),
    PORTRAIT(R.string.mode_portrait),
    PHOTO(R.string.mode_photo),
    VIDEO(R.string.mode_video),
    PRO(R.string.mode_pro)
}