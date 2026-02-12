package id.xms.ecucamera.ui.components.gallery

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import id.xms.ecucamera.R

/**
 * GalleryButton displays either a thumbnail of the latest photo or a default gallery icon.
 * When clicked, it opens the system gallery.
 * 
 * @param thumbnail The latest photo thumbnail bitmap, or null to show default icon
 * @param onClick Callback invoked when the button is clicked
 * @param modifier Optional modifier for customization
 */
@Composable
fun GalleryButton(
    thumbnail: Bitmap?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (thumbnail != null) {
            Image(
                bitmap = thumbnail.asImageBitmap(),
                contentDescription = stringResource(R.string.cd_open_gallery),
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .border(2.dp, Color.White, CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(
                imageVector = Icons.Filled.Photo,
                contentDescription = stringResource(R.string.cd_open_gallery),
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}
