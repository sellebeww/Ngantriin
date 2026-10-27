package org.umn.ngantriin.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.umn.ngantriin.R

/**
 * Tombol hati di atas foto, jadi punya scrim sendiri supaya tetap
 * terbaca di gambar apa pun.
 */
@Composable
fun FavoriteButton(
    isFavorite: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    /** Di atas foto hati butuh scrim, di permukaan polos tidak. */
    overPhoto: Boolean = true
) {
    // Hati membesar saat berubah status, tapi tidak di composition pertama,
    // supaya daftar kartu yang sudah difavoritkan tidak memantul semua saat di-scroll.
    val scale = remember { Animatable(1f) }
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(isFavorite) {
        if (!settled) {
            settled = true
        } else {
            scale.snapTo(0.6f)
            scale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
        }
    }
    val tint by animateColorAsState(
        targetValue = if (isFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "favoriteTint"
    )

    IconButton(
        onClick = onToggle,
        modifier = if (overPhoto) {
            modifier
                .size(40.dp)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f), CircleShape)
        } else {
            modifier.size(36.dp)
        }
    ) {
        Icon(
            imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            contentDescription = stringResource(
                if (isFavorite) R.string.cd_favorite_remove else R.string.cd_favorite_add
            ),
            tint = tint,
            modifier = Modifier
                .size(22.dp)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                }
        )
    }
}
