package org.umn.ngantriin.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.umn.ngantriin.R

/** Original wordmark and tagline, loaded locally for first-frame branding. */
@Composable
fun NgantriinLogo(modifier: Modifier = Modifier) {
    // Preserve the supplied dark lettering on a light backing in dark mode.
    val backing = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
        Modifier.background(Color.White, RoundedCornerShape(20.dp)).padding(12.dp)
    } else {
        Modifier
    }
    Image(
        painter = painterResource(R.drawable.ngantriin_logo),
        contentDescription = stringResource(R.string.app_name) + ". " +
            stringResource(R.string.app_tagline),
        contentScale = ContentScale.Fit,
        modifier = modifier.then(backing)
    )
}
